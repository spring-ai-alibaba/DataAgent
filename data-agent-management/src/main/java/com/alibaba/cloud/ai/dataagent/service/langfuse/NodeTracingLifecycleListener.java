/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.langfuse;

import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author suke
 * @version 1.0
 * @date 2026-08-08 13:37
 * @description 为图中每个节点的每一次执行（含重试）创建独立 Langfuse 子 span，记录输入/输出/token/成败状态
 */
@Slf4j
@Component
public class NodeTracingLifecycleListener implements GraphLifecycleListener {

	private static final AttributeKey<String> INPUT_VALUE = AttributeKey.stringKey("input.value");

	private static final AttributeKey<String> OUTPUT_VALUE = AttributeKey.stringKey("output.value");

	private static final AttributeKey<String> ATTR_NODE_ID = AttributeKey.stringKey("data_agent.node_id");

	private static final AttributeKey<Long> ATTR_NODE_ATTEMPT = AttributeKey.longKey("data_agent.node_attempt");

	private static final AttributeKey<Long> GEN_AI_PROMPT_TOKENS = AttributeKey.longKey("gen_ai.usage.prompt_tokens");

	private static final AttributeKey<Long> GEN_AI_COMPLETION_TOKENS = AttributeKey
		.longKey("gen_ai.usage.completion_tokens");

	private static final AttributeKey<Long> GEN_AI_TOTAL_TOKENS = AttributeKey.longKey("gen_ai.usage.total_tokens");

	/**
	 * Langfuse v4 识别此属性把 span 归类为 observation 类型（{@code ObservationTypeMapper}）。 取值
	 * {@code generation} 时该 span 被视为 LLM 生成，token 用量会作为 Usage 徽章上浮到 span 头部（否则只藏在
	 * metadata 里）。仅对确有 token 的节点标记，无 LLM 调用的节点保持普通 span。
	 */
	private static final AttributeKey<String> LANGFUSE_OBSERVATION_TYPE = AttributeKey
		.stringKey("langfuse.observation.type");

	private static final String OBSERVATION_TYPE_GENERATION = "generation";

	/** 单个 attribute 值的上限，避免超长 SQL/schema 把 span 撑爆 */
	private static final int MAX_ATTRIBUTE_CHARS = 8_000;

	private final Tracer tracer;

	private final LangfuseService langfuseService;

	/** 每个 threadId 下未结束的节点 span 栈；用 Deque 为未来的并行/子图场景兜底 */
	private final Map<String, Deque<ActiveNode>> activeNodes = new ConcurrentHashMap<>();

	/** 重试计数器，key 为 {@code threadId#nodeId} */
	private final Map<String, AtomicInteger> attemptCounters = new ConcurrentHashMap<>();

	public NodeTracingLifecycleListener(Tracer langfuseTracer, LangfuseService langfuseService) {
		this.tracer = langfuseTracer;
		this.langfuseService = langfuseService;
	}

	/** 一次进行中的节点执行：它的 span 加上进入时的 output key 快照 */
	private record ActiveNode(String nodeId, Span span, Map<String, Object> outputSnapshot) {
	}

	@Override
	public void before(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
		String threadId = threadId(config);
		if (threadId == null) {
			return;
		}
		Span rootSpan = langfuseService.getRootSpan(threadId);
		if (rootSpan == null) {
			// Langfuse 未启用，或本轮运行的根 span 已结束
			return;
		}

		try {
			int attempt = attemptCounters.computeIfAbsent(counterKey(threadId, nodeId), k -> new AtomicInteger())
				.incrementAndGet();

			Span span = tracer.spanBuilder(nodeId)
				.setSpanKind(SpanKind.INTERNAL)
				.setParent(io.opentelemetry.context.Context.current().with(rootSpan))
				.startSpan();

			span.setAttribute(ATTR_NODE_ID, nodeId);
			span.setAttribute(ATTR_NODE_ATTEMPT, attempt);
			span.setAttribute(INPUT_VALUE, render(NodeIoRegistry.extractInputs(nodeId, state)));

			// 为失败判定拍快照：记录本次执行开始时各 output key 的值
			Map<String, Object> snapshot = NodeIoRegistry.extractOutputs(nodeId, state);

			// 该 attempt 专属的 token 累加器；FluxUtil 按 threadId 找到它并累加
			LangfuseService.registerActiveAccumulator(threadId);

			activeNodes.computeIfAbsent(threadId, k -> new ArrayDeque<>()).push(new ActiveNode(nodeId, span, snapshot));
		}
		catch (Exception e) {
			log.warn("Failed to start Langfuse span for node {}", nodeId, e);
		}
	}

	@Override
	public void after(String nodeId, Map<String, Object> state, RunnableConfig config, Long curTime) {
		String threadId = threadId(config);
		ActiveNode active = popActiveNode(threadId, nodeId);
		if (active == null) {
			return;
		}

		try {
			Map<String, Object> outputs = NodeIoRegistry.extractOutputs(nodeId, state);
			active.span().setAttribute(OUTPUT_VALUE, render(outputs));
			applyNodeTokens(active.span(), threadId);

			if (producedNewOutput(active.outputSnapshot(), outputs)) {
				active.span().setStatus(StatusCode.OK);
			}
			else {
				// 节点执行完毕却没有产出任何新输出：resultSupplier 未运行，通常是 LLM 调用失败
				active.span()
					.setStatus(StatusCode.ERROR,
							"Node produced no new output; declared output keys unchanged during execution");
			}
		}
		catch (Exception e) {
			log.warn("Failed to finish Langfuse span for node {}", nodeId, e);
		}
		finally {
			active.span().end();
		}
	}

