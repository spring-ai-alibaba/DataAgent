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

import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.dataagent.enums.GraphEventType;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.workflow.node.PlannerNode;
import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.ClarificationContextManager;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.ClarificationContextManager.ClarificationStateSnapshot;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.MultiTurnContextManager;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.StreamContext;
import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
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
import static com.alibaba.cloud.ai.dataagent.constant.Constant.ORIGINAL_USER_QUERY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.REFINED_USER_QUERY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_GENERATE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_COMPLETE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_ERROR;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TRACE_THREAD_ID;

@Slf4j
@Service
public class GraphServiceImpl implements GraphService {

	private static final String RESUME_MODE_CLARIFICATION = "clarification";

	private final CompiledGraph compiledGraph;

	private final ExecutorService executor;

	private final BaseCheckpointSaver checkpointSaver;

	private final ConcurrentHashMap<String, StreamContext> streamContextMap = new ConcurrentHashMap<>();

	private final MultiTurnContextManager multiTurnContextManager;

	private final ClarificationContextManager clarificationContextManager;

	private final LangfuseService langfuseReporter;

	public GraphServiceImpl(StateGraph stateGraph, CompileConfig compileConfig, BaseCheckpointSaver checkpointSaver,
			ExecutorService executorService, MultiTurnContextManager multiTurnContextManager,
			LangfuseService langfuseReporter) throws GraphStateException {
		this.compiledGraph = stateGraph.compile(compileConfig);
		this.checkpointSaver = checkpointSaver;
		this.executor = executorService;
		this.multiTurnContextManager = multiTurnContextManager;
		this.clarificationContextManager = clarificationContextManager;
		this.langfuseReporter = langfuseReporter;
	}

	@Override
	public String nl2sql(String naturalQuery, String agentId) throws GraphRunnerException {
		RunnableConfig config = RunnableConfig.builder().threadId(UUID.randomUUID().toString()).build();
		try {
			OverAllState state = compiledGraph
				.invoke(Map.of(IS_ONLY_NL2SQL, true, INPUT_KEY, naturalQuery, AGENT_ID, agentId), config)
				.orElseThrow();
			return state.value(SQL_GENERATE_OUTPUT, "");
		}
		finally {
			releaseCheckpoint(config);
		}
	}

