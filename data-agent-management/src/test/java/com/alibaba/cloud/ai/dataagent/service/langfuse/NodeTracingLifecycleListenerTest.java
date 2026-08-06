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

import com.alibaba.cloud.ai.graph.RunnableConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link NodeTracingLifecycleListener} 的行为测试。
 *
 * <p>
 * 使用真实的 OTel SDK 配一个内存 exporter，断言的是<b>最终导出的 span 数据</b>（属性、状态、父子关系），而不是对
 * mock 的调用次数——本功能的价值恰恰体现在"Langfuse 上看到的东西对不对"，用 mock 验证 setter 调用无法覆盖
 * 状态判定这类核心逻辑。
 *
 * @author suke
 */
class NodeTracingLifecycleListenerTest {

	private static final String THREAD_ID = "thread-1";

	private static final AttributeKey<Long> ATTEMPT = AttributeKey.longKey("data_agent.node_attempt");

	private static final AttributeKey<String> INPUT_VALUE = AttributeKey.stringKey("input.value");

	private static final AttributeKey<String> OUTPUT_VALUE = AttributeKey.stringKey("output.value");

	private static final AttributeKey<Long> PROMPT_TOKENS = AttributeKey.longKey("gen_ai.usage.prompt_tokens");

	private static final AttributeKey<Long> TOTAL_TOKENS = AttributeKey.longKey("gen_ai.usage.total_tokens");

	private final RecordingExporter exporter = new RecordingExporter();

	private SdkTracerProvider tracerProvider;

	private Tracer tracer;

	private LangfuseService langfuseService;

	private NodeTracingLifecycleListener listener;

	private Span rootSpan;

	private RunnableConfig config;

	@BeforeEach
	void setUp() {
		tracerProvider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
		tracer = tracerProvider.get("test");
		langfuseService = mock(LangfuseService.class);
		listener = new NodeTracingLifecycleListener(tracer, langfuseService);

		rootSpan = tracer.spanBuilder("graph-stream").startSpan();
		when(langfuseService.getRootSpan(THREAD_ID)).thenReturn(rootSpan);

		config = RunnableConfig.builder().threadId(THREAD_ID).build();

		LangfuseService.discardAccumulators(THREAD_ID);
	}

	@AfterEach
	void tearDown() {
		LangfuseService.discardAccumulators(THREAD_ID);
		rootSpan.end();
		tracerProvider.close();
	}

	@Test
	void singleExecution_recordsInputOutputAttemptAndTokens() {
		Map<String, Object> before = Map.of(INPUT_KEY, "查一下销量", MULTI_TURN_CONTEXT, "(无)");
		listener.before(INTENT_RECOGNITION_NODE, before, config, 1L);

		// 模拟节点内 LLM 流式产出 token
		LangfuseService.accumulateTokens(THREAD_ID, 120, 30);

		Map<String, Object> after = new HashMap<>(before);
		after.put(INTENT_RECOGNITION_NODE_OUTPUT, "DATA_ANALYSIS");
		listener.after(INTENT_RECOGNITION_NODE, after, config, 2L);

		SpanData span = onlyNodeSpan();
		assertEquals(INTENT_RECOGNITION_NODE, span.getName());
		assertEquals(StatusCode.OK, span.getStatus().getStatusCode());
		assertEquals(1L, span.getAttributes().get(ATTEMPT));
		assertEquals(120L, span.getAttributes().get(PROMPT_TOKENS));
		assertEquals(150L, span.getAttributes().get(TOTAL_TOKENS));
		// 输入只含该节点声明的 key，不含后续节点的输出
		assertTrue(span.getAttributes().get(INPUT_VALUE).contains("查一下销量"));
		assertTrue(span.getAttributes().get(OUTPUT_VALUE).contains("DATA_ANALYSIS"));
	}

	@Test
	void nodeSpanIsChildOfTheRootSpan() {
		listener.before(INTENT_RECOGNITION_NODE, Map.of(INPUT_KEY, "q"), config, 1L);
		listener.after(INTENT_RECOGNITION_NODE, Map.of(INTENT_RECOGNITION_NODE_OUTPUT, "x"), config, 2L);

		SpanData span = onlyNodeSpan();
		assertEquals(rootSpan.getSpanContext().getSpanId(), span.getParentSpanId());
		assertEquals(rootSpan.getSpanContext().getTraceId(), span.getTraceId());
	}

