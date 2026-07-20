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
import com.alibaba.cloud.ai.dataagent.enums.ReasoningEffort;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.ClarificationContextManager;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.ClarificationContextManager.ClarificationStateSnapshot;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.MultiTurnContextManager;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.StreamContext;
import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import com.alibaba.cloud.ai.dataagent.workflow.node.ClarificationNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.PlannerNode;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.AWAITING_CLARIFICATION;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.CLARIFICATION_ANSWER;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.CLARIFICATION_COUNT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.HUMAN_FEEDBACK_DATA;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.HUMAN_FEEDBACK_NODE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.HUMAN_REVIEW_ENABLED;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.IS_ONLY_NL2SQL;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.MULTI_TURN_CONTEXT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.REASONING_EFFORT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.ORIGINAL_USER_QUERY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.REFINED_USER_QUERY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_GENERATE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_COMPLETE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_ERROR;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TRACE_THREAD_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.THINKING_ENABLED;

@Slf4j
@Service
public class GraphServiceImpl implements GraphService {

	private static final String RESUME_MODE_CLARIFICATION = "clarification";

	private static final long RECONNECT_GRACE_PERIOD_SECONDS = 45;

	private final CompiledGraph compiledGraph;

	private final ExecutorService executor;

	private final ConcurrentHashMap<String, StreamContext> streamContextMap = new ConcurrentHashMap<>();

	private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();

	private final MultiTurnContextManager multiTurnContextManager;

	private final ClarificationContextManager clarificationContextManager;

	private final LangfuseService langfuseReporter;

	public GraphServiceImpl(StateGraph stateGraph, ExecutorService executorService,
			MultiTurnContextManager multiTurnContextManager, ClarificationContextManager clarificationContextManager,
			LangfuseService langfuseReporter) throws GraphStateException {
		this.compiledGraph = stateGraph.compile(CompileConfig.builder().interruptBefore(HUMAN_FEEDBACK_NODE).build());
		this.executor = executorService;
		this.multiTurnContextManager = multiTurnContextManager;
		this.clarificationContextManager = clarificationContextManager;
		this.langfuseReporter = langfuseReporter;
	}

	@Override
	public String nl2sql(String naturalQuery, String agentId) throws GraphRunnerException {
		OverAllState state = compiledGraph
			.invoke(Map.of(IS_ONLY_NL2SQL, true, INPUT_KEY, naturalQuery, AGENT_ID, agentId),
					RunnableConfig.builder().build())
			.orElseThrow();
		return state.value(SQL_GENERATE_OUTPUT, "");
	}

	@Override
	public void graphStreamProcess(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest graphRequest) {
		if (!StringUtils.hasText(graphRequest.getThreadId())) {
			graphRequest.setThreadId(UUID.randomUUID().toString());
		}
		String threadId = graphRequest.getThreadId();
		StreamContext existingContext = streamContextMap.get(threadId);
		if (graphRequest.isReconnect()) {
			if (existingContext == null || existingContext.isCleaned()) {
				emitReconnectFailure(sink, graphRequest);
				return;
			}
			attachReconnectSink(existingContext, sink, graphRequest);
			return;
		}

		StreamContext context = streamContextMap.computeIfAbsent(threadId, k -> new StreamContext());
		context.attachSink(sink);
		context.setAgentId(graphRequest.getAgentId());
		context.setThreadId(threadId);
		context.setConversationId(graphRequest.getConversationId());

		if (StringUtils.hasText(graphRequest.getHumanFeedbackContent())) {
			handleHumanFeedback(graphRequest);
			return;
		}

		if (RESUME_MODE_CLARIFICATION.equalsIgnoreCase(graphRequest.getResumeMode())
				&& StringUtils.hasText(graphRequest.getClarificationAnswer())) {
			handleClarificationResume(graphRequest);
			return;
		}

		clarificationContextManager.clear(threadId);
		handleNewProcess(graphRequest);
	}

	@Override
	public void disconnectStream(String threadId) {
		if (!StringUtils.hasText(threadId)) {
			return;
		}
		StreamContext context = streamContextMap.get(threadId);
		if (context == null || context.isCleaned()) {
			return;
		}
		context.markDisconnected();
		context.setCleanupFuture(reconnectScheduler.schedule(() -> {
			StreamContext currentContext = streamContextMap.get(threadId);
			if (currentContext == context && currentContext.isAwaitingReconnect()) {
				log.info("Reconnect grace period expired, stopping stream processing for threadId: {}", threadId);
				stopStreamProcessing(threadId);
			}
		}, RECONNECT_GRACE_PERIOD_SECONDS, TimeUnit.SECONDS));
	}

