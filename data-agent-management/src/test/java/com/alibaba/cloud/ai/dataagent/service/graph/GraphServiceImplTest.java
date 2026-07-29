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
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.service.graph.turn.ConversationTurnService;
import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.dataagent.service.memory.context.ConversationContextAssembler;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphServiceImplTest {

	@Mock
	private CompiledGraph compiledGraph;

	@Mock
	private ConversationContextAssembler contextAssembler;

	@Mock
	private ConversationTurnService turnService;

	@Mock
	private AgentDatasourceMapper agentDatasourceMapper;

	@Mock
	private LangfuseService langfuseReporter;

	@Mock
	private BaseCheckpointSaver checkpointSaver;

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
		graphService = new GraphServiceImpl(mockStateGraph, compileConfig, checkpointSaver, executor, contextAssembler,
				turnService, agentDatasourceMapper, langfuseReporter);

		when(langfuseReporter.startLLMSpan(anyString(), any())).thenReturn(mockSpan);
		when(mockSpan.isRecording()).thenReturn(true);
		when(agentDatasourceMapper.selectActiveDatasourceIdByAgentId(anyLong())).thenReturn(3);
		when(contextAssembler.build(anyString(), anyInt(), nullable(String.class), nullable(Integer.class)))
			.thenReturn("(无)");
		when(turnService.beginTurn(anyString(), anyInt(), nullable(Integer.class), anyString(), anyString(),
				anyBoolean()))
			.thenReturn("turn-1");
		when(turnService.resumeTurn(nullable(String.class), anyString(), anyBoolean())).thenReturn("turn-1");
		when(turnService.getPinnedDatasourceId("turn-1")).thenReturn(3);
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
		@SuppressWarnings("unchecked")
		var inputCaptor = (org.mockito.ArgumentCaptor<java.util.Map<String, Object>>) (org.mockito.ArgumentCaptor<?>) org.mockito.ArgumentCaptor
			.forClass(java.util.Map.class);
		verify(compiledGraph).stream(inputCaptor.capture(), any(RunnableConfig.class));
		assertEquals(3, inputCaptor.getValue().get("datasourceId"));
		verify(turnService).beginTurn("conversation-1", 1, 3, request.getThreadId(), "test query", false);
	}

	@Test
	void graphStreamProcess_legacyThreadId_startsFreshRunAndKeepsConversationIdentity() {
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
		verify(turnService).markWaitingReview(eq("turn-1"), eq(request.getThreadId()), nullable(String.class));
		verify(turnService, never()).completeTurn(any(), any(), any(), any(), any());
		verify(checkpointSaver, never()).release(any(RunnableConfig.class));
	}

	@Test
	void graphStreamProcess_completedBeforeHumanFeedback_doesNotEmitRequiredEventAndReleasesCheckpoint()
			throws Exception {
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
		verify(turnService).completeTurn(eq("turn-1"), eq(request.getThreadId()), any(), nullable(String.class),
				nullable(String.class));
		verify(turnService, never()).markWaitingReview(any(), any(), any());
		verify(checkpointSaver).release(any(RunnableConfig.class));
	}

	@Test
	void graphStreamProcess_startupFailureMarksDurableTurnFailedAndReleasesCheckpoint() throws Exception {
		when(contextAssembler.build("conversation-1", 1, "test query", 3))
			.thenThrow(new IllegalStateException("context unavailable"));
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-1")
			.query("test query")
			.build();

		assertThrows(IllegalStateException.class,
				() -> graphService.graphStreamProcess(Sinks.many().multicast().onBackpressureBuffer(), request));

		verify(turnService).failTurn(eq("turn-1"), eq(request.getThreadId()), any(IllegalStateException.class),
				nullable(String.class));
		verify(checkpointSaver).release(argThat(config -> request.getThreadId().equals(config.threadId().orElse(null))));
	}

	@Test
	void graphStreamProcess_completionPersistenceFailureMarksTurnFailedAndEmitsError() throws Exception {
		doThrow(new IllegalStateException("database unavailable")).when(turnService)
			.completeTurn(eq("turn-1"), anyString(), any(), nullable(String.class), nullable(String.class));
		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(Flux.empty());
		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
		var responsesFuture = sink.asFlux().map(ServerSentEvent::data).collectList().toFuture();
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-1")
			.query("test query")
			.build();

		graphService.graphStreamProcess(sink, request);
		List<GraphNodeResponse> responses = responsesFuture.get(Duration.ofSeconds(2).toMillis(),
				TimeUnit.MILLISECONDS);

		assertTrue(responses.stream()
			.anyMatch(response -> response.isError() && response.getText().contains("Failed to persist graph result")),
				responses.toString());
		verify(turnService).failTurn(eq("turn-1"), eq(request.getThreadId()), any(IllegalStateException.class),
				nullable(String.class));
		verify(checkpointSaver)
			.release(argThat(config -> request.getThreadId().equals(config.threadId().orElse(null))));
	}

	@Test
	void graphStreamProcess_rejectedFeedbackInterruptedAgain_emitsRequiredEventAndRetainsCheckpoint() throws Exception {
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
	}

	@Test
	void stopStreamProcessing_unknownThread_doesNothing() {
		assertDoesNotThrow(() -> graphService.stopStreamProcessing("unknown-thread"));
		verify(turnService, never()).cancelTurn(any(), any(), any());
	}

	@Test
	void stopStreamProcessing_existingThread_cleansUp() {
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-to-stop")
			.threadId("thread-to-stop")
			.query("test query")
			.build();

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().multicast().onBackpressureBuffer();
		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(Flux.never());

		graphService.graphStreamProcess(sink, request);
		String runId = request.getThreadId();

		try {
			Thread.sleep(100);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		graphService.stopStreamProcessing(runId);
		verify(turnService).cancelTurn(eq("turn-1"), eq(runId), nullable(String.class));
	}

	@Test
	void stopStreamProcessingByConversationId_cancelsActiveGraphSubscription() throws Exception {
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
		verify(turnService).cancelTurn(eq("turn-1"), eq(request.getThreadId()), nullable(String.class));
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

		assertThrows(RuntimeException.class, () -> graphService.nl2sql("test", "1"));
	}

	@Test
	void nl2sql_emptyOptional_returnsEmpty() throws GraphRunnerException {
		when(compiledGraph.invoke(anyMap(), any(RunnableConfig.class))).thenReturn(Optional.empty());

		assertThrows(Exception.class, () -> graphService.nl2sql("test", "1"));
	}

}