	/**
	 * 输入白名单必须过滤掉与本节点无关的 state key。{@code OverAllState} 单调累加，若不过滤，靠后的节点 span
	 * 会把前面所有节点的输出都当成自己的输入。
	 */
	@Test
	void inputAttributeExcludesUnrelatedStateKeys() {
		Map<String, Object> state = Map.of(INPUT_KEY, "q", MULTI_TURN_CONTEXT, "(无)", SQL_GENERATE_OUTPUT,
				"SELECT * FROM orders", TABLE_RELATION_OUTPUT, "schema-blob");

		listener.before(INTENT_RECOGNITION_NODE, state, config, 1L);
		listener.after(INTENT_RECOGNITION_NODE, Map.of(INTENT_RECOGNITION_NODE_OUTPUT, "x"), config, 2L);

		String input = onlyNodeSpan().getAttributes().get(INPUT_VALUE);
		assertFalse(input.contains("SELECT * FROM orders"), "unrelated node output must not appear as this node's input");
		assertFalse(input.contains("schema-blob"));
	}

	/**
	 * 本 Issue 的核心：同一节点重试两次要产生两个独立子 span，attempt 递增，且 token 各自独立不合并。
	 */
	@Test
	void tworetries_produceTwoSpansWithIndependentAttemptAndTokens() {
		Map<String, Object> state = new HashMap<>(Map.of(SQL_GENERATE_COUNT, 0));

		listener.before(SQL_GENERATE_NODE, state, config, 1L);
		LangfuseService.accumulateTokens(THREAD_ID, 2000, 0);
		state.put(SQL_GENERATE_OUTPUT, "SELECT 1");
		listener.after(SQL_GENERATE_NODE, state, config, 2L);

		listener.before(SQL_GENERATE_NODE, state, config, 3L);
		// 重试 prompt 膨胀：带上了失败 SQL 和错误信息
		LangfuseService.accumulateTokens(THREAD_ID, 5000, 0);
		state.put(SQL_GENERATE_OUTPUT, "SELECT 2");
		listener.after(SQL_GENERATE_NODE, state, config, 4L);

		List<SpanData> spans = nodeSpans();
		assertEquals(2, spans.size());
		assertEquals(1L, spans.get(0).getAttributes().get(ATTEMPT));
		assertEquals(2L, spans.get(1).getAttributes().get(ATTEMPT));
		// 关键：不是 7000/7000，而是各自独立——否则无法区分「2000×2」和「2000+5000」
		assertEquals(2000L, spans.get(0).getAttributes().get(PROMPT_TOKENS));
		assertEquals(5000L, spans.get(1).getAttributes().get(PROMPT_TOKENS));
	}