	@Override
	public void stopStreamProcessing(String threadId) {
		if (!StringUtils.hasText(threadId)) {
			return;
		}
		log.info("Stopping stream processing for threadId: {}", threadId);
		multiTurnContextManager.discardPending(threadId);
		StreamContext context = streamContextMap.remove(threadId);
		if (context != null) {
			if (context.getSpan() != null && context.getSpan().isRecording()) {
				langfuseReporter.endSpanSuccess(context.getSpan(), threadId, context.getCollectedOutput());
			}
			context.cleanup();
			log.info("Cleaned up stream context for threadId: {}", threadId);
		}
	}

	@Override
	public void stopStreamProcessingByConversationId(String conversationId) {
		if (!StringUtils.hasText(conversationId)) {
			return;
		}
		streamContextMap.forEach((threadId, context) -> {
			if (conversationId.equals(context.getConversationId())) {
				stopStreamProcessing(threadId);
			}
		});
	}

	private void emitReconnectFailure(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest graphRequest) {
		GraphNodeResponse response = GraphNodeResponse.error(graphRequest.getAgentId(), graphRequest.getThreadId(),
				"未找到可恢复的运行上下文，请重新发起任务。");
		sink.tryEmitNext(ServerSentEvent.<GraphNodeResponse>builder(response).event(STREAM_EVENT_ERROR).build());
		sink.tryEmitComplete();
	}

	private void attachReconnectSink(StreamContext context, Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink,
			GraphRequest request) {
		context.attachSink(sink);
		context.setAgentId(request.getAgentId());
		context.setThreadId(request.getThreadId());
		context.setConversationId(request.getConversationId());
		long lastSequence = Optional.ofNullable(request.getLastSequence()).orElse(0L);
		for (GraphNodeResponse response : context.getReplayResponsesAfter(lastSequence)) {
			sink.tryEmitNext(ServerSentEvent.builder(response).build());
		}
	}

	private void handleNewProcess(GraphRequest graphRequest) {
		handleNewProcess(graphRequest, graphRequest.getQuery(), graphRequest.getQuery(), 0, null);
	}

	private void handleClarificationResume(GraphRequest graphRequest) {
		String threadId = graphRequest.getThreadId();
		String clarificationAnswer = graphRequest.getClarificationAnswer();
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(graphRequest.getAgentId())
				|| !StringUtils.hasText(clarificationAnswer)) {
			throw new IllegalArgumentException("Invalid clarification arguments");
		}

		Optional<ClarificationStateSnapshot> snapshotOptional = clarificationContextManager.submitAnswer(threadId,
				clarificationAnswer);
		if (snapshotOptional.isEmpty()) {
			throw new IllegalStateException("当前没有待补充的澄清问题，请重新描述完整问题");
		}

		ClarificationStateSnapshot snapshot = snapshotOptional.get();
		String refinedQuery = clarificationContextManager.buildRefinedQuery(threadId);
		if (!StringUtils.hasText(refinedQuery)) {
			throw new IllegalStateException("澄清后的查询为空，请重新描述完整问题");
		}

		GraphRequest resumedRequest = GraphRequest.builder()
			.agentId(graphRequest.getAgentId())
			.threadId(threadId)
			.query(refinedQuery)
			.humanFeedback(graphRequest.isHumanFeedback())
			.humanFeedbackContent(graphRequest.getHumanFeedbackContent())
			.clarificationAnswer(clarificationAnswer)
			.resumeMode(graphRequest.getResumeMode())
			.rejectedPlan(graphRequest.isRejectedPlan())
			.nl2sqlOnly(graphRequest.isNl2sqlOnly())
			.thinkingEnabled(graphRequest.getThinkingEnabled())
			.reasoningEffort(graphRequest.getReasoningEffort())
			.build();

