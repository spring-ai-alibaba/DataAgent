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

import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

	public LangfuseService(Tracer langfuseTracer, @Value("${langfuse.enabled:true}") boolean enabled) {
		this.tracer = langfuseTracer;
		this.enabled = enabled;
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
			}

			return span;
		}
		catch (Exception e) {
			log.error("Failed to start OTel span", e);
			return Span.getInvalid();
		}
	}

	/**
	 * 累计 token 用量（由 FluxUtil 在处理 ChatResponse 时调用）
	 */
	public static void accumulateTokens(Object threadId, long promptTokens, long completionTokens) {
		if (threadId == null) {
			return;
		}
		long[] tokens = TOKEN_ACCUMULATOR.get(threadId);
		if (tokens != null) {
			synchronized (tokens) {
				tokens[0] += promptTokens;
				tokens[1] += completionTokens;
			}
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
