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
 * 为图中每个节点的<b>每一次执行（含每次重试）</b>创建一个独立的 Langfuse 子 span，记录该次执行的输入、输出、token
 * 用量与成功/失败状态。
 *
 * <p>
 * 子 span 挂在 {@code GraphServiceImpl} 创建的根 span（{@code graph-stream}/{@code graph-feedback}）下。
 * 未启用 Langfuse 或找不到根 span 时全程静默跳过，保持零侵入。
 *
 * <h2>失败判定为何不能依赖 {@code onError}</h2>
 *
 * DataAgent 的节点普遍返回 embedded {@code Flux}，而 {@code FluxUtil} 用
 * {@code onErrorResume} 把流内异常转成了一个<i>正常的数据元素</i>（{@code GraphResponse.error(...)}）。
 * 框架因此走的是 {@code NODE_AFTER} 而不是 {@code ERROR} 分支——LLM 调用失败时
 * {@link #onError} <b>不会被触发</b>。若把 {@code onError} 当作唯一失败路径，失败节点会在 Langfuse
 * 上显示为绿色 OK，正好打掉本功能的核心价值。所以 {@link #after} 必须自行判定成败。
 *
 * <h2>判据：节点窗口内是否产出了新的输出</h2>
 *
 * {@code OverAllState} 是<b>单调累加</b>的，仅检查"声明的 output key 是否存在"并不够：节点重试时，上一次
 * attempt 写入的 key 仍留在 state 里，于是失败的第二次尝试会被误判为成功——这正是需要避免的那种静默失效。
 *
 * <p>
 * 因此本类在 {@link #before} 为声明的 output key 拍一份快照，在 {@link #after} 与当前 state 比对：
 *
 * <pre>
 * 该节点声明的 output key 中，没有任何一个在本次执行窗口内新增或发生变化 → ERROR
 * 至少有一个新增或变化                                                  → OK
 * </pre>
 *
 * 这样既能抓出"LLM 挂掉、{@code resultSupplier} 从未运行、state 完全没动"（真失败），也不会把
 * "业务校验失败但正常写了 {@code PLAN_VALIDATION_*}"或"降级模式换了一组 key"误判为失败——后两者都是节点正常工作
 * 并如实报告结果，本身有重试机制，标红只会制造噪音、掩盖真故障。
 *
 * <h2>已知的判定边界</h2>
 *
 * {@code TableRelationNode} 是唯一"Flux 与普通值混合返回"的节点，它的
 * {@code DB_DIALECT_TYPE}/{@code TABLE_RELATION_RETRY_COUNT} 等在 Flux 完成前就已落进 state，
 * 即使随后 LLM 失败也会造成"有变化"。该节点的 LLM 失败因此可能被判为 OK。这是有意接受的取舍：listener
 * 是纯旁路（框架把回调包在 try/catch 里、state 不可写、路由与 listener 无关），误判的唯一后果是面板颜色标错，
 * 不影响 workflow 执行。
 *
 * @author suke
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

			activeNodes.computeIfAbsent(threadId, k -> new ArrayDeque<>())
				.push(new ActiveNode(nodeId, span, snapshot));
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
	 * 客户端主动断开时的兜底清理：结束该 threadId 下所有仍挂着的节点 span，并清空计数器与累加器。
	 *
	 * <p>
	 * 这是唯一会绕过 {@code after}/{@code onError} 的场景；不清理会同时造成内存泄漏和 Langfuse 上永不结束的 span。
	 */
	public void discardThread(String threadId) {
		if (threadId == null) {
			return;
		}
		Deque<ActiveNode> stack = activeNodes.remove(threadId);
		if (stack != null) {
			for (ActiveNode active : stack) {
				try {
					active.span().setStatus(StatusCode.ERROR, "Client disconnected before node completed");
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
