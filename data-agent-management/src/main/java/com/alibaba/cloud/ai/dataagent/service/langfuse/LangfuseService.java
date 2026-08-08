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

import com.alibaba.cloud.ai.dataagent.config.OpenTelemetryConfig;
import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zihenzzz
 * @date 2026/2/16 13:54 基于 OpenTelemetry 的 Langfuse Reporter，用于追踪 LLM 调用
 */
@Slf4j
@Component
public class LangfuseService {

	private final Tracer tracer;

	private final boolean enabled;

	// --- Span Attribute Keys ---
	private static final AttributeKey<String> INPUT_VALUE = AttributeKey.stringKey("input.value");

	private static final AttributeKey<String> OUTPUT_VALUE = AttributeKey.stringKey("output.value");

	private static final AttributeKey<String> ATTR_AGENT_ID = AttributeKey.stringKey("data_agent.agent_id");

	private static final AttributeKey<String> ATTR_THREAD_ID = AttributeKey.stringKey("data_agent.thread_id");

	private static final AttributeKey<Boolean> ATTR_NL2SQL_ONLY = AttributeKey.booleanKey("data_agent.nl2sql_only");

	private static final AttributeKey<Boolean> ATTR_HUMAN_FEEDBACK = AttributeKey
		.booleanKey("data_agent.human_feedback");

	private static final AttributeKey<Long> GEN_AI_PROMPT_TOKENS = AttributeKey.longKey("gen_ai.usage.prompt_tokens");

	private static final AttributeKey<Long> GEN_AI_COMPLETION_TOKENS = AttributeKey
		.longKey("gen_ai.usage.completion_tokens");

	private static final AttributeKey<Long> GEN_AI_TOTAL_TOKENS = AttributeKey.longKey("gen_ai.usage.total_tokens");

	private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

	private static final AttributeKey<String> ERROR_MESSAGE = AttributeKey.stringKey("error.message");

	// --- Token 累计器，按 threadId 隔离 ---
	private static final ConcurrentHashMap<String, long[]> TOKEN_ACCUMULATOR = new ConcurrentHashMap<>();

	/**
	 * 当前活跃的<b>节点级</b> token 累加器，key 为 threadId。
	 *
	 * <p>
	 * 与 {@link #TOKEN_ACCUMULATOR}（整轮图运行的汇总）并存：本表由
	 * {@code NodeTracingLifecycleListener} 在每个节点的每一次尝试开始时 register、结束时 take，因此每个 attempt
	 * 各自拿到一份独立计数。重试时 prompt 会系统性膨胀，若合并成一个数字就无法区分「5000×3」和「2000+5000+8000」，
	 * 而后者恰恰是"重试 prompt 累积失控"的唯一信号。
	 *
	 * <p>
	 * 之所以按 threadId <b>单键</b>而非 {@code threadId#nodeId#attempt}：{@code FluxUtil} 侧只知道节点
	 * <i>类名</i>（{@code IntentRecognitionNode}），而 listener 侧的 nodeId 是图注册常量
	 * （{@code INTENT_RECOGNITION_NODE}），两者永不相等，任何拼 key 方案都会让 token 静默丢失。单键 +
	 * register/take 严格配对可绕开这个问题（依赖图为串行执行，无并行节点）。
	 */
	private static final ConcurrentHashMap<String, long[]> ACTIVE_NODE_ACCUMULATOR = new ConcurrentHashMap<>();

	/** 每个 threadId 当前的根 span，供节点级子 span 作为父 span 挂载 */
	private static final ConcurrentHashMap<String, Span> ROOT_SPANS = new ConcurrentHashMap<>();

	public LangfuseService(Tracer langfuseTracer, OpenTelemetryConfig openTelemetryConfig) {
		this.tracer = langfuseTracer;
		this.enabled = openTelemetryConfig.isEnabled();
	}

	/**
	 * 开始一个 Graph 流式处理的 Span，记录完整的请求上下文
	 */
	public Span startLLMSpan(String spanName, GraphRequest request) {
		if (!enabled) {
			return Span.getInvalid();
		}

		try {
			Span span = tracer.spanBuilder(spanName)
				.setSpanKind(SpanKind.CLIENT)
				.setParent(Context.current())
				.startSpan();

			String inputValue = String.format(
					"{\"query\":\"%s\",\"agentId\":\"%s\",\"threadId\":\"%s\",\"nl2sqlOnly\":%s,\"humanFeedback\":%s}",
					request.getQuery() != null ? request.getQuery() : "",
					request.getAgentId() != null ? request.getAgentId() : "",
					request.getThreadId() != null ? request.getThreadId() : "", request.isNl2sqlOnly(),
					request.isHumanFeedback());
			span.setAttribute(INPUT_VALUE, inputValue);
			span.setAttribute(ATTR_AGENT_ID, request.getAgentId() != null ? request.getAgentId() : "");
			span.setAttribute(ATTR_THREAD_ID, request.getThreadId() != null ? request.getThreadId() : "");
			span.setAttribute(ATTR_NL2SQL_ONLY, request.isNl2sqlOnly());
			span.setAttribute(ATTR_HUMAN_FEEDBACK, request.isHumanFeedback());

			// 初始化该 threadId 的 token 累计器
			if (request.getThreadId() != null) {
				TOKEN_ACCUMULATOR.put(request.getThreadId(), new long[] { 0, 0 });
				ROOT_SPANS.put(request.getThreadId(), span);
			}

			return span;
		}
		catch (Exception e) {
			log.error("Failed to start OTel span", e);
			return Span.getInvalid();
		}
	}