		handleNewProcess(resumedRequest, snapshot.originalQuery(), refinedQuery, snapshot.clarificationCount(),
				clarificationAnswer);
	}

	private void handleNewProcess(GraphRequest graphRequest, String originalUserQuery, String inputQuery,
			int clarificationCount, String clarificationAnswer) {
		String agentId = graphRequest.getAgentId();
		String threadId = graphRequest.getThreadId();
		boolean nl2sqlOnly = graphRequest.isNl2sqlOnly();
		boolean humanReviewEnabled = graphRequest.isHumanFeedback() & !(nl2sqlOnly);
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(agentId) || !StringUtils.hasText(inputQuery)) {
			throw new IllegalArgumentException("Invalid arguments");
		}

		StreamContext context = streamContextMap.get(threadId);
		if (context == null || context.getSink() == null) {
			throw new IllegalStateException("StreamContext not found for threadId: " + threadId);
		}
		if (context.isCleaned()) {
			log.warn("StreamContext already cleaned for threadId: {}, skipping stream start", threadId);
			return;
		}

		Span span = langfuseReporter.startLLMSpan("graph-stream", graphRequest);
		context.setSpan(span);

		String multiTurnContext = multiTurnContextManager.buildContext(threadId);
		multiTurnContextManager.beginTurn(threadId, inputQuery);
		Flux<NodeOutput> nodeOutputFlux = compiledGraph.stream(
				buildInitialState(agentId, threadId, inputQuery, originalUserQuery, multiTurnContext, nl2sqlOnly,
						humanReviewEnabled, clarificationCount, clarificationAnswer, graphRequest.getThinkingEnabled(),
						graphRequest.getReasoningEffort()),
				RunnableConfig.builder().threadId(threadId).build());
		subscribeToFlux(context, nodeOutputFlux, graphRequest, agentId, threadId);
	}

	private Map<String, Object> buildInitialState(String agentId, String threadId, String inputQuery,
			String originalUserQuery, String multiTurnContext, boolean nl2sqlOnly, boolean humanReviewEnabled,
			int clarificationCount, String clarificationAnswer, Boolean thinkingEnabled, String reasoningEffort) {
		Map<String, Object> state = new HashMap<>();
		state.put(IS_ONLY_NL2SQL, nl2sqlOnly);
		if (thinkingEnabled != null) {
			state.put(THINKING_ENABLED, thinkingEnabled);
			state.put(REASONING_EFFORT, normalizeReasoningEffort(reasoningEffort));
		}
		state.put(INPUT_KEY, inputQuery);
		state.put(AGENT_ID, agentId);
		state.put(HUMAN_REVIEW_ENABLED, humanReviewEnabled);
		state.put(MULTI_TURN_CONTEXT, multiTurnContext);
		state.put(TRACE_THREAD_ID, threadId);
		state.put(ORIGINAL_USER_QUERY, StringUtils.hasText(originalUserQuery) ? originalUserQuery : inputQuery);
		state.put(REFINED_USER_QUERY, inputQuery);
		state.put(CLARIFICATION_COUNT, clarificationCount);
		state.put(AWAITING_CLARIFICATION, false);
		if (StringUtils.hasText(clarificationAnswer)) {
			state.put(CLARIFICATION_ANSWER, clarificationAnswer);
		}
		return state;
	}

	private String normalizeReasoningEffort(String reasoningEffort) {
		return StringUtils.hasText(reasoningEffort) ? ReasoningEffort.fromCode(reasoningEffort).getCode()
				: ReasoningEffort.HIGH.getCode();
	}

	private void handleHumanFeedback(GraphRequest graphRequest) {
		String agentId = graphRequest.getAgentId();
		String threadId = graphRequest.getThreadId();
		String feedbackContent = graphRequest.getHumanFeedbackContent();
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(agentId) || !StringUtils.hasText(feedbackContent)) {
			throw new IllegalArgumentException("Invalid arguments");
		}
		StreamContext context = streamContextMap.get(threadId);
		if (context == null || context.getSink() == null) {
			throw new IllegalStateException("StreamContext not found for threadId: " + threadId);
		}
		if (context.isCleaned()) {
			log.warn("StreamContext already cleaned for threadId: {}, skipping stream start", threadId);
			return;
		}

		Span span = langfuseReporter.startLLMSpan("graph-feedback", graphRequest);
		context.setSpan(span);

		Map<String, Object> feedbackData = Map.of("feedback", !graphRequest.isRejectedPlan(), "feedback_content",
				feedbackContent);
		if (graphRequest.isRejectedPlan()) {
			multiTurnContextManager.restartLastTurn(threadId);
		}
		Map<String, Object> stateUpdate = new HashMap<>();
		stateUpdate.put(HUMAN_FEEDBACK_DATA, feedbackData);
		stateUpdate.put(MULTI_TURN_CONTEXT, multiTurnContextManager.buildContext(threadId));

		RunnableConfig baseConfig = RunnableConfig.builder().threadId(threadId).build();
		RunnableConfig updatedConfig;
		try {
			updatedConfig = compiledGraph.updateState(baseConfig, stateUpdate);
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to update graph state for human feedback", e);
		}
		RunnableConfig resumeConfig = RunnableConfig.builder(updatedConfig)
			.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, feedbackData)
			.build();

		Flux<NodeOutput> nodeOutputFlux = compiledGraph.stream(null, resumeConfig);
		subscribeToFlux(context, nodeOutputFlux, graphRequest, agentId, threadId);
	}

	private void subscribeToFlux(StreamContext context, Flux<NodeOutput> nodeOutputFlux, GraphRequest graphRequest,
			String agentId, String threadId) {
		CompletableFuture.runAsync(() -> {
			if (context.isCleaned()) {
				log.debug("StreamContext cleaned before subscription for threadId: {}", threadId);
				return;
			}
			Disposable disposable = nodeOutputFlux.subscribe(output -> handleNodeOutput(graphRequest, output),
					error -> handleStreamError(agentId, threadId, error),
					() -> handleStreamComplete(agentId, threadId));
			synchronized (context) {
				if (context.isCleaned()) {
					if (disposable != null && !disposable.isDisposed()) {
						disposable.dispose();
					}
				}
				else {
					context.setDisposable(disposable);
				}
			}
		}, executor);
	}

	private void handleStreamError(String agentId, String threadId, Throwable error) {
		log.error("Error in stream processing for threadId: {}: ", threadId, error);
		StreamContext context = streamContextMap.remove(threadId);
		if (context != null && !context.isCleaned()) {
			long now = System.currentTimeMillis();
			emitFinalNodeTiming(context, agentId, threadId, now);
			if (context.getSpan() != null) {
				langfuseReporter.endSpanError(context.getSpan(), threadId,
						error instanceof Exception ? (Exception) error : new RuntimeException(error));
			}
			if (context.getSink() != null && context.getSink().currentSubscriberCount() > 0) {
				GraphNodeResponse errorResponse = GraphNodeResponse.error(agentId, threadId,
						"Error in stream processing: " + error.getMessage());
				errorResponse.setWorkflowStartedAt(context.getWorkflowStartedAt());
				errorResponse.setTotalElapsedMs(now - context.getWorkflowStartedAt());
				context.getSink()
					.tryEmitNext(ServerSentEvent
						.builder(errorResponse)
						.event(STREAM_EVENT_ERROR)
						.build());
				context.getSink().tryEmitComplete();
			}
			context.cleanup();
		}
	}

	private void handleStreamComplete(String agentId, String threadId) {
		log.info("Stream processing completed successfully for threadId: {}", threadId);
		multiTurnContextManager.finishTurn(threadId);
		if (!clarificationContextManager.isAwaitingClarification(threadId)) {
			clarificationContextManager.clear(threadId);
		}

		StreamContext context = streamContextMap.remove(threadId);
		if (context != null && !context.isCleaned()) {
			long now = System.currentTimeMillis();
			emitFinalNodeTiming(context, agentId, threadId, now);
			if (context.getSpan() != null) {
				langfuseReporter.endSpanSuccess(context.getSpan(), threadId, context.getCollectedOutput());
			}
			if (context.getSink() != null && context.getSink().currentSubscriberCount() > 0) {
				GraphNodeResponse completeResponse = GraphNodeResponse.complete(agentId, threadId);
				completeResponse.setNodeName(context.getActiveNodeName());
				completeResponse.setWorkflowStartedAt(context.getWorkflowStartedAt());
				completeResponse.setNodeStartedAt(context.getActiveNodeStartedAt());
				completeResponse.setNodeElapsedMs(elapsedSince(context.getActiveNodeStartedAt(), now));
				completeResponse.setTotalElapsedMs(now - context.getWorkflowStartedAt());
				context.getSink()
					.tryEmitNext(ServerSentEvent.builder(completeResponse)
						.event(STREAM_EVENT_COMPLETE)
						.build());
				context.getSink().tryEmitComplete();
			}
			context.cleanup();
		}
	}

	private void handleNodeOutput(GraphRequest request, NodeOutput output) {
		log.debug("Received output: {}", output.getClass().getSimpleName());
		if (output instanceof StreamingOutput streamingOutput) {
			handleStreamNodeOutput(request, streamingOutput);
		}
	}

	private void handleStreamNodeOutput(GraphRequest request, StreamingOutput output) {
		String threadId = request.getThreadId();
		StreamContext context = streamContextMap.get(threadId);
		if (context == null) {
			log.debug("Stream processing already stopped for threadId: {}, skipping output", threadId);
			return;
		}
		String node = output.node();
		String chunk = output.chunk();
		log.debug("Received Stream output: {}", chunk);

		if (chunk == null || chunk.isEmpty()) {
			return;
		}

		TextType originType = context.getTextType();
		TextType textType;
		boolean isTypeSign = false;
		if (originType == null) {
			textType = TextType.getTypeByStratSign(chunk);
			if (textType != TextType.TEXT) {
				isTypeSign = true;
			}
			context.setTextType(textType);
		}
		else {
			textType = TextType.getType(originType, chunk);
			if (textType != originType) {
				isTypeSign = true;
			}
			context.setTextType(textType);
		}
		if (!isTypeSign) {
			long now = System.currentTimeMillis();
			beginNodeTiming(context, request.getAgentId(), threadId, node, now);
			context.appendOutput(chunk);
			if (PlannerNode.class.getSimpleName().equals(node)) {
				multiTurnContextManager.appendPlannerChunk(threadId, chunk);
			}
			boolean isClarificationNode = ClarificationNode.class.getSimpleName().equals(node);
			GraphNodeResponse response = GraphNodeResponse.builder()
				.agentId(request.getAgentId())
				.threadId(threadId)
				.nodeName(node)
				.text(chunk)
				.sequence(context.nextSequence())
				.textType(textType)
				.interactionType(isClarificationNode ? RESUME_MODE_CLARIFICATION : "normal")
				.awaitingInput(isClarificationNode && clarificationContextManager.isAwaitingClarification(threadId))
				.workflowStartedAt(context.getWorkflowStartedAt())
				.nodeStartedAt(context.getActiveNodeStartedAt())
				.nodeElapsedMs(elapsedSince(context.getActiveNodeStartedAt(), now))
				.totalElapsedMs(now - context.getWorkflowStartedAt())
				.build();
			emitDataResponse(context, response);
		}
	}

	private void beginNodeTiming(StreamContext context, String agentId, String threadId, String nodeName, long now) {
		String activeNodeName = context.getActiveNodeName();
		if (nodeName.equals(activeNodeName)) {
			return;
		}
		if (activeNodeName != null) {
			emitNodeTiming(context, agentId, threadId, activeNodeName, context.getActiveNodeStartedAt(), now);
		}
		context.setActiveNodeName(nodeName);
		context.setActiveNodeStartedAt(now);
	}

	private void emitFinalNodeTiming(StreamContext context, String agentId, String threadId, long now) {
		if (context.getActiveNodeName() == null) {
			return;
		}
		emitNodeTiming(context, agentId, threadId, context.getActiveNodeName(), context.getActiveNodeStartedAt(), now);
	}

	private void emitNodeTiming(StreamContext context, String agentId, String threadId, String nodeName,
			long nodeStartedAt, long now) {
		GraphNodeResponse timingResponse = GraphNodeResponse.builder()
			.agentId(agentId)
			.threadId(threadId)
			.nodeName(nodeName)
			.text("")
			.textType(TextType.TEXT)
			.sequence(context.nextSequence())
			.workflowStartedAt(context.getWorkflowStartedAt())
			.nodeStartedAt(nodeStartedAt)
			.nodeElapsedMs(elapsedSince(nodeStartedAt, now))
			.totalElapsedMs(now - context.getWorkflowStartedAt())
			.timingOnly(true)
			.build();
		emitDataResponse(context, timingResponse);
	}

	private long elapsedSince(long startedAt, long now) {
		return startedAt > 0 ? Math.max(0, now - startedAt) : 0;
	}

	private void emitDataResponse(StreamContext context, GraphNodeResponse response) {
		context.cacheResponse(response);
		if (context.getSink() == null) {
			return;
		}
		Sinks.EmitResult result = context.getSink().tryEmitNext(ServerSentEvent.builder(response).build());
		if (result.isFailure()) {
			log.warn("Failed to emit data to sink for threadId: {}, result: {}. Waiting for reconnect.",
					context.getThreadId(), result);
			disconnectStream(context.getThreadId());
		}
	}

}