	@Override
	public void graphStreamProcess(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest graphRequest) {
		boolean resuming = StringUtils.hasText(graphRequest.getHumanFeedbackContent());
		if (!resuming) {
			if (!StringUtils.hasText(graphRequest.getConversationId())) {
				graphRequest.setConversationId(StringUtils.hasText(graphRequest.getThreadId())
						? graphRequest.getThreadId() : UUID.randomUUID().toString());
			}
			graphRequest.setThreadId(UUID.randomUUID().toString());
		}
		else if (!StringUtils.hasText(graphRequest.getThreadId())) {
			throw new IllegalArgumentException("Graph run ID is required when resuming human feedback");
		}
		if (!StringUtils.hasText(graphRequest.getConversationId())) {
			// Compatibility for existing clients: their old threadId was both the
			// conversation ID and graph run ID.
			graphRequest.setConversationId(graphRequest.getThreadId());
		}
		String threadId = graphRequest.getThreadId();
		StreamContext context = streamContextMap.computeIfAbsent(threadId, k -> new StreamContext());
		context.setConversationId(graphRequest.getConversationId());
		context.setSink(sink);

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
	public void stopStreamProcessing(String threadId) {
		if (!StringUtils.hasText(threadId)) {
			return;
		}
		log.info("Stopping stream processing for threadId: {}", threadId);
		StreamContext context = streamContextMap.remove(threadId);
		multiTurnContextManager.discardPending(context != null ? context.getConversationId() : threadId);
		if (context != null) {
			if (context.getSpan() != null && context.getSpan().isRecording()) {
				langfuseReporter.endSpanSuccess(context.getSpan(), threadId, context.getCollectedOutput());
			}
			context.cleanup();
			log.info("Cleaned up stream context for threadId: {}", threadId);
		}
		// Dispose the graph subscription before releasing its checkpoint so a
		// cancelled run cannot write another checkpoint after the release.
		releaseCheckpoint(RunnableConfig.builder().threadId(threadId).build());
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
			.build();

		handleNewProcess(resumedRequest, snapshot.originalQuery(), refinedQuery, snapshot.clarificationCount(),
				clarificationAnswer);
	}

	private void handleNewProcess(GraphRequest graphRequest, String originalUserQuery, String inputQuery,
			int clarificationCount, String clarificationAnswer) {
		String agentId = graphRequest.getAgentId();
		String threadId = graphRequest.getThreadId();
		String conversationId = graphRequest.getConversationId();
		boolean nl2sqlOnly = graphRequest.isNl2sqlOnly();
		boolean humanReviewEnabled = graphRequest.isHumanFeedback() & !(nl2sqlOnly);
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(conversationId) || !StringUtils.hasText(agentId)
				|| !StringUtils.hasText(query)) {
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

		String multiTurnContext = multiTurnContextManager.buildContext(conversationId);
		multiTurnContextManager.beginTurn(conversationId, query);
		Flux<NodeOutput> nodeOutputFlux = compiledGraph.stream(
				buildInitialState(agentId, threadId, inputQuery, originalUserQuery, multiTurnContext, nl2sqlOnly,
						humanReviewEnabled, clarificationCount, clarificationAnswer),
				RunnableConfig.builder().threadId(threadId).build());
		subscribeToFlux(context, nodeOutputFlux, graphRequest, agentId, threadId);
	}

	private Map<String, Object> buildInitialState(String agentId, String threadId, String inputQuery,
			String originalUserQuery, String multiTurnContext, boolean nl2sqlOnly, boolean humanReviewEnabled,
			int clarificationCount, String clarificationAnswer) {
		Map<String, Object> state = new HashMap<>();
		state.put(IS_ONLY_NL2SQL, nl2sqlOnly);
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

	private void handleHumanFeedback(GraphRequest graphRequest) {
		String agentId = graphRequest.getAgentId();
		String threadId = graphRequest.getThreadId();
		String conversationId = graphRequest.getConversationId();
		String feedbackContent = graphRequest.getHumanFeedbackContent();
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(conversationId) || !StringUtils.hasText(agentId)
				|| !StringUtils.hasText(feedbackContent)) {
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
			multiTurnContextManager.restartLastTurn(conversationId);
		}
		Map<String, Object> stateUpdate = new HashMap<>();
		stateUpdate.put(HUMAN_FEEDBACK_DATA, feedbackData);
		stateUpdate.put(MULTI_TURN_CONTEXT, multiTurnContextManager.buildContext(conversationId));

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
					error -> handleStreamError(graphRequest, error), () -> handleStreamComplete(graphRequest));
			// 原子性地设置 Disposable，如果已经清理则立即释放
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

	/**
	 * 处理流式错误 线程安全：使用 remove 操作确保只有一个线程能获取到 context
	 */
	private void handleStreamError(GraphRequest request, Throwable error) {
		String agentId = request.getAgentId();
		String threadId = request.getThreadId();
		log.error("Error in stream processing for threadId: {}: ", threadId, error);
		StreamContext context = streamContextMap.remove(threadId);
		multiTurnContextManager.discardPending(request.getConversationId());
		releaseCheckpoint(RunnableConfig.builder().threadId(threadId).build());
		if (context != null && !context.isCleaned()) {
			if (context.getSpan() != null) {
				langfuseReporter.endSpanError(context.getSpan(), threadId,
						error instanceof Exception ? (Exception) error : new RuntimeException(error));
			}
			if (context.getSink() != null && context.getSink().currentSubscriberCount() > 0) {
				context.getSink()
					.tryEmitNext(ServerSentEvent
						.builder(GraphNodeResponse.error(agentId, threadId,
								"Error in stream processing: " + error.getMessage()))
						.event(STREAM_EVENT_ERROR)
						.build());
				context.getSink().tryEmitComplete();
			}
			context.cleanup();
		}
	}

	/**
	 * 处理流式完成 线程安全：使用 remove 操作确保只有一个线程能获取到 context
	 */
	private void handleStreamComplete(GraphRequest request) {
		String agentId = request.getAgentId();
		String threadId = request.getThreadId();
		log.info("Stream processing completed successfully for threadId: {}", threadId);
		multiTurnContextManager.finishTurn(request.getConversationId());
		RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
		boolean awaitingHumanFeedback = isAwaitingHumanFeedback(request, config);
		if (!awaitingHumanFeedback) {
			releaseCheckpoint(config);
		}
		StreamContext context = streamContextMap.remove(threadId);
		if (context != null && !context.isCleaned()) {
			if (context.getSpan() != null) {
				langfuseReporter.endSpanSuccess(context.getSpan(), threadId, context.getCollectedOutput());
			}
			if (context.getSink() != null && context.getSink().currentSubscriberCount() > 0) {
				if (awaitingHumanFeedback) {
					context.getSink()
						.tryEmitNext(ServerSentEvent
							.builder(GraphNodeResponse.builder()
								.agentId(agentId)
								.threadId(threadId)
								.eventType(GraphEventType.HUMAN_FEEDBACK_REQUIRED)
								.textType(TextType.TEXT)
								.build())
							.build());
				}
				if (StringUtils.hasText(context.getFinalAnswer())) {
					context.getSink()
						.tryEmitNext(ServerSentEvent
							.builder(GraphNodeResponse.finalAnswer(agentId, threadId, context.getFinalAnswer()))
							.build());
				}
				context.getSink()
					.tryEmitNext(ServerSentEvent.builder(GraphNodeResponse.complete(agentId, threadId))
						.event(STREAM_EVENT_COMPLETE)
						.build());
				context.getSink().tryEmitComplete();
			}
			context.cleanup();
		}
	}

	private void handleNodeOutput(GraphRequest request, NodeOutput output) {
		log.debug("Received output: {}", output.getClass().getSimpleName());
		StreamContext context = streamContextMap.get(request.getThreadId());
		if (context != null) {
			output.state()
				.value(FINAL_ANSWER)
				.map(Object::toString)
				.filter(StringUtils::hasText)
				.ifPresent(context::setFinalAnswer);
		}
		if (output instanceof StreamingOutput streamingOutput) {
			handleStreamNodeOutput(request, streamingOutput);
		}
	}

	private void handleStreamNodeOutput(GraphRequest request, StreamingOutput output) {
		String threadId = request.getThreadId();
		StreamContext context = streamContextMap.get(threadId);
		if (context == null || context.getSink() == null) {
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
			context.appendOutput(chunk);
			StreamContext.StepIdentity stepIdentity = context.resolveStep(node);
			if (PlannerNode.class.getSimpleName().equals(node)) {
				multiTurnContextManager.appendPlannerChunk(request.getConversationId(), chunk);
			}
			boolean isClarificationNode = ClarificationNode.class.getSimpleName().equals(node);
			GraphNodeResponse response = GraphNodeResponse.builder()
				.agentId(request.getAgentId())
				.threadId(threadId)
				.stepId(stepIdentity.stepId())
				.attempt(stepIdentity.attempt())
				.nodeName(node)
				.text(chunk)
				.textType(textType)
				.interactionType(isClarificationNode ? RESUME_MODE_CLARIFICATION : "normal")
				.awaitingInput(isClarificationNode && clarificationContextManager.isAwaitingClarification(threadId))
				.build();
			Sinks.EmitResult result = context.getSink().tryEmitNext(ServerSentEvent.builder(response).build());
			if (result.isFailure()) {
				log.warn("Failed to emit data to sink for threadId: {}, result: {}. Stopping stream processing.",
						threadId, result);
				stopStreamProcessing(threadId);
			}
		}
	}

	private boolean isAwaitingHumanFeedback(GraphRequest request, RunnableConfig config) {
		if (!request.isHumanFeedback()) {
			return false;
		}
		try {
			return checkpointSaver.get(config)
				.map(checkpoint -> HUMAN_FEEDBACK_NODE.equals(checkpoint.getNextNodeId()))
				.orElse(false);
		}
		catch (Exception e) {
			log.warn("Unable to inspect checkpoint for threadId: {}", request.getThreadId(), e);
			return false;
		}
	}

	private void releaseCheckpoint(RunnableConfig config) {
		try {
			checkpointSaver.release(config);
		}
		catch (Exception e) {
			log.warn("Unable to release checkpoint for threadId: {}", config.threadId().orElse("unknown"), e);
		}
	}

}
