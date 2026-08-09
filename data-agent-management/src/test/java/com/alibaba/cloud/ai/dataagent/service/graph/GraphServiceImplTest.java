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
package com.alibaba.cloud.ai.dataagent.service.graph;

import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.enums.GraphEventType;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.MultiTurnContextManager;
import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.dataagent.service.langfuse.NodeTracingLifecycleListener;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphServiceImplTest {

	@Mock
	private CompiledGraph compiledGraph;

	@Mock
	private MultiTurnContextManager multiTurnContextManager;

	@Mock
	private LangfuseService langfuseReporter;

	@Mock
	private BaseCheckpointSaver checkpointSaver;

	@Mock
	private NodeTracingLifecycleListener nodeTracingLifecycleListener;

	@Mock
	private Span mockSpan;

	private GraphServiceImpl graphService;

	private ExecutorService executor;

	@BeforeEach
	void setUp() throws Exception {
		executor = Executors.newSingleThreadExecutor();

		StateGraph mockStateGraph = mock(StateGraph.class);
		when(mockStateGraph.compile(any())).thenReturn(compiledGraph);

		CompileConfig compileConfig = CompileConfig.builder().build();
		graphService = new GraphServiceImpl(mockStateGraph, compileConfig, checkpointSaver, executor,
				multiTurnContextManager, langfuseReporter, nodeTracingLifecycleListener);
	}

	private void stubStreamDependencies() {
		when(langfuseReporter.startLLMSpan(anyString(), any())).thenReturn(mockSpan);
		when(multiTurnContextManager.buildContext(anyString())).thenReturn("(无)");
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
	}

	@Test
	void nl2sql_validQuery_returnsResult() throws GraphRunnerException {
		OverAllState mockState = mock(OverAllState.class);
		when(mockState.value(eq("SQL_GENERATE_OUTPUT"), eq(""))).thenReturn("SELECT * FROM users");
		when(compiledGraph.invoke(anyMap(), any(RunnableConfig.class))).thenReturn(Optional.of(mockState));

		String result = graphService.nl2sql("show all users", "1");

		assertEquals("SELECT * FROM users", result);
		var configCaptor = org.mockito.ArgumentCaptor.forClass(RunnableConfig.class);
		verify(compiledGraph).invoke(anyMap(), configCaptor.capture());
		assertTrue(configCaptor.getValue().threadId().isPresent());
		assertNotEquals(BaseCheckpointSaver.THREAD_ID_DEFAULT, configCaptor.getValue().threadId().orElseThrow());
		try {
			verify(checkpointSaver).release(configCaptor.getValue());
		}
		catch (Exception e) {
			fail(e);
		}
	}

	@Test
	void nl2sql_emptyResult_returnsEmptyString() throws GraphRunnerException {
		OverAllState mockState = mock(OverAllState.class);
		when(mockState.value(eq("SQL_GENERATE_OUTPUT"), eq(""))).thenReturn("");
		when(compiledGraph.invoke(anyMap(), any(RunnableConfig.class))).thenReturn(Optional.of(mockState));

		String result = graphService.nl2sql("invalid query", "1");

		assertEquals("", result);
	}

	@Test
	void graphStreamProcess_newProcess_setsThreadIdIfMissing() {
		stubStreamDependencies();
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-1")
			.query("test query")
			.build();

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().multicast().onBackpressureBuffer();

		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(Flux.empty());

		graphService.graphStreamProcess(sink, request);

		assertNotNull(request.getThreadId());
		assertFalse(request.getThreadId().isEmpty());
		assertNotEquals(request.getConversationId(), request.getThreadId());
	}

	@Test
	void graphStreamProcess_legacyThreadId_startsFreshRunAndKeepsConversationIdentity() {
		stubStreamDependencies();
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.threadId("existing-thread")
			.query("test query")
			.build();

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().multicast().onBackpressureBuffer();

		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(Flux.empty());

		graphService.graphStreamProcess(sink, request);

		assertEquals("existing-thread", request.getConversationId());
		assertNotEquals("existing-thread", request.getThreadId());
	}

	@Test
	void graphStreamProcess_humanFeedback_reusesInterruptedRunId() throws Exception {
		stubStreamDependencies();
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-1")
			.threadId("interrupted-run")
			.query("test query")
			.humanFeedback(true)
			.humanFeedbackContent("approve")
			.build();
		RunnableConfig updatedConfig = RunnableConfig.builder().threadId("interrupted-run").build();
		when(compiledGraph.updateState(any(RunnableConfig.class), anyMap())).thenReturn(updatedConfig);
		when(compiledGraph.stream(isNull(), any(RunnableConfig.class))).thenReturn(Flux.empty());

		graphService.graphStreamProcess(Sinks.many().multicast().onBackpressureBuffer(), request);

		var configCaptor = org.mockito.ArgumentCaptor.forClass(RunnableConfig.class);
		verify(compiledGraph).updateState(configCaptor.capture(), anyMap());
		assertEquals("interrupted-run", configCaptor.getValue().threadId().orElseThrow());
		assertEquals("interrupted-run", request.getThreadId());
	}

	@Test
	void graphStreamProcess_interruptedForHumanFeedback_emitsRequiredEventAndRetainsCheckpoint() throws Exception {
		stubStreamDependencies();
		Checkpoint checkpoint = Checkpoint.builder()
			.nodeId("PLANNER_NODE")
			.nextNodeId("HUMAN_FEEDBACK_NODE")
			.state(java.util.Map.of())
			.build();
		when(checkpointSaver.get(any(RunnableConfig.class))).thenReturn(Optional.of(checkpoint));
		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(Flux.empty());

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
		var responsesFuture = sink.asFlux().map(ServerSentEvent::data).collectList().toFuture();
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-1")
			.query("review this plan")
			.humanFeedback(true)
			.build();

		graphService.graphStreamProcess(sink, request);
		List<GraphNodeResponse> responses = responsesFuture.get(Duration.ofSeconds(2).toMillis(),
				TimeUnit.MILLISECONDS);

		assertTrue(
				responses.stream()
					.anyMatch(response -> "HUMAN_FEEDBACK_REQUIRED".equals(response.getEventType().name())),
				responses.toString());
		verify(checkpointSaver, never()).release(any(RunnableConfig.class));
	}

	@Test
	void graphStreamProcess_completedBeforeHumanFeedback_doesNotEmitRequiredEventAndReleasesCheckpoint()
			throws Exception {
		stubStreamDependencies();
		Checkpoint checkpoint = Checkpoint.builder()
			.nodeId("SCHEMA_RECALL_NODE")
			.nextNodeId("__END__")
			.state(java.util.Map.of())
			.build();
		when(checkpointSaver.get(any(RunnableConfig.class))).thenReturn(Optional.of(checkpoint));
		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(Flux.empty());

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
		var responsesFuture = sink.asFlux().map(ServerSentEvent::data).collectList().toFuture();
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-1")
			.query("query without available schema")
			.humanFeedback(true)
			.build();

		graphService.graphStreamProcess(sink, request);
		List<GraphNodeResponse> responses = responsesFuture.get(Duration.ofSeconds(2).toMillis(),
				TimeUnit.MILLISECONDS);

		assertFalse(
				responses.stream()
					.anyMatch(response -> "HUMAN_FEEDBACK_REQUIRED".equals(response.getEventType().name())),
				responses.toString());
		verify(checkpointSaver).release(any(RunnableConfig.class));
	}

	@Test
	void graphStreamProcess_rejectedFeedbackInterruptedAgain_emitsRequiredEventAndRetainsCheckpoint() throws Exception {
		stubStreamDependencies();
		Checkpoint checkpoint = Checkpoint.builder()
			.nodeId("PLANNER_NODE")
			.nextNodeId("HUMAN_FEEDBACK_NODE")
			.state(java.util.Map.of())
			.build();
		when(checkpointSaver.get(any(RunnableConfig.class))).thenReturn(Optional.of(checkpoint));
		when(compiledGraph.updateState(any(RunnableConfig.class), anyMap()))
			.thenReturn(RunnableConfig.builder().threadId("interrupted-run").build());
		when(compiledGraph.stream(isNull(), any(RunnableConfig.class))).thenReturn(Flux.empty());

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
		var responsesFuture = sink.asFlux().map(ServerSentEvent::data).collectList().toFuture();
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-1")
			.threadId("interrupted-run")
			.query("review this plan")
			.humanFeedback(true)
			.humanFeedbackContent("please revise")
			.rejectedPlan(true)
			.build();

		graphService.graphStreamProcess(sink, request);
		List<GraphNodeResponse> responses = responsesFuture.get(Duration.ofSeconds(2).toMillis(),
				TimeUnit.MILLISECONDS);

		assertTrue(
				responses.stream()
					.anyMatch(response -> GraphEventType.HUMAN_FEEDBACK_REQUIRED.equals(response.getEventType())),
				responses.toString());
		verify(checkpointSaver, never()).release(any(RunnableConfig.class));
	}

	@Test
	void graphStreamProcess_emitsStepIdentityAndTypedFinalAnswer() throws Exception {
		stubStreamDependencies();
		OverAllState regularState = new OverAllState();
		regularState.registerKeyAndStrategy("final_answer", new ReplaceStrategy());
		OverAllState finalState = new OverAllState();
		finalState.registerKeyAndStrategy("final_answer", new ReplaceStrategy());
		finalState.updateState(java.util.Map.of("final_answer", "请补充时间范围"));

		StreamingOutput<?> first = streamingOutput("IntentRecognitionNode", "first", regularState);
		StreamingOutput<?> second = streamingOutput("IntentRecognitionNode", "second", regularState);
		StreamingOutput<?> other = streamingOutput("QueryEnhanceNode", "other", regularState);
		StreamingOutput<?> retry = streamingOutput("IntentRecognitionNode", "retry", finalState);
		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class)))
			.thenReturn(Flux.just(first, second, other, retry));

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
		var responsesFuture = sink.asFlux().map(ServerSentEvent::data).collectList().toFuture();
		GraphRequest request = GraphRequest.builder().agentId("1").threadId("run-1").query("test query").build();

		graphService.graphStreamProcess(sink, request);
		List<GraphNodeResponse> responses = responsesFuture.get(Duration.ofSeconds(2).toMillis(),
				java.util.concurrent.TimeUnit.MILLISECONDS);

		List<GraphNodeResponse> nodeEvents = responses.stream()
			.filter(response -> response.getEventType() == GraphEventType.NODE_OUTPUT && !response.isComplete())
			.toList();
		assertEquals(nodeEvents.get(0).getStepId(), nodeEvents.get(1).getStepId());
		assertNotEquals(nodeEvents.get(1).getStepId(), nodeEvents.get(2).getStepId());
		assertNotEquals(nodeEvents.get(0).getStepId(), nodeEvents.get(3).getStepId());
		assertEquals(1, nodeEvents.get(0).getAttempt());
		assertEquals(2, nodeEvents.get(3).getAttempt());
		assertTrue(responses.stream()
			.anyMatch(response -> response.getEventType() == GraphEventType.FINAL_ANSWER
					&& "请补充时间范围".equals(response.getText())),
				responses.toString());
	}

	@SuppressWarnings("unchecked")
	private StreamingOutput<?> streamingOutput(String node, String chunk, OverAllState state) {
		StreamingOutput<Object> output = mock(StreamingOutput.class);
		when(output.node()).thenReturn(node);
		when(output.chunk()).thenReturn(chunk);
		when(output.state()).thenReturn(state);
		return output;
	}

	@Test
	void stopStreamProcessing_nullThreadId_doesNothing() {
		assertDoesNotThrow(() -> graphService.stopStreamProcessing(null));
		assertDoesNotThrow(() -> graphService.stopStreamProcessing(""));
		verifyNoInteractions(multiTurnContextManager, checkpointSaver, langfuseReporter);
	}

	@Test
	void stopStreamProcessing_unknownThread_doesNothing() {
		assertDoesNotThrow(() -> graphService.stopStreamProcessing("unknown-thread"));
		verify(multiTurnContextManager).discardPending("unknown-thread");
	}

	@Test
	void stopStreamProcessing_existingThread_cleansUp() throws Exception {
		stubStreamDependencies();
		when(mockSpan.isRecording()).thenReturn(true);
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-to-stop")
			.threadId("thread-to-stop")
			.query("test query")
			.build();

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().multicast().onBackpressureBuffer();
		CountDownLatch subscribed = new CountDownLatch(1);
		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(
				Flux.<com.alibaba.cloud.ai.graph.NodeOutput>never().doOnSubscribe(ignored -> subscribed.countDown()));

		graphService.graphStreamProcess(sink, request);
		String runId = request.getThreadId();
		assertTrue(subscribed.await(2, TimeUnit.SECONDS));

		graphService.stopStreamProcessing(runId);
		verify(multiTurnContextManager).discardPending("conversation-to-stop");
		verify(langfuseReporter).endSpanSuccess(eq(mockSpan), eq(runId), anyString());
	}

	/**
	 * 客户端断开是唯一绕过节点 after/onError 的路径。若不清理，该 threadId 下仍挂着的节点 span 会在 Langfuse 上永不结束，同时
	 * listener 内部的 map 会持续泄漏。
	 */
	@Test
	void stopStreamProcessing_discardsDanglingNodeSpans() {
		stubStreamDependencies();
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-with-nodes")
			.threadId("thread-with-nodes")
			.query("test query")
			.build();

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().multicast().onBackpressureBuffer();
		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(Flux.never());

		graphService.graphStreamProcess(sink, request);
		String runId = request.getThreadId();

		graphService.stopStreamProcessing(runId);

		verify(nodeTracingLifecycleListener).discardThread(runId);
	}

	@Test
	void stopStreamProcessing_discardsNodeSpansEvenForUnknownThread() {
		graphService.stopStreamProcessing("never-started-thread");

		// 即使没有 StreamContext，也要清理 listener 侧可能存在的残留
		verify(nodeTracingLifecycleListener).discardThread("never-started-thread");
	}

	/**
	 * 正常终止路径必须清理 listener 侧的 attempt 计数器，否则每个新 threadId 都会留下残留、无界增长。 graphStreamProcess
	 * 会用新 UUID 覆盖入参中的 threadId，因此使用 request 的实际 threadId 做断言。
	 */
	@Test
	void handleStreamComplete_finishesThreadToClearAttemptCounters() {
		stubStreamDependencies();
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-complete")
			.query("test query")
			.build();

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().multicast().onBackpressureBuffer();
		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(Flux.empty());

		graphService.graphStreamProcess(sink, request);
		// graphStreamProcess 会将实际分配的 threadId 写回 request
		String actualThreadId = request.getThreadId();
		assertNotNull(actualThreadId, "graphStreamProcess must assign a threadId");

		verify(nodeTracingLifecycleListener, timeout(2000)).finishThread(actualThreadId);
	}

	/**
	 * 流式错误路径同样必须清理 listener 侧残留。
	 */
	@Test
	void handleStreamError_finishesThreadToClearAttemptCounters() {
		stubStreamDependencies();
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-error")
			.query("test query")
			.build();

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().multicast().onBackpressureBuffer();
		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class)))
			.thenReturn(Flux.error(new RuntimeException("boom")));

		graphService.graphStreamProcess(sink, request);
		String actualThreadId = request.getThreadId();
		assertNotNull(actualThreadId, "graphStreamProcess must assign a threadId");

		verify(nodeTracingLifecycleListener, timeout(2000)).finishThread(actualThreadId);
	}

	/**
	 * 等待人工反馈时不能清理 attempt 计数器：反馈恢复后 attempt 需接着上一轮递增。
	 */
	@Test
	void handleStreamComplete_awaitingHumanFeedback_doesNotFinishThread() throws Exception {
		stubStreamDependencies();
		Checkpoint checkpoint = Checkpoint.builder()
			.nodeId("PLANNER_NODE")
			.nextNodeId("HUMAN_FEEDBACK_NODE")
			.state(java.util.Map.of())
			.build();
		when(checkpointSaver.get(any(RunnableConfig.class))).thenReturn(Optional.of(checkpoint));
		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(Flux.empty());

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
		var responsesFuture = sink.asFlux().map(ServerSentEvent::data).collectList().toFuture();
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-feedback")
			.query("review this plan")
			.humanFeedback(true)
			.build();

		graphService.graphStreamProcess(sink, request);
		responsesFuture.get(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS);

		verify(nodeTracingLifecycleListener, never()).finishThread(anyString());
	}

	@Test
	void stopStreamProcessingByConversationId_cancelsActiveGraphSubscription() throws Exception {
		stubStreamDependencies();
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-to-cancel")
			.query("test query")
			.build();
		CountDownLatch subscribed = new CountDownLatch(1);
		CountDownLatch cancelled = new CountDownLatch(1);
		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class)))
			.thenReturn(Flux.<com.alibaba.cloud.ai.graph.NodeOutput>never()
				.doOnSubscribe(ignored -> subscribed.countDown())
				.doOnCancel(cancelled::countDown));

		graphService.graphStreamProcess(Sinks.many().multicast().onBackpressureBuffer(), request);

		assertTrue(subscribed.await(2, TimeUnit.SECONDS));
		graphService.stopStreamProcessingByConversationId("conversation-to-cancel");

		assertTrue(cancelled.await(2, TimeUnit.SECONDS));
		verify(multiTurnContextManager).discardPending("conversation-to-cancel");
		var configCaptor = org.mockito.ArgumentCaptor.forClass(RunnableConfig.class);
		try {
			verify(checkpointSaver).release(configCaptor.capture());
		}
		catch (Exception e) {
			fail(e);
		}
		assertEquals(request.getThreadId(), configCaptor.getValue().threadId().orElseThrow());
	}

	@Test
	void nl2sql_graphRunnerException_throwsException() {
		when(compiledGraph.invoke(anyMap(), any(RunnableConfig.class)))
			.thenThrow(new RuntimeException("Graph execution failed"));

		assertThrowsExactly(RuntimeException.class, () -> graphService.nl2sql("test", "1"));
	}

	@Test
	void nl2sql_emptyOptional_returnsEmpty() throws GraphRunnerException {
		when(compiledGraph.invoke(anyMap(), any(RunnableConfig.class))).thenReturn(Optional.empty());

		assertThrowsExactly(NoSuchElementException.class, () -> graphService.nl2sql("test", "1"));
	}

}