	@Override
	public void onError(String nodeId, Map<String, Object> state, Throwable ex, RunnableConfig config) {
		String threadId = threadId(config);
		ActiveNode active = popActiveNode(threadId, nodeId);
		if (active == null) {
			return;
		}

		try {
			active.span().setAttribute(OUTPUT_VALUE, render(NodeIoRegistry.extractOutputs(nodeId, state)));
			applyNodeTokens(active.span(), threadId);
			if (ex != null) {
				active.span().recordException(ex);
				active.span().setStatus(StatusCode.ERROR, ex.getClass().getSimpleName() + ": " + ex.getMessage());
			}
			else {
				active.span().setStatus(StatusCode.ERROR, "Node execution failed");
			}
		}
		catch (Exception e) {
			log.warn("Failed to record Langfuse span error for node {}", nodeId, e);
		}
		finally {
			active.span().end();
		}
	}

	/**
	 * 客户端主动断开时的兜底清理：结束该 threadId 下所有仍挂着的节点 span（标记为断开），并清空计数器与累加器。
	 *
	 * <p>
	 * 这是唯一会绕过 {@code after}/{@code onError} 的场景；不清理会同时造成内存泄漏和 Langfuse 上永不结束的 span。
	 */
	public void discardThread(String threadId) {
		endThread(threadId, "Client disconnected before node completed");
	}

	/**
	 * 一轮图运行正常结束（成功或失败）后的清理：清空该 threadId 的 attempt 计数器与累加器残留。
	 *
	 * <p>
	 * {@code after}/{@code onError} 只结束单个节点 span，但 {@link #attemptCounters} 是按
	 * {@code threadId#nodeId} 累积的，正常结束路径不会移除它们；不在此处清理会导致每个新 threadId
	 * 都留下计数器条目、随流量无界增长。若确有节点 span 未闭合（理论上不该发生），一并结束以防泄漏。
	 */
	public void finishThread(String threadId) {
		endThread(threadId, "Run ended before node completed");
	}

	private void endThread(String threadId, String danglingReason) {
		if (threadId == null) {
			return;
		}
		Deque<ActiveNode> stack = activeNodes.remove(threadId);
		if (stack != null) {
			for (ActiveNode active : stack) {
				try {
					active.span().setStatus(StatusCode.ERROR, danglingReason);
				}
				catch (Exception e) {
					log.warn("Failed to mark interrupted span for node {}", active.nodeId(), e);
				}
				finally {
					active.span().end();
				}
			}
		}
		attemptCounters.keySet().removeIf(key -> key.startsWith(threadId + "#"));
		LangfuseService.discardAccumulators(threadId);
	}

	/**
	 * 判断本次执行窗口内是否产出了新输出。
	 *
	 * <p>
	 * "新增一个 key" 或 "已有 key 的值发生变化" 都算产出。两者皆无则说明 state 完全没被这个节点改动过。
	 */
	private boolean producedNewOutput(Map<String, Object> before, Map<String, Object> after) {
		for (Map.Entry<String, Object> entry : after.entrySet()) {
			if (!before.containsKey(entry.getKey())) {
				return true;
			}
			if (!Objects.equals(before.get(entry.getKey()), entry.getValue())) {
				return true;
			}
		}
		return false;
	}

	private void applyNodeTokens(Span span, String threadId) {
		long[] tokens = LangfuseService.takeActiveAccumulator(threadId);
		// 无 LLM 调用的节点（如 PlanExecutorNode）没有 token，属预期情况
		if (tokens != null && (tokens[0] > 0 || tokens[1] > 0)) {
			// 标记为 generation，使 token 在 Langfuse 面板上作为 Usage 徽章显示
			span.setAttribute(LANGFUSE_OBSERVATION_TYPE, OBSERVATION_TYPE_GENERATION);
			span.setAttribute(GEN_AI_PROMPT_TOKENS, tokens[0]);
			span.setAttribute(GEN_AI_COMPLETION_TOKENS, tokens[1]);
			span.setAttribute(GEN_AI_TOTAL_TOKENS, tokens[0] + tokens[1]);
		}
	}

	/**
	 * 弹出该 threadId 栈顶的节点。若栈顶 nodeId 与预期不符（理论上不该发生），仍按栈顺序弹出并记日志， 避免 span 永远挂着。
	 */
	private ActiveNode popActiveNode(String threadId, String nodeId) {
		if (threadId == null) {
			return null;
		}
		Deque<ActiveNode> stack = activeNodes.get(threadId);
		if (stack == null || stack.isEmpty()) {
			// before 未触发（如 interruptBefore 中断在节点执行前直接返回），属正常情况
			return null;
		}
		ActiveNode active = stack.pop();
		if (stack.isEmpty()) {
			activeNodes.remove(threadId);
		}
		if (!Objects.equals(active.nodeId(), nodeId)) {
			log.warn("Langfuse span stack mismatch: popped {} while finishing {}", active.nodeId(), nodeId);
		}
		return active;
	}

	private String threadId(RunnableConfig config) {
		return config == null ? null : config.threadId().orElse(null);
	}

	private String counterKey(String threadId, String nodeId) {
		return threadId + "#" + nodeId;
	}

	/**
	 * 把 state 子集渲染成便于在 Langfuse 上阅读的字符串，并对超长值截断。
	 */
	private String render(Map<String, Object> values) {
		if (values.isEmpty()) {
			return "{}";
		}
		Map<String, String> rendered = new LinkedHashMap<>();
		values.forEach((key, value) -> rendered.put(key, truncate(String.valueOf(value))));
		return rendered.toString();
	}

	private String truncate(String text) {
		if (text.length() <= MAX_ATTRIBUTE_CHARS) {
			return text;
		}
		return text.substring(0, MAX_ATTRIBUTE_CHARS) + "...(truncated, " + text.length() + " chars total)";
	}

}