	/**
	 * 回归测试（本功能最容易静默失效的地方）：LLM 调用失败时框架走的是 {@code after} 而非 {@code onError}，
	 * state 完全没被改动。此时 span 必须是 ERROR，否则失败节点会在 Langfuse 上显示为绿色成功。
	 */
	@Test
	void afterWithNoStateChange_marksSpanAsError() {
		Map<String, Object> state = Map.of(INPUT_KEY, "q", EVIDENCE, "无");

		listener.before(QUERY_ENHANCE_NODE, state, config, 1L);
		// resultSupplier 从未运行（FluxUtil 把异常吞成了数据元素），state 原样返回
		listener.after(QUERY_ENHANCE_NODE, state, config, 2L);

		SpanData span = onlyNodeSpan();
		assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode(),
				"a node that produced no new output must not be reported as OK");
	}

	/**
	 * 单调累加的陷阱：第二次尝试失败时，第一次尝试写入的 output key 仍在 state 里。 若只判断"key 是否存在"，失败的重试会被误判为成功。
	 */
	@Test
	void failedRetryIsStillErrorEvenThoughEarlierAttemptLeftItsOutputInState() {
		Map<String, Object> state = new HashMap<>();

		listener.before(QUERY_ENHANCE_NODE, state, config, 1L);
		state.put(QUERY_ENHANCE_NODE_OUTPUT, "enhanced-v1");
		listener.after(QUERY_ENHANCE_NODE, state, config, 2L);

		// 第二次尝试：LLM 挂了，state 保持上一次的值不变
		listener.before(QUERY_ENHANCE_NODE, state, config, 3L);
		listener.after(QUERY_ENHANCE_NODE, state, config, 4L);

		List<SpanData> spans = nodeSpans();
		assertEquals(StatusCode.OK, spans.get(0).getStatus().getStatusCode());
		assertEquals(StatusCode.ERROR, spans.get(1).getStatus().getStatusCode(),
				"the stale key from attempt 1 must not make the failed attempt 2 look successful");
	}

	/**
	 * 业务校验失败不是节点故障：{@code PlanExecutorNode} 正常工作并如实报告校验结果，有自己的重试机制。 标红会制造噪音、掩盖真故障。
	 */
	@Test
	void validationFailurePath_isReportedAsOk() {
		Map<String, Object> state = new HashMap<>(Map.of(PLANNER_NODE_OUTPUT, "{bad json}"));

		listener.before(PLAN_EXECUTOR_NODE, state, config, 1L);
		state.put(PLAN_VALIDATION_STATUS, false);
		state.put(PLAN_VALIDATION_ERROR, "plan structure invalid");
		state.put(PLAN_REPAIR_COUNT, 1);
		listener.after(PLAN_EXECUTOR_NODE, state, config, 2L);

		SpanData span = onlyNodeSpan();
		assertEquals(StatusCode.OK, span.getStatus().getStatusCode(),
				"business validation failure is not a node failure");
		assertTrue(span.getAttributes().get(OUTPUT_VALUE).contains("plan structure invalid"),
				"the validation error must still be visible in the span output for troubleshooting");
	}

	/**
	 * 降级路径写的是另一组 key（不含外层信封 {@code PYTHON_ANALYSIS_NODE_OUTPUT}），属合法输出。
	 */
	@Test
	void degradedFallbackPath_isReportedAsOk() {
		Map<String, Object> state = new HashMap<>(Map.of(PYTHON_FALLBACK_MODE, true, PLAN_CURRENT_STEP, 2));

		listener.before(PYTHON_ANALYZE_NODE, state, config, 1L);
		state.put(SQL_EXECUTE_NODE_OUTPUT, Map.of("step_2_analysis", "Python 高级分析功能暂时不可用"));
		state.put(PLAN_CURRENT_STEP, 3);
		listener.after(PYTHON_ANALYZE_NODE, state, config, 2L);

		assertEquals(StatusCode.OK, onlyNodeSpan().getStatus().getStatusCode());
	}

	/**
	 * {@code SchemaRecallNode} 的外层信封 {@code SCHEMA_RECALL_NODE_OUTPUT} 永不进 state，
	 * 白名单登记的是内层两个 key。若登记错了，这个节点会每次都判失败。
	 */
	@Test
	void schemaRecallNode_isReportedAsOkUsingInnerKeys() {
		Map<String, Object> state = new HashMap<>(Map.of(AGENT_ID, "1"));

		listener.before(SCHEMA_RECALL_NODE, state, config, 1L);
		state.put(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, List.of("orders"));
		state.put(COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, List.of("orders.id"));
		listener.after(SCHEMA_RECALL_NODE, state, config, 2L);

		assertEquals(StatusCode.OK, onlyNodeSpan().getStatus().getStatusCode());
	}

	@Test
	void onError_marksSpanAsErrorAndRecordsException() {
		listener.before(EVIDENCE_RECALL_NODE, Map.of(INPUT_KEY, "q"), config, 1L);

		listener.onError(EVIDENCE_RECALL_NODE, Map.of(), new IllegalStateException("boom"), config);

		SpanData span = onlyNodeSpan();
		assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
		assertTrue(span.getStatus().getDescription().contains("boom"));
		assertEquals(1, span.getEvents().size(), "the exception should be recorded as a span event");
	}

	@Test
	void onError_withNullThrowable_stillEndsSpanAsError() {
		listener.before(EVIDENCE_RECALL_NODE, Map.of(INPUT_KEY, "q"), config, 1L);

		listener.onError(EVIDENCE_RECALL_NODE, Map.of(), null, config);

		assertEquals(StatusCode.ERROR, onlyNodeSpan().getStatus().getStatusCode());
	}

	/**
	 * 未启用 Langfuse（无根 span）时必须完全静默：不创建 span、不注册累加器、不抛异常。
	 */
	@Test
	void withoutRootSpan_doesNothing() {
		when(langfuseService.getRootSpan(THREAD_ID)).thenReturn(null);

		assertDoesNotThrow(() -> {
			listener.before(INTENT_RECOGNITION_NODE, Map.of(INPUT_KEY, "q"), config, 1L);
			listener.after(INTENT_RECOGNITION_NODE, Map.of(INTENT_RECOGNITION_NODE_OUTPUT, "x"), config, 2L);
		});

		assertTrue(nodeSpans().isEmpty());
		assertNull(LangfuseService.takeActiveAccumulator(THREAD_ID));
	}

	@Test
	void withoutThreadId_doesNothing() {
		RunnableConfig noThread = RunnableConfig.builder().build();

		assertDoesNotThrow(() -> {
			listener.before(INTENT_RECOGNITION_NODE, Map.of(INPUT_KEY, "q"), noThread, 1L);
			listener.after(INTENT_RECOGNITION_NODE, Map.of(INTENT_RECOGNITION_NODE_OUTPUT, "x"), noThread, 2L);
		});

		assertTrue(nodeSpans().isEmpty());
	}

	@Test
	void nullConfig_doesNotThrow() {
		assertDoesNotThrow(() -> {
			listener.before(INTENT_RECOGNITION_NODE, Map.of(), null, 1L);
			listener.after(INTENT_RECOGNITION_NODE, Map.of(), null, 2L);
			listener.onError(INTENT_RECOGNITION_NODE, Map.of(), new RuntimeException("x"), null);
		});
		assertTrue(nodeSpans().isEmpty());
	}

	/**
	 * {@code interruptBefore} 会在节点执行前直接返回，此时 {@code before} 压根不触发。 孤立的 {@code after}
	 * 不得抛异常，也不得凭空造出 span。
	 */
	@Test
	void afterWithoutBefore_isIgnored() {
		assertDoesNotThrow(() -> listener.after(HUMAN_FEEDBACK_NODE, Map.of(), config, 1L));

		assertTrue(nodeSpans().isEmpty());
	}

	/**
	 * 无 LLM 调用的节点（{@code PlanExecutorNode}）没有 token，span 仍须正常结束且不带 token 属性。
	 */
	@Test
	void nodeWithoutLlmCall_endsSpanWithoutTokenAttributes() {
		Map<String, Object> state = new HashMap<>(Map.of(PLANNER_NODE_OUTPUT, "plan"));

		listener.before(PLAN_EXECUTOR_NODE, state, config, 1L);
		state.put(PLAN_NEXT_NODE, SQL_EXECUTE_NODE);
		state.put(PLAN_VALIDATION_STATUS, true);
		listener.after(PLAN_EXECUTOR_NODE, state, config, 2L);

		SpanData span = onlyNodeSpan();
		assertEquals(StatusCode.OK, span.getStatus().getStatusCode());
		assertNull(span.getAttributes().get(PROMPT_TOKENS));
		assertNull(span.getAttributes().get(TOTAL_TOKENS));
	}

	/**
	 * 未登记的 nodeId（理论上被 {@code NodeIoRegistryTest} 的覆盖断言拦住）不应导致异常； span 仍会创建，只是 input/output
	 * 为空——这样至少能在 Langfuse 上看到该节点执行过。
	 */
	@Test
	void unregisteredNode_stillProducesASpanWithEmptyIo() {
		listener.before("NOT_IN_REGISTRY", Map.of(INPUT_KEY, "q"), config, 1L);
		listener.after("NOT_IN_REGISTRY", Map.of(INPUT_KEY, "q"), config, 2L);

		SpanData span = onlyNodeSpan();
		assertEquals("{}", span.getAttributes().get(INPUT_VALUE));
		// 无声明的 output key ⇒ 无法判定产出 ⇒ 保守标 ERROR，以免真失败被漏标
		assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
	}

	@Test
	void longAttributeValuesAreTruncated() {
		String hugeSql = "SELECT " + "x".repeat(20_000);
		Map<String, Object> state = new HashMap<>();

		listener.before(SQL_GENERATE_NODE, state, config, 1L);
		state.put(SQL_GENERATE_OUTPUT, hugeSql);
		listener.after(SQL_GENERATE_NODE, state, config, 2L);

		String output = onlyNodeSpan().getAttributes().get(OUTPUT_VALUE);
		assertTrue(output.contains("(truncated"), "oversized values must be truncated to keep spans exportable");
		assertTrue(output.length() < hugeSql.length());
	}

	/**
	 * 客户端断开是唯一绕过 {@code after}/{@code onError} 的路径。未结束的 span 必须被标记并结束， 否则内存泄漏 +
	 * Langfuse 上出现永不结束的 span。
	 */
	@Test
	void discardThread_endsDanglingSpansAndClearsState() {
		listener.before(SQL_EXECUTE_NODE, Map.of(SQL_GENERATE_OUTPUT, "SELECT 1"), config, 1L);
		LangfuseService.accumulateTokens(THREAD_ID, 10, 10);

		listener.discardThread(THREAD_ID);

		SpanData span = onlyNodeSpan();
		assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
		assertTrue(span.getStatus().getDescription().contains("disconnected"));
		assertNull(LangfuseService.takeActiveAccumulator(THREAD_ID));
	}

	/**
	 * 断开后 attempt 计数器必须清零，否则同一 threadId 被复用时 attempt 会从旧值接着涨。
	 */
	@Test
	void discardThread_resetsAttemptCounters() {
		Map<String, Object> state = new HashMap<>();
		listener.before(SQL_GENERATE_NODE, state, config, 1L);
		state.put(SQL_GENERATE_OUTPUT, "SELECT 1");
		listener.after(SQL_GENERATE_NODE, state, config, 2L);

		listener.discardThread(THREAD_ID);
		exporter.clear();

		Map<String, Object> fresh = new HashMap<>();
		listener.before(SQL_GENERATE_NODE, fresh, config, 3L);
		fresh.put(SQL_GENERATE_OUTPUT, "SELECT 2");
		listener.after(SQL_GENERATE_NODE, fresh, config, 4L);

		assertEquals(1L, onlyNodeSpan().getAttributes().get(ATTEMPT), "attempt must restart after the thread is discarded");
	}

	/**
	 * 只有 completion token 而 prompt token 为 0（部分 provider 的流式响应会这样），仍须记录。
	 */
	@Test
	void completionOnlyTokensAreStillRecorded() {
		Map<String, Object> state = new HashMap<>();
		listener.before(SQL_GENERATE_NODE, state, config, 1L);
		LangfuseService.accumulateTokens(THREAD_ID, 0, 250);
		state.put(SQL_GENERATE_OUTPUT, "SELECT 1");
		listener.after(SQL_GENERATE_NODE, state, config, 2L);

		SpanData span = onlyNodeSpan();
		assertEquals(0L, span.getAttributes().get(PROMPT_TOKENS));
		assertEquals(250L, span.getAttributes().get(TOTAL_TOKENS));
	}

	/**
	 * 同一个节点连续两次 after（栈已空）：第二次必须被忽略，不能凭空造 span 也不能抛异常。
	 */
	@Test
	void secondAfterOnEmptyStackIsIgnored() {
		Map<String, Object> state = new HashMap<>();
		listener.before(SQL_GENERATE_NODE, state, config, 1L);
		state.put(SQL_GENERATE_OUTPUT, "SELECT 1");
		listener.after(SQL_GENERATE_NODE, state, config, 2L);

		assertDoesNotThrow(() -> listener.after(SQL_GENERATE_NODE, state, config, 3L));

		assertEquals(1, nodeSpans().size(), "a duplicate after must not create another span");
	}

	/**
	 * after 阶段 span 抛异常时，catch 分支必须兜住并仍然 end()——否则 span 永远挂着。 这里让 setStatus
	 * 抛异常，覆盖"输出已写入但状态判定失败"的路径。
	 */
	@Test
	void exceptionWhileSettingStatusIsSwallowedAndSpanEnded() {
		Tracer flakyTracer = mock(Tracer.class);
		io.opentelemetry.api.trace.SpanBuilder builder = mock(io.opentelemetry.api.trace.SpanBuilder.class);
		Span flakySpan = mock(Span.class);
		when(flakyTracer.spanBuilder(anyStringArg())).thenReturn(builder);
		when(builder.setSpanKind(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
		when(builder.setParent(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
		when(builder.startSpan()).thenReturn(flakySpan);
		when(flakySpan.setStatus(org.mockito.ArgumentMatchers.any(StatusCode.class)))
			.thenThrow(new RuntimeException("status rejected"));

		NodeTracingLifecycleListener flakyListener = new NodeTracingLifecycleListener(flakyTracer, langfuseService);
		flakyListener.before(SQL_GENERATE_NODE, Map.of(), config, 1L);

		assertDoesNotThrow(() -> flakyListener.after(SQL_GENERATE_NODE, Map.of(SQL_GENERATE_OUTPUT, "x"), config, 2L));

		org.mockito.Mockito.verify(flakySpan).end();
	}

	/**
	 * onError 阶段 recordException 抛异常时同样必须兜住并 end()。
	 */
	@Test
	void exceptionWhileRecordingErrorIsSwallowedAndSpanEnded() {
		Tracer flakyTracer = mock(Tracer.class);
		io.opentelemetry.api.trace.SpanBuilder builder = mock(io.opentelemetry.api.trace.SpanBuilder.class);
		Span flakySpan = mock(Span.class);
		when(flakyTracer.spanBuilder(anyStringArg())).thenReturn(builder);
		when(builder.setSpanKind(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
		when(builder.setParent(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
		when(builder.startSpan()).thenReturn(flakySpan);
		when(flakySpan.recordException(org.mockito.ArgumentMatchers.any(Throwable.class)))
			.thenThrow(new RuntimeException("record rejected"));

		NodeTracingLifecycleListener flakyListener = new NodeTracingLifecycleListener(flakyTracer, langfuseService);
		flakyListener.before(SQL_GENERATE_NODE, Map.of(), config, 1L);

		assertDoesNotThrow(
				() -> flakyListener.onError(SQL_GENERATE_NODE, Map.of(), new IllegalStateException("x"), config));

		org.mockito.Mockito.verify(flakySpan).end();
	}

	@Test
	void discardThread_isSafeForUnknownAndNullThreadId() {
		assertDoesNotThrow(() -> listener.discardThread("never-seen"));
		assertDoesNotThrow(() -> listener.discardThread(null));
	}

	/**
	 * 两个节点嵌套未闭合时（理论上不该发生），栈顺序弹出并告警，但绝不能把 span 永远挂着。
	 */
	@Test
	void mismatchedNodeIdStillEndsTheSpan() {
		listener.before(SQL_GENERATE_NODE, Map.of(), config, 1L);

		// 用另一个 nodeId 结束：走 stack mismatch 分支
		listener.after(SQL_EXECUTE_NODE, Map.of(SQL_EXECUTE_NODE_OUTPUT, Map.of("step_1", "ok")), config, 2L);

		SpanData span = onlyNodeSpan();
		assertEquals(SQL_GENERATE_NODE, span.getName());
		assertTrue(span.getEndEpochNanos() > 0, "span must be ended even when the stack does not match");
	}

	/**
	 * 同一 threadId 下嵌套两层未闭合的节点，覆盖"弹栈后栈内仍有残留"的分支。
	 */
	@Test
	void nestedNodesArePoppedInStackOrder() {
		listener.before(PLAN_EXECUTOR_NODE, Map.of(), config, 1L);
		listener.before(SQL_GENERATE_NODE, Map.of(), config, 2L);

		Map<String, Object> state = new HashMap<>();
		state.put(SQL_GENERATE_OUTPUT, "SELECT 1");
		listener.after(SQL_GENERATE_NODE, state, config, 3L);

		// 内层已结束，外层仍在栈里
		assertEquals(1, nodeSpans().size());

		state.put(PLAN_NEXT_NODE, SQL_EXECUTE_NODE);
		listener.after(PLAN_EXECUTOR_NODE, state, config, 4L);

		List<SpanData> spans = nodeSpans();
		assertEquals(2, spans.size());
		assertEquals(SQL_GENERATE_NODE, spans.get(1).getName());
		assertEquals(PLAN_EXECUTOR_NODE, spans.get(0).getName());
	}

	/**
	 * token 全为 0 时不应写 token 属性——0 是"没调用 LLM"，与"调用了但用了 0 token"在面板上应无区别。
	 */
	@Test
	void zeroTokensAreNotRecordedAsAttributes() {
		Map<String, Object> state = new HashMap<>();
		listener.before(SQL_GENERATE_NODE, state, config, 1L);
		LangfuseService.accumulateTokens(THREAD_ID, 0, 0);
		state.put(SQL_GENERATE_OUTPUT, "SELECT 1");
		listener.after(SQL_GENERATE_NODE, state, config, 2L);

		assertNull(onlyNodeSpan().getAttributes().get(PROMPT_TOKENS));
	}

	/**
	 * onError 时也要把该 attempt 已消耗的 token 写进 span——失败前烧掉的 token 同样需要可见。
	 */
	@Test
	void onError_stillRecordsTokensConsumedBeforeFailure() {
		listener.before(SQL_GENERATE_NODE, Map.of(), config, 1L);
		LangfuseService.accumulateTokens(THREAD_ID, 800, 200);

		listener.onError(SQL_GENERATE_NODE, Map.of(), new IllegalStateException("mid-stream failure"), config);

		SpanData span = onlyNodeSpan();
		assertEquals(800L, span.getAttributes().get(PROMPT_TOKENS));
		assertEquals(1000L, span.getAttributes().get(TOTAL_TOKENS));
	}

	@Test
	void onErrorWithoutBefore_isIgnored() {
		assertDoesNotThrow(() -> listener.onError(HUMAN_FEEDBACK_NODE, Map.of(), new RuntimeException("x"), config));

		assertTrue(nodeSpans().isEmpty());
	}

	/**
	 * span 自身在结束阶段抛异常时，catch 分支必须兜住，且仍要 end()。
	 */
	@Test
	void exceptionWhileFinishingSpanIsSwallowed() {
		Tracer flakyTracer = mock(Tracer.class);
		io.opentelemetry.api.trace.SpanBuilder builder = mock(io.opentelemetry.api.trace.SpanBuilder.class);
		Span flakySpan = mock(Span.class);
		when(flakyTracer.spanBuilder(anyStringArg())).thenReturn(builder);
		when(builder.setSpanKind(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
		when(builder.setParent(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
		when(builder.startSpan()).thenReturn(flakySpan);
		when(flakySpan.setAttribute(org.mockito.ArgumentMatchers.<AttributeKey<String>>any(), anyStringArg()))
			.thenThrow(new RuntimeException("attribute rejected"));

		NodeTracingLifecycleListener flakyListener = new NodeTracingLifecycleListener(flakyTracer, langfuseService);

		assertDoesNotThrow(() -> {
			flakyListener.before(SQL_GENERATE_NODE, Map.of(), config, 1L);
			flakyListener.after(SQL_GENERATE_NODE, Map.of(SQL_GENERATE_OUTPUT, "x"), config, 2L);
		});
	}

	/**
	 * discardThread 遇到 span.setStatus 抛异常时也必须继续 end() 并清理，否则泄漏。
	 */
	@Test
	void discardThread_swallowsExceptionFromSpanAndStillEnds() {
		Tracer flakyTracer = mock(Tracer.class);
		io.opentelemetry.api.trace.SpanBuilder builder = mock(io.opentelemetry.api.trace.SpanBuilder.class);
		Span flakySpan = mock(Span.class);
		when(flakyTracer.spanBuilder(anyStringArg())).thenReturn(builder);
		when(builder.setSpanKind(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
		when(builder.setParent(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
		when(builder.startSpan()).thenReturn(flakySpan);
		when(flakySpan.setStatus(org.mockito.ArgumentMatchers.any(StatusCode.class), anyStringArg()))
			.thenThrow(new RuntimeException("span already closed"));

		NodeTracingLifecycleListener flakyListener = new NodeTracingLifecycleListener(flakyTracer, langfuseService);
		flakyListener.before(SQL_GENERATE_NODE, Map.of(), config, 1L);

		assertDoesNotThrow(() -> flakyListener.discardThread(THREAD_ID));

		org.mockito.Mockito.verify(flakySpan).end();
	}

	@Test
	void concurrentThreads_doNotShareSpansOrCounters() {
		String otherThread = "thread-2";
		Span otherRoot = tracer.spanBuilder("graph-stream-2").startSpan();
		when(langfuseService.getRootSpan(otherThread)).thenReturn(otherRoot);
		RunnableConfig otherConfig = RunnableConfig.builder().threadId(otherThread).build();

		Map<String, Object> stateA = new HashMap<>();
		Map<String, Object> stateB = new HashMap<>();
		listener.before(SQL_GENERATE_NODE, stateA, config, 1L);
		listener.before(SQL_GENERATE_NODE, stateB, otherConfig, 1L);

		LangfuseService.accumulateTokens(THREAD_ID, 300, 0);
		LangfuseService.accumulateTokens(otherThread, 6, 0);

		stateA.put(SQL_GENERATE_OUTPUT, "A");
		stateB.put(SQL_GENERATE_OUTPUT, "B");
		listener.after(SQL_GENERATE_NODE, stateA, config, 2L);
		listener.after(SQL_GENERATE_NODE, stateB, otherConfig, 2L);

		otherRoot.end();
		LangfuseService.discardAccumulators(otherThread);

		List<SpanData> spans = nodeSpans();
		assertEquals(300L, spans.get(0).getAttributes().get(PROMPT_TOKENS));
		assertEquals(6L, spans.get(1).getAttributes().get(PROMPT_TOKENS));
		// 各自的 attempt 都从 1 开始，没有互相影响
		assertEquals(1L, spans.get(0).getAttributes().get(ATTEMPT));
		assertEquals(1L, spans.get(1).getAttributes().get(ATTEMPT));
	}

	/**
	 * listener 内部异常绝不能冒泡：框架虽然会 catch，但吞掉后 span 会永远挂着。
	 */
	@Test
	void exceptionFromTracerIsSwallowed() {
		Tracer brokenTracer = mock(Tracer.class);
		when(brokenTracer.spanBuilder(anyStringArg())).thenThrow(new RuntimeException("tracer down"));
		NodeTracingLifecycleListener brokenListener = new NodeTracingLifecycleListener(brokenTracer, langfuseService);

		assertDoesNotThrow(() -> brokenListener.before(INTENT_RECOGNITION_NODE, Map.of(INPUT_KEY, "q"), config, 1L));
		assertDoesNotThrow(() -> brokenListener.after(INTENT_RECOGNITION_NODE, Map.of(), config, 2L));
	}

	// --- helpers ---

	private static String anyStringArg() {
		return org.mockito.ArgumentMatchers.anyString();
	}
	/** 导出的 span 里排除根 span，只留节点 span，按开始时间排序 */
	private List<SpanData> nodeSpans() {
		List<SpanData> spans = new ArrayList<>(exporter.spans);
		spans.removeIf(s -> s.getName().startsWith("graph-stream"));
		spans.sort((a, b) -> Long.compare(a.getStartEpochNanos(), b.getStartEpochNanos()));
		return spans;
	}

	private SpanData onlyNodeSpan() {
		List<SpanData> spans = nodeSpans();
		assertEquals(1, spans.size(), () -> "expected exactly one node span but got " + spans.size());
		return spans.get(0);
	}

	/** 把导出的 span 收进内存列表，避免为测试新增 opentelemetry-sdk-testing 依赖 */
	private static final class RecordingExporter implements SpanExporter {

		private final List<SpanData> spans = new CopyOnWriteArrayList<>();

		@Override
		public CompletableResultCode export(Collection<SpanData> spans) {
			this.spans.addAll(spans);
			return CompletableResultCode.ofSuccess();
		}

		@Override
		public CompletableResultCode flush() {
			return CompletableResultCode.ofSuccess();
		}

		@Override
		public CompletableResultCode shutdown() {
			return CompletableResultCode.ofSuccess();
		}

		void clear() {
			spans.clear();
		}

	}

	/** 仅为让静态导入的 assertEquals 在比较 StatusData 时有明确语义 */
	@SuppressWarnings("unused")
	private static StatusCode codeOf(StatusData status) {
		return status.getStatusCode();
	}

}