	/**
	 * 取出该 threadId 当前的根 span，供节点级子 span 作为父 span 使用。
	 * @return 根 span；若 Langfuse 未启用、该轮运行尚未开始或已结束则返回 {@code null}
	 */
	public Span getRootSpan(String threadId) {
		return threadId == null ? null : ROOT_SPANS.get(threadId);
	}

	/**
	 * 为某个节点的某一次尝试注册一个全新的 token 累加器。
	 *
	 * <p>
	 * 由 {@code NodeTracingLifecycleListener#before} 调用。若上一次 attempt 的累加器因异常路径未被
	 * {@link #takeActiveAccumulator} 取走，此处会直接覆盖，保证新 attempt 从零开始计数。
	 */
	public static void registerActiveAccumulator(String threadId) {
		if (threadId == null) {
			return;
		}
		ACTIVE_NODE_ACCUMULATOR.put(threadId, new long[] { 0, 0 });
	}

	/**
	 * 取出并移除当前活跃的节点级累加器。
	 *
	 * <p>
	 * 由 {@code NodeTracingLifecycleListener#after}/{@code #onError} 调用。
	 * @return {@code {promptTokens, completionTokens}}；无活跃累加器时返回 {@code null}
	 */
	public static long[] takeActiveAccumulator(String threadId) {
		if (threadId == null) {
			return null;
		}
		long[] tokens = ACTIVE_NODE_ACCUMULATOR.remove(threadId);
		if (tokens == null) {
			return null;
		}
		synchronized (tokens) {
			return new long[] { tokens[0], tokens[1] };
		}
	}

	/**
	 * 丢弃该 threadId 的全部 token 累加状态与根 span 引用。
	 *
	 * <p>
	 * 供客户端主动断开（{@code stopStreamProcessing}）时兜底清理，避免内存泄漏。
	 */
	public static void discardAccumulators(String threadId) {
		if (threadId == null) {
			return;
		}
		ACTIVE_NODE_ACCUMULATOR.remove(threadId);
		TOKEN_ACCUMULATOR.remove(threadId);
		ROOT_SPANS.remove(threadId);
	}

	/**
	 * 累计 token 用量（由 FluxUtil 在处理 ChatResponse 时调用）
	 *
	 * <p>
	 * 同时写入两处：整轮运行的汇总累加器（根 span 用）与当前活跃的节点级累加器（子 span 用）。 后者可能不存在（Langfuse
	 * 未启用，或该 token 产生于任何节点的 before/after 窗口之外），此时静默跳过。
	 */
	public static void accumulateTokens(Object threadId, long promptTokens, long completionTokens) {
		if (threadId == null) {
			return;
		}
		addTo(TOKEN_ACCUMULATOR.get(threadId), promptTokens, completionTokens);
		addTo(ACTIVE_NODE_ACCUMULATOR.get(threadId), promptTokens, completionTokens);
	}

	private static void addTo(long[] tokens, long promptTokens, long completionTokens) {
		if (tokens == null) {
			return;
		}
		synchronized (tokens) {
			tokens[0] += promptTokens;
			tokens[1] += completionTokens;
		}
	}

	/**
	 * 结束 Span（成功），附带累计的 token 用量
	 */
	public void endSpanSuccess(Span span, String threadId, String output) {
		if (!enabled || span == null || !span.isRecording()) {
			return;
		}

		try {
			span.setAttribute(OUTPUT_VALUE, output != null ? output : "");
			applyAccumulatedTokens(span, threadId);
			span.setStatus(StatusCode.OK);
		}
		catch (Exception e) {
			log.error("Failed to end OTel span", e);
		}
		finally {
			span.end();
		}
	}

	/**
	 * 结束 Span（失败）
	 */
	public void endSpanError(Span span, String threadId, Exception error) {
		if (!enabled || span == null || !span.isRecording()) {
			return;
		}

		try {
			String errorType = error.getClass().getSimpleName();
			String errorMessage = error.getMessage() != null ? error.getMessage() : "";

			span.setAttribute(ERROR_TYPE, errorType);
			span.setAttribute(ERROR_MESSAGE, errorMessage);
			applyAccumulatedTokens(span, threadId);
			span.setStatus(StatusCode.ERROR, errorType + ": " + errorMessage);
			span.recordException(error);
		}
		catch (Exception e) {
			log.error("Failed to record span error", e);
		}
		finally {
			span.end();
		}
	}

	/**
	 * 读取并清除累计的 token，写入 span attributes
	 */
	private void applyAccumulatedTokens(Span span, String threadId) {
		if (threadId == null) {
			return;
		}
		ROOT_SPANS.remove(threadId);
		ACTIVE_NODE_ACCUMULATOR.remove(threadId);
		long[] tokens = TOKEN_ACCUMULATOR.remove(threadId);
		if (tokens != null) {
			synchronized (tokens) {
				if (tokens[0] > 0 || tokens[1] > 0) {
					span.setAttribute(GEN_AI_PROMPT_TOKENS, tokens[0]);
					span.setAttribute(GEN_AI_COMPLETION_TOKENS, tokens[1]);
					span.setAttribute(GEN_AI_TOTAL_TOKENS, tokens[0] + tokens[1]);
				}
			}
		}
	}

}
