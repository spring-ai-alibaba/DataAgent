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
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LangfuseServiceTest {

	@Mock
	private Tracer tracer;

	@Mock
	private SpanBuilder spanBuilder;

	@Mock
	private Span span;

	private LangfuseService langfuseService;

	@BeforeEach
	void setUp() {
		langfuseService = new LangfuseService(tracer, true);
	}

	@Test
	void startLLMSpan_disabled_returnsInvalidSpan() {
		LangfuseService disabledService = new LangfuseService(tracer, false);
		GraphRequest request = new GraphRequest();
		request.setQuery("test");

		Span result = disabledService.startLLMSpan("test-span", request);

		assertFalse(result.isRecording());
	}

	@Test
	void startLLMSpan_enabled_returnsSpan() {
		GraphRequest request = new GraphRequest();
		request.setQuery("test query");
		request.setAgentId("agent-1");
		request.setThreadId("thread-1");

		when(tracer.spanBuilder("test-span")).thenReturn(spanBuilder);
		when(spanBuilder.setSpanKind(any())).thenReturn(spanBuilder);
		when(spanBuilder.setParent(any())).thenReturn(spanBuilder);
		when(spanBuilder.startSpan()).thenReturn(span);

		Span result = langfuseService.startLLMSpan("test-span", request);

		assertNotNull(result);
		assertEquals(span, result);
		verify(tracer).spanBuilder("test-span");
	}

	@Test
	void startLLMSpan_exceptionInTracer_returnsInvalidSpan() {
		GraphRequest request = new GraphRequest();
		when(tracer.spanBuilder(anyString())).thenThrow(new RuntimeException("tracer error"));

		Span result = langfuseService.startLLMSpan("test", request);
		assertFalse(result.isRecording());
	}

	@Test
	void accumulateTokens_nullThreadId_doesNothing() {
		LangfuseService.accumulateTokens(null, 10, 20);
	}

	@Test
	void accumulateTokens_validThreadId_accumulatesTokens() {
		GraphRequest request = new GraphRequest();
		request.setThreadId("token-thread");
		request.setQuery("q");

		when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
		when(spanBuilder.setSpanKind(any())).thenReturn(spanBuilder);
		when(spanBuilder.setParent(any())).thenReturn(spanBuilder);
		when(spanBuilder.startSpan()).thenReturn(span);

		langfuseService.startLLMSpan("span", request);

		LangfuseService.accumulateTokens("token-thread", 100, 200);
		LangfuseService.accumulateTokens("token-thread", 50, 100);
	}

	@Test
	void endSpanSuccess_disabled_doesNothing() {
		LangfuseService disabledService = new LangfuseService(tracer, false);
		disabledService.endSpanSuccess(span, "thread", "output");
		verify(span, never()).end();
	}

	@Test
	void endSpanSuccess_nullSpan_doesNothing() {
		langfuseService.endSpanSuccess(null, "thread", "output");
	}

	@Test
	void endSpanSuccess_validSpan_endsSpan() {
		when(span.isRecording()).thenReturn(true);

		langfuseService.endSpanSuccess(span, "thread-end", "test output");

		verify(span).setStatus(any());
		verify(span).end();
	}

	@Test
	void endSpanError_disabled_doesNothing() {
		LangfuseService disabledService = new LangfuseService(tracer, false);
		disabledService.endSpanError(span, "thread", new RuntimeException("err"));
		verify(span, never()).end();
	}

	@Test
	void endSpanError_validSpan_recordsException() {
		when(span.isRecording()).thenReturn(true);
		RuntimeException error = new RuntimeException("test error");

		langfuseService.endSpanError(span, "thread-err", error);

		verify(span).recordException(error);
		verify(span).end();
	}

	@Test
	void endSpanError_nullErrorMessage_handlesGracefully() {
		when(span.isRecording()).thenReturn(true);
		RuntimeException error = new RuntimeException((String) null);

		langfuseService.endSpanError(span, "thread-err", error);

		verify(span).end();
	}

	// --- 根 span 注册表（供节点级子 span 找父 span） ---

	@Test
	void getRootSpan_returnsSpanRegisteredByStartLLMSpan() {
		GraphRequest request = requestWithThreadId("root-thread");
		stubTracer();

		langfuseService.startLLMSpan("graph-stream", request);

		assertSame(span, langfuseService.getRootSpan("root-thread"));
	}

	@Test
	void getRootSpan_returnsNullForUnknownOrNullThreadId() {
		assertNull(langfuseService.getRootSpan("never-started"));
		assertNull(langfuseService.getRootSpan(null));
	}

	@Test
	void getRootSpan_returnsNullAfterSpanSuccessfullyEnded() {
		GraphRequest request = requestWithThreadId("ending-thread");
		stubTracer();
		when(span.isRecording()).thenReturn(true);
		langfuseService.startLLMSpan("graph-stream", request);

		langfuseService.endSpanSuccess(span, "ending-thread", "done");

		assertNull(langfuseService.getRootSpan("ending-thread"),
				"root span must be de-registered so late node callbacks cannot attach to a finished span");
	}

	@Test
	void getRootSpan_returnsNullAfterSpanEndedWithError() {
		GraphRequest request = requestWithThreadId("failing-thread");
		stubTracer();
		when(span.isRecording()).thenReturn(true);
		langfuseService.startLLMSpan("graph-stream", request);

		langfuseService.endSpanError(span, "failing-thread", new RuntimeException("boom"));

		assertNull(langfuseService.getRootSpan("failing-thread"));
	}

	@Test
	void getRootSpan_notRegisteredWhenLangfuseDisabled() {
		LangfuseService disabled = new LangfuseService(tracer, false);

		disabled.startLLMSpan("graph-stream", requestWithThreadId("disabled-thread"));

		assertNull(disabled.getRootSpan("disabled-thread"));
	}

	// --- 活跃 token 累加器（按 attempt 分离节点级 token） ---

	@Test
	void activeAccumulator_collectsTokensBetweenRegisterAndTake() {
		LangfuseService.registerActiveAccumulator("acc-thread");

		LangfuseService.accumulateTokens("acc-thread", 100, 20);
		LangfuseService.accumulateTokens("acc-thread", 50, 10);

		assertArrayEquals(new long[] { 150, 30 }, LangfuseService.takeActiveAccumulator("acc-thread"));
	}

	@Test
	void takeActiveAccumulator_removesTheAccumulator() {
		LangfuseService.registerActiveAccumulator("once-thread");
		LangfuseService.accumulateTokens("once-thread", 10, 5);

		assertArrayEquals(new long[] { 10, 5 }, LangfuseService.takeActiveAccumulator("once-thread"));
		assertNull(LangfuseService.takeActiveAccumulator("once-thread"), "taking twice must not return stale tokens");
	}

	/**
	 * 本 Issue 的核心价值点：同一节点重试三次，每次 token 必须各自独立，不能合并成一个数字。 重试时 prompt
	 * 会系统性膨胀，合并后就无法区分「5000×3」和「2000+5000+8000」。
	 */
	@Test
	void sequentialRetries_eachAttemptAccumulatesIndependently() {
		long[][] perAttempt = new long[3][];

		for (int attempt = 0; attempt < 3; attempt++) {
			LangfuseService.registerActiveAccumulator("retry-thread");
			// 模拟 prompt 递增：第 N 次每 chunk 消耗 100*(N+1)
			long perChunk = 100L * (attempt + 1);
			LangfuseService.accumulateTokens("retry-thread", perChunk, perChunk);
			LangfuseService.accumulateTokens("retry-thread", perChunk, perChunk);
			perAttempt[attempt] = LangfuseService.takeActiveAccumulator("retry-thread");
		}

		assertArrayEquals(new long[] { 200, 200 }, perAttempt[0]);
		assertArrayEquals(new long[] { 400, 400 }, perAttempt[1]);
		assertArrayEquals(new long[] { 600, 600 }, perAttempt[2]);
	}

	@Test
	void concurrentThreadIds_doNotShareTokens() {
		LangfuseService.registerActiveAccumulator("thread-a");
		LangfuseService.registerActiveAccumulator("thread-b");

		LangfuseService.accumulateTokens("thread-a", 300, 0);
		LangfuseService.accumulateTokens("thread-b", 6, 0);

		assertArrayEquals(new long[] { 300, 0 }, LangfuseService.takeActiveAccumulator("thread-a"));
		assertArrayEquals(new long[] { 6, 0 }, LangfuseService.takeActiveAccumulator("thread-b"));
	}

	@Test
	void registerActiveAccumulator_replacesAnyStaleAccumulator() {
		LangfuseService.registerActiveAccumulator("stale-thread");
		LangfuseService.accumulateTokens("stale-thread", 999, 999);

		// 上一次 attempt 未被 take（如异常路径），新 attempt 必须从零开始
		LangfuseService.registerActiveAccumulator("stale-thread");
		LangfuseService.accumulateTokens("stale-thread", 1, 2);

		assertArrayEquals(new long[] { 1, 2 }, LangfuseService.takeActiveAccumulator("stale-thread"));
	}

	@Test
	void discardAccumulators_clearsBothRootAndActiveState() {
		GraphRequest request = requestWithThreadId("discard-thread");
		stubTracer();
		langfuseService.startLLMSpan("graph-stream", request);
		LangfuseService.registerActiveAccumulator("discard-thread");
		LangfuseService.accumulateTokens("discard-thread", 10, 10);

		LangfuseService.discardAccumulators("discard-thread");

		assertNull(LangfuseService.takeActiveAccumulator("discard-thread"));
	}

	/**
	 * Langfuse 未启用（或节点在 register 之前就产出 token）时，累加调用必须静默无副作用。
	 */
	@Test
	void accumulateTokens_withoutActiveAccumulator_doesNotThrow() {
		assertDoesNotThrow(() -> LangfuseService.accumulateTokens("unregistered-thread", 100, 100));
		assertNull(LangfuseService.takeActiveAccumulator("unregistered-thread"));
	}

	@Test
	void activeAccumulatorApis_toleratesNullThreadId() {
		assertDoesNotThrow(() -> LangfuseService.registerActiveAccumulator(null));
		assertDoesNotThrow(() -> LangfuseService.discardAccumulators(null));
		assertNull(LangfuseService.takeActiveAccumulator(null));
	}

	/**
	 * 回归保护：节点级累加器不得影响根 span 的 token 汇总。两者必须并存，便于交叉核对。
	 */
	@Test
	void nodeLevelAccumulationDoesNotDisturbRootSpanTotals() {
		GraphRequest request = requestWithThreadId("both-thread");
		stubTracer();
		when(span.isRecording()).thenReturn(true);
		langfuseService.startLLMSpan("graph-stream", request);

		LangfuseService.registerActiveAccumulator("both-thread");
		LangfuseService.accumulateTokens("both-thread", 70, 30);
		LangfuseService.takeActiveAccumulator("both-thread");

		langfuseService.endSpanSuccess(span, "both-thread", "out");

		// 根 span 仍应收到同一批 token 的汇总
		verify(span).setAttribute(AttributeKey.longKey("gen_ai.usage.prompt_tokens"), 70L);
		verify(span).setAttribute(AttributeKey.longKey("gen_ai.usage.completion_tokens"), 30L);
		verify(span).setAttribute(AttributeKey.longKey("gen_ai.usage.total_tokens"), 100L);
	}

	// --- helpers ---

	/**
	 * 请求各字段为 null / threadId 缺失时的降级路径：既不能抛异常，也不能往注册表里塞 null key。
	 */
	@Test
	void startLLMSpan_withAllNullRequestFields_doesNotThrowAndSkipsRegistration() {
		stubTracer();
		GraphRequest blank = new GraphRequest();

		Span result = langfuseService.startLLMSpan("graph-stream", blank);

		assertSame(span, result);
		assertNull(langfuseService.getRootSpan(null));
	}

	@Test
	void endSpanSuccess_nullOutput_isRecordedAsEmptyString() {
		when(span.isRecording()).thenReturn(true);

		langfuseService.endSpanSuccess(span, "null-output-thread", null);

		verify(span).setAttribute(AttributeKey.stringKey("output.value"), "");
		verify(span).end();
	}

	@Test
	void endSpanSuccess_nonRecordingSpan_doesNothing() {
		when(span.isRecording()).thenReturn(false);

		langfuseService.endSpanSuccess(span, "thread", "out");

		verify(span, never()).end();
	}

	@Test
	void endSpanError_nullSpanOrNonRecording_doesNothing() {
		langfuseService.endSpanError(null, "thread", new RuntimeException("x"));
		when(span.isRecording()).thenReturn(false);

		langfuseService.endSpanError(span, "thread", new RuntimeException("x"));

		verify(span, never()).end();
	}

	/**
	 * threadId 为 null 时不得触碰任何注册表（否则 ConcurrentHashMap 会因 null key 抛 NPE）。
	 */
	@Test
	void endSpan_withNullThreadId_skipsTokenApplication() {
		when(span.isRecording()).thenReturn(true);

		assertDoesNotThrow(() -> langfuseService.endSpanSuccess(span, null, "out"));

		verify(span).end();
		verify(span, never()).setAttribute(AttributeKey.longKey("gen_ai.usage.total_tokens"), 0L);
	}

	/**
	 * 累计值为 0 时不写 token 属性——避免面板上出现一堆 0 token 的噪音。
	 */
	@Test
	void endSpanSuccess_withZeroTokens_doesNotSetTokenAttributes() {
		GraphRequest request = requestWithThreadId("zero-token-thread");
		stubTracer();
		when(span.isRecording()).thenReturn(true);
		langfuseService.startLLMSpan("graph-stream", request);

		langfuseService.endSpanSuccess(span, "zero-token-thread", "out");

		verify(span, never()).setAttribute(AttributeKey.longKey("gen_ai.usage.prompt_tokens"), 0L);
	}

	/**
	 * span.setAttribute 抛异常时必须被 catch 住，且仍要 end()，否则 span 永远挂着。
	 */
	@Test
	void endSpanSuccess_exceptionFromSpan_isSwallowedAndSpanStillEnded() {
		when(span.isRecording()).thenReturn(true);
		when(span.setAttribute(AttributeKey.stringKey("output.value"), "out"))
			.thenThrow(new RuntimeException("span rejected"));

		assertDoesNotThrow(() -> langfuseService.endSpanSuccess(span, "boom-thread", "out"));

		verify(span).end();
	}

	@Test
	void endSpanError_exceptionFromSpan_isSwallowedAndSpanStillEnded() {
		when(span.isRecording()).thenReturn(true);
		when(span.setAttribute(AttributeKey.stringKey("error.type"), "RuntimeException"))
			.thenThrow(new RuntimeException("span rejected"));

		assertDoesNotThrow(() -> langfuseService.endSpanError(span, "boom-thread", new RuntimeException("orig")));

		verify(span).end();
	}

	/**
	 * 只有 completion token（prompt 为 0）时，根 span 仍须记录 —— 覆盖 {@code tokens[1] > 0} 分支。
	 */
	@Test
	void endSpanSuccess_withCompletionTokensOnly_setsTokenAttributes() {
		GraphRequest request = requestWithThreadId("completion-only-thread");
		stubTracer();
		when(span.isRecording()).thenReturn(true);
		langfuseService.startLLMSpan("graph-stream", request);
		LangfuseService.accumulateTokens("completion-only-thread", 0, 90);

		langfuseService.endSpanSuccess(span, "completion-only-thread", "out");

		verify(span).setAttribute(AttributeKey.longKey("gen_ai.usage.completion_tokens"), 90L);
		verify(span).setAttribute(AttributeKey.longKey("gen_ai.usage.total_tokens"), 90L);
	}

	@Test
	void accumulateTokens_intoRootAccumulatorOnly_whenNoActiveNode() {		GraphRequest request = requestWithThreadId("root-only-thread");
		stubTracer();
		when(span.isRecording()).thenReturn(true);
		langfuseService.startLLMSpan("graph-stream", request);

		// 没有 registerActiveAccumulator：只有根累加器收到 token
		LangfuseService.accumulateTokens("root-only-thread", 40, 60);

		assertNull(LangfuseService.takeActiveAccumulator("root-only-thread"));
		langfuseService.endSpanSuccess(span, "root-only-thread", "out");
		verify(span).setAttribute(AttributeKey.longKey("gen_ai.usage.total_tokens"), 100L);
	}

	private GraphRequest requestWithThreadId(String threadId) {		GraphRequest request = new GraphRequest();
		request.setQuery("q");
		request.setThreadId(threadId);
		return request;
	}

	private void stubTracer() {
		when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
		when(spanBuilder.setSpanKind(any())).thenReturn(spanBuilder);
		when(spanBuilder.setParent(any())).thenReturn(spanBuilder);
		when(spanBuilder.startSpan()).thenReturn(span);
	}

}
