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
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.StreamContext;
import com.alibaba.cloud.ai.dataagent.service.graph.runtime.ActiveGraphRunRegistry;
import com.alibaba.cloud.ai.dataagent.service.graph.turn.ConversationTurnService;
import com.alibaba.cloud.ai.dataagent.service.graph.turn.TurnExecutionScope;
import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.dataagent.service.langfuse.NodeTracingLifecycleListener;
import com.alibaba.cloud.ai.dataagent.service.memory.context.ConversationContextAssembler;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryEventType;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryOutboxService;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import com.alibaba.cloud.ai.dataagent.workflow.node.ReportGeneratorNode;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

@Slf4j
@Service
public class GraphServiceImpl implements GraphService {

	private final CompiledGraph compiledGraph;

	private final ExecutorService executor;

	private final BaseCheckpointSaver checkpointSaver;

	private final ActiveGraphRunRegistry activeGraphRunRegistry;

	private final ConversationContextAssembler contextAssembler;

	private final ConversationTurnService turnService;

	private final MemoryOutboxService outboxService;

	private final AgentDatasourceMapper agentDatasourceMapper;

	private final LangfuseService langfuseReporter;

	private final NodeTracingLifecycleListener nodeTracingLifecycleListener;

	public GraphServiceImpl(StateGraph stateGraph, CompileConfig compileConfig, BaseCheckpointSaver checkpointSaver,
			ExecutorService executorService, ConversationContextAssembler contextAssembler,
			ConversationTurnService turnService, MemoryOutboxService outboxService,
			AgentDatasourceMapper agentDatasourceMapper, ActiveGraphRunRegistry activeGraphRunRegistry,
			LangfuseService langfuseReporter, NodeTracingLifecycleListener nodeTracingLifecycleListener)
			throws GraphStateException {
		this.compiledGraph = stateGraph.compile(compileConfig);
		this.checkpointSaver = checkpointSaver;
		this.executor = executorService;
		this.contextAssembler = contextAssembler;
		this.turnService = turnService;
		this.outboxService = outboxService;
		this.agentDatasourceMapper = agentDatasourceMapper;
		this.activeGraphRunRegistry = activeGraphRunRegistry;
		this.langfuseReporter = langfuseReporter;
		this.nodeTracingLifecycleListener = nodeTracingLifecycleListener;
	}

	@Override
	public String nl2sql(String naturalQuery, String agentId) throws GraphRunnerException {
		RunnableConfig config = RunnableConfig.builder().threadId(UUID.randomUUID().toString()).build();
		try {
			Map<String, Object> input = new HashMap<>();
			input.put(IS_ONLY_NL2SQL, true);
			input.put(INPUT_KEY, naturalQuery);
			input.put(AGENT_ID, agentId);
			Integer datasourceId = resolveActiveDatasourceId(Integer.valueOf(agentId));
			if (datasourceId != null) {
				input.put(DATASOURCE_ID, datasourceId);
			}
			OverAllState state = compiledGraph.invoke(input, config).orElseThrow();
			return state.value(SQL_GENERATE_OUTPUT, "");
		}
		finally {
			releaseCheckpoint(config, false);
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
		StreamContext context = new StreamContext();
		context.setAgentId(graphRequest.getAgentId());
		context.setConversationId(graphRequest.getConversationId());
		context.setSink(sink);
		if (!activeGraphRunRegistry.register(threadId, context)) {
			throw new IllegalStateException("Graph run is already active: " + threadId);
		}
		try {
			if (StringUtils.hasText(graphRequest.getHumanFeedbackContent())) {
				handleHumanFeedback(graphRequest);
			}
			else {
				handleNewProcess(graphRequest);
			}
		}
		catch (RuntimeException e) {
			boolean ownsRun = !resuming || StringUtils.hasText(context.getTurnId());
			cleanupFailedStart(context, threadId, e, ownsRun);
			throw e;
		}
	}

	/**
	 * 停止指定 threadId 的流式处理 线程安全：使用 remove 操作确保只有一个线程能获取到 context
	 * @param threadId 线程ID
	 */
	@Override
	public void stopStreamProcessing(String threadId, String agentId) {
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(agentId)) {
			return;
		}
		log.info("Stopping stream processing for threadId: {}", threadId);
		StreamContext context = activeGraphRunRegistry.removeOwned(threadId, agentId, false);
		if (context == null) {
			log.warn("Ignoring stop request for unowned or inactive graph run: {}", threadId);
			return;
		}
		// Serialize disposal with subscription registration. Once this block returns,
		// the removed context can no longer start a graph subscription that recreates a
		// checkpoint after release.
		synchronized (context) {
			context.cleanup();
		}
		boolean cancellationPersisted = false;
		try {
			turnService.cancelTurn(context.getTurnId(), threadId, context.timelineJson());
			cancellationPersisted = true;
		}
		catch (RuntimeException e) {
			log.error("Failed to persist cancellation for threadId: {}", threadId, e);
		}
		// 客户端断开，结束根 Langfuse span。必须在 discardThread 清理累加器之前，
		// 否则根 span 的 token 汇总会被提前清空。
		if (context.getSpan() != null && context.getSpan().isRecording()) {
			langfuseReporter.endSpanSuccess(context.getSpan(), threadId, context.getCollectedOutput());
		}
		log.info("Cleaned up stream context for threadId: {}", threadId);
		// 客户端断开是唯一绕过节点 after/onError 的路径：结束仍挂着的节点 span（标记为断开）
		// 并清理计数器/累加器，否则会内存泄漏，且 Langfuse 上会留下永不结束的 span。
		nodeTracingLifecycleListener.discardThread(threadId);
		// Dispose the graph subscription before releasing its checkpoint so a
		// cancelled run cannot write another checkpoint after the release.
		if (cancellationPersisted && !context.isCheckpointReleaseDeferred()) {
			releaseCheckpoint(RunnableConfig.builder().threadId(threadId).build(),
					StringUtils.hasText(context.getTurnId()));
		}
	}

	@Override
	public void stopStreamProcessingByConversationId(String conversationId, String agentId) {
		activeGraphRunRegistry.findRunIds(conversationId, agentId)
			.forEach(threadId -> stopStreamProcessing(threadId, agentId));
	}

	private void handleNewProcess(GraphRequest graphRequest) {
		String query = graphRequest.getQuery();
		String agentId = graphRequest.getAgentId();
		String threadId = graphRequest.getThreadId();
		String conversationId = graphRequest.getConversationId();
		boolean nl2sqlOnly = graphRequest.isNl2sqlOnly();
		boolean humanReviewEnabled = graphRequest.isHumanFeedback() & !(nl2sqlOnly);
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(conversationId) || !StringUtils.hasText(agentId)
				|| !StringUtils.hasText(query)) {
			throw new IllegalArgumentException("Invalid arguments");
		}
		StreamContext context = activeGraphRunRegistry.get(threadId);
		if (context == null || context.getSink() == null) {
			throw new IllegalStateException("StreamContext not found for threadId: " + threadId);
		}
		// 检查是否已经清理，如果已清理则不再启动新的流
		if (!isActiveContext(threadId, context)) {
			log.warn("StreamContext already cleaned for threadId: {}, skipping stream start", threadId);
			return;
		}

		Integer numericAgentId = Integer.valueOf(agentId);
		Integer datasourceId = resolveActiveDatasourceId(numericAgentId);
		String turnId = turnService.beginTurn(conversationId, numericAgentId, datasourceId, threadId, query,
				graphRequest.isTitleNeeded());
		graphRequest.setTurnId(turnId);
		context.setTurnId(turnId);
		if (!continueSetup(context, threadId)) {
			return;
		}
		String multiTurnContext = contextAssembler.build(conversationId, numericAgentId, query, datasourceId);
		if (!continueSetup(context, threadId)) {
			return;
		}
		Map<String, Object> input = new HashMap<>();
		input.put(IS_ONLY_NL2SQL, nl2sqlOnly);
		input.put(INPUT_KEY, query);
		input.put(AGENT_ID, agentId);
		input.put(HUMAN_REVIEW_ENABLED, humanReviewEnabled);
		input.put(MULTI_TURN_CONTEXT, multiTurnContext);
		input.put(TRACE_THREAD_ID, threadId);
		if (datasourceId != null) {
			input.put(DATASOURCE_ID, datasourceId);
		}
		Flux<NodeOutput> nodeOutputFlux = compiledGraph.stream(input,
				RunnableConfig.builder().threadId(threadId).build());
		if (!startSpanIfActive("graph-stream", graphRequest, context, threadId)) {
			continueSetup(context, threadId);
			return;
		}
		subscribeToFlux(context, nodeOutputFlux, graphRequest, agentId, threadId);
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
		StreamContext context = activeGraphRunRegistry.get(threadId);
		if (context == null || context.getSink() == null) {
			throw new IllegalStateException("StreamContext not found for threadId: " + threadId);
		}
		if (!isActiveContext(threadId, context)) {
			log.warn("StreamContext already cleaned for threadId: {}, skipping stream start", threadId);
			return;
		}
		TurnExecutionScope turnScope = turnService.resumeTurn(graphRequest.getTurnId(), threadId,
				graphRequest.isRejectedPlan(), Integer.valueOf(agentId), conversationId);
		graphRequest.setTurnId(turnScope.turnId());
		graphRequest.setConversationId(turnScope.conversationId());
		context.setTurnId(turnScope.turnId());
		context.setConversationId(turnScope.conversationId());
		if (!continueSetup(context, threadId)) {
			return;
		}
		Integer datasourceId = turnScope.datasourceId();

		Map<String, Object> feedbackData = Map.of("feedback", !graphRequest.isRejectedPlan(), "feedback_content",
				feedbackContent);
		Map<String, Object> stateUpdate = new HashMap<>();
		stateUpdate.put(HUMAN_FEEDBACK_DATA, feedbackData);
		stateUpdate.put(MULTI_TURN_CONTEXT, contextAssembler.build(turnScope.conversationId(), turnScope.agentId(),
				turnScope.rawQuery(), datasourceId));
		if (!continueSetup(context, threadId)) {
			return;
		}
		if (datasourceId != null) {
			stateUpdate.put(DATASOURCE_ID, datasourceId);
		}

		RunnableConfig baseConfig = RunnableConfig.builder().threadId(threadId).build();
		RunnableConfig updatedConfig;
		try {
			updatedConfig = compiledGraph.updateState(baseConfig, stateUpdate);
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to update graph state for human feedback", e);
		}
		if (!continueSetup(context, threadId)) {
			return;
		}
		RunnableConfig resumeConfig = RunnableConfig.builder(updatedConfig)
			.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, feedbackData)
			.build();

		Flux<NodeOutput> nodeOutputFlux = compiledGraph.stream(null, resumeConfig);
		if (!startSpanIfActive("graph-feedback", graphRequest, context, threadId)) {
			continueSetup(context, threadId);
			return;
		}
		subscribeToFlux(context, nodeOutputFlux, graphRequest, agentId, threadId);
	}

	/**
	 * 订阅 Flux 并原子性地设置 Disposable 线程安全：使用 synchronized 确保 Disposable 设置的原子性
	 * @param context 流式处理上下文
	 * @param nodeOutputFlux 节点输出流
	 * @param graphRequest 图请求
	 * @param agentId 代理ID
	 * @param threadId 线程ID
	 */
	private void subscribeToFlux(StreamContext context, Flux<NodeOutput> nodeOutputFlux, GraphRequest graphRequest,
			String agentId, String threadId) {
		CompletableFuture.runAsync(() -> {
			synchronized (context) {
				// Keep the identity check and subscription registration atomic with stop
				// cleanup. Otherwise a removed context could subscribe after its
				// checkpoint
				// had already been released, racing a replacement run with the same ID.
				if (!isActiveContext(threadId, context)) {
					log.debug("StreamContext cleaned before subscription for threadId: {}", threadId);
					return;
				}
				Disposable disposable = nodeOutputFlux.subscribe(
						output -> handleNodeOutput(graphRequest, context, output),
						error -> handleStreamError(graphRequest, context, error),
						() -> handleStreamComplete(graphRequest, context));
				if (!isActiveContext(threadId, context)) {
					// 如果已经清理，立即释放刚创建的 Disposable
					if (disposable != null && !disposable.isDisposed()) {
						disposable.dispose();
					}
				}
				else {
					// 只有在未清理的情况下才设置 Disposable
					context.setDisposable(disposable);
				}
			}
		}, executor);
	}

	/**
	 * 处理流式错误 线程安全：使用 remove 操作确保只有一个线程能获取到 context
	 */
	private void handleStreamError(GraphRequest request, StreamContext expectedContext, Throwable error) {
		String agentId = request.getAgentId();
		String threadId = request.getThreadId();
		if (!activeGraphRunRegistry.remove(threadId, expectedContext)) {
			log.debug("Ignoring terminal error from a stale graph subscription: {}", threadId);
			return;
		}
		log.error("Error in stream processing for threadId: {}: ", threadId, error);
		StreamContext context = expectedContext;
		if (context != null && !context.isCleaned()) {
			boolean failurePersisted = false;
			try {
				turnService.failTurn(context.getTurnId(), threadId, error, context.timelineJson());
				failurePersisted = true;
			}
			catch (RuntimeException persistenceError) {
				log.error("Failed to persist graph error for threadId: {}", threadId, persistenceError);
			}
			if (failurePersisted) {
				releaseCheckpoint(RunnableConfig.builder().threadId(threadId).build(),
						StringUtils.hasText(context.getTurnId()));
			}
			// 结束 Langfuse span（失败）。先结束根 span 取走 token 汇总，再清理 listener 侧残留。
			if (context.getSpan() != null) {
				langfuseReporter.endSpanError(context.getSpan(), threadId,
						error instanceof Exception ? (Exception) error : new RuntimeException(error));
			}
			// 清理 listener 侧的 attempt 计数器与仍挂着的节点 span，避免无界增长。
			nodeTracingLifecycleListener.finishThread(threadId);
			if (context.getSink() != null && context.getSink().currentSubscriberCount() > 0) {
				context.getSink()
					.tryEmitNext(ServerSentEvent
						.builder(GraphNodeResponse.error(agentId, threadId, context.getTurnId(),
								"Error in stream processing: " + error.getMessage()))
						.event(STREAM_EVENT_ERROR)
						.build());
				context.getSink().tryEmitComplete();
			}
			// 清理资源（cleanup 内部已经保证只执行一次）
			context.cleanup();
		}
	}

	/**
	 * 处理流式完成 线程安全：使用 remove 操作确保只有一个线程能获取到 context
	 */
	private void handleStreamComplete(GraphRequest request, StreamContext expectedContext) {
		String agentId = request.getAgentId();
		String threadId = request.getThreadId();
		if (!activeGraphRunRegistry.remove(threadId, expectedContext)) {
			log.debug("Ignoring completion from a stale graph subscription: {}", threadId);
			return;
		}
		log.info("Stream processing completed successfully for threadId: {}", threadId);
		RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
		StreamContext context = expectedContext;
		if (context != null && !context.isCleaned()) {
			boolean awaitingHumanFeedback;
			try {
				awaitingHumanFeedback = isAwaitingHumanFeedback(request, config);
				if (awaitingHumanFeedback) {
					turnService.markWaitingReview(context.getTurnId(), threadId, context.timelineJson());
				}
				else {
					turnService.completeTurn(context.getTurnId(), threadId, context.getMemorySnapshot(),
							context.getReportContent(), context.timelineJson());
					releaseCheckpoint(config, StringUtils.hasText(context.getTurnId()));
				}
			}
			catch (RuntimeException e) {
				log.error("Failed to persist completed graph run for threadId: {}", threadId, e);
				boolean failurePersisted = false;
				try {
					turnService.failTurn(context.getTurnId(), threadId, e, context.timelineJson());
					failurePersisted = true;
				}
				catch (RuntimeException failurePersistenceError) {
					log.error("Failed to mark graph run failed after completion persistence error for threadId: {}",
							threadId, failurePersistenceError);
				}
				if (failurePersisted) {
					releaseCheckpoint(config, StringUtils.hasText(context.getTurnId()));
				}
				emitPersistenceError(context, agentId, threadId, e);
				return;
			}
			// 结束 Langfuse span（成功）。必须在清理节点级 accumulator 之前，否则根 span 拿不到 token 汇总。
			if (context.getSpan() != null) {
				langfuseReporter.endSpanSuccess(context.getSpan(), threadId, context.getCollectedOutput());
			}
			// 正常终止（非等待人工反馈）时清理 listener 侧的 attempt 计数器残留，避免无界增长。
			// 等待人工反馈时保留计数器，让反馈恢复后的 attempt 能接着上一轮递增。
			if (!awaitingHumanFeedback) {
				nodeTracingLifecycleListener.finishThread(threadId);
			}
			if (context.getSink() != null && context.getSink().currentSubscriberCount() > 0) {
				if (awaitingHumanFeedback) {
					context.getSink()
						.tryEmitNext(ServerSentEvent
							.builder(GraphNodeResponse.builder()
								.agentId(agentId)
								.threadId(threadId)
								.turnId(context.getTurnId())
								.eventType(GraphEventType.HUMAN_FEEDBACK_REQUIRED)
								.textType(TextType.TEXT)
								.build())
							.build());
				}
				if (StringUtils.hasText(context.getFinalAnswer())) {
					context.getSink()
						.tryEmitNext(ServerSentEvent
							.builder(GraphNodeResponse.finalAnswer(agentId, threadId, context.getTurnId(),
									context.getFinalAnswer()))
							.build());
				}
				context.getSink()
					.tryEmitNext(
							ServerSentEvent.builder(GraphNodeResponse.complete(agentId, threadId, context.getTurnId()))
								.event(STREAM_EVENT_COMPLETE)
								.build());
				context.getSink().tryEmitComplete();
			}
			context.cleanup();
		}
		else {
			releaseCheckpoint(config, context != null && StringUtils.hasText(context.getTurnId()));
		}
	}

	private void emitPersistenceError(StreamContext context, String agentId, String threadId, RuntimeException error) {
		if (context.getSpan() != null) {
			langfuseReporter.endSpanError(context.getSpan(), threadId, error);
		}
		nodeTracingLifecycleListener.finishThread(threadId);
		if (context.getSink() != null && context.getSink().currentSubscriberCount() > 0) {
			context.getSink()
				.tryEmitNext(ServerSentEvent
					.builder(GraphNodeResponse.error(agentId, threadId, context.getTurnId(),
							"Failed to persist graph result"))
					.event(STREAM_EVENT_ERROR)
					.build());
			context.getSink().tryEmitComplete();
		}
		context.cleanup();
	}

	/**
	 * 处理节点输出
	 */
	private void handleNodeOutput(GraphRequest request, StreamContext expectedContext, NodeOutput output) {
		log.debug("Received output: {}", output.getClass().getSimpleName());
		if (!activeGraphRunRegistry.isActive(request.getThreadId(), expectedContext)) {
			return;
		}
		expectedContext.getMemorySnapshot().capture(output.state(), output.node());
		output.state()
			.value(FINAL_ANSWER)
			.map(Object::toString)
			.filter(StringUtils::hasText)
			.ifPresent(expectedContext::setFinalAnswer);
		if (output instanceof StreamingOutput streamingOutput) {
			handleStreamNodeOutput(request, expectedContext, streamingOutput);
		}
	}

	private void handleStreamNodeOutput(GraphRequest request, StreamContext context, StreamingOutput output) {
		String threadId = request.getThreadId();
		// 检查是否已经停止处理
		if (!activeGraphRunRegistry.isActive(threadId, context) || context.getSink() == null) {
			log.debug("Stream processing already stopped for threadId: {}, skipping output", threadId);
			return;
		}
		String node = output.node();
		String chunk = output.chunk();
		log.debug("Received Stream output: {}", chunk);

		if (chunk == null || chunk.isEmpty()) {
			return;
		}

		// 如果是文本标记符号，则更新文本类型
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
		// 文本标记符号不返回给前端
		if (!isTypeSign) {
			context.appendOutput(chunk);
			StreamContext.StepIdentity stepIdentity = context.resolveStep(node);
			GraphNodeResponse response = GraphNodeResponse.builder()
				.agentId(request.getAgentId())
				.threadId(threadId)
				.turnId(context.getTurnId())
				.stepId(stepIdentity.stepId())
				.attempt(stepIdentity.attempt())
				.nodeName(node)
				.text(chunk)
				.textType(textType)
				.build();
			if (ReportGeneratorNode.class.getSimpleName().equals(node) && textType == TextType.MARK_DOWN) {
				context.appendReport(chunk);
			}
			context.recordResponse(response);
			// 检查发送是否成功，如果失败说明客户端已断开
			Sinks.EmitResult result = context.getSink().tryEmitNext(ServerSentEvent.builder(response).build());
			if (result.isFailure()) {
				log.warn("Failed to emit data to sink for threadId: {}, result: {}. Stopping stream processing.",
						threadId, result);
				// 如果发送失败，停止处理
				stopStreamProcessing(threadId, request.getAgentId());
			}
		}
	}

	private void cleanupFailedStart(StreamContext context, String threadId, RuntimeException error, boolean ownsRun) {
		activeGraphRunRegistry.remove(threadId, context);
		if (ownsRun) {
			boolean failurePersisted = false;
			try {
				turnService.failTurn(context.getTurnId(), threadId, error, context.timelineJson());
				failurePersisted = true;
			}
			catch (RuntimeException persistenceError) {
				log.error("Failed to persist graph startup error for threadId: {}", threadId, persistenceError);
			}
			if (failurePersisted && !context.isCheckpointReleaseDeferred()) {
				releaseCheckpoint(RunnableConfig.builder().threadId(threadId).build(),
						StringUtils.hasText(context.getTurnId()));
			}
		}
		if (context.getSpan() != null) {
			langfuseReporter.endSpanError(context.getSpan(), threadId, error);
		}
		if (ownsRun) {
			nodeTracingLifecycleListener.finishThread(threadId);
		}
		context.cleanup();
	}

	private boolean isActiveContext(String threadId, StreamContext context) {
		return activeGraphRunRegistry.isActive(threadId, context);
	}

	/**
	 * Reconciles an early stop that happened while a durable turn or checkpoint update
	 * was still being created. The stop path may have observed a null turn ID, so the
	 * setup path must cancel the newly created turn itself before returning.
	 */
	private boolean continueSetup(StreamContext context, String threadId) {
		if (isActiveContext(threadId, context)) {
			return true;
		}
		boolean cancellationPersisted = false;
		try {
			turnService.cancelTurn(context.getTurnId(), threadId, context.timelineJson());
			cancellationPersisted = true;
		}
		catch (RuntimeException e) {
			log.error("Failed to reconcile an interrupted graph startup for threadId: {}", threadId, e);
		}
		context.cleanup();
		if (cancellationPersisted && !context.isCheckpointReleaseDeferred()) {
			releaseCheckpoint(RunnableConfig.builder().threadId(threadId).build(),
					StringUtils.hasText(context.getTurnId()));
		}
		return false;
	}

	private boolean startSpanIfActive(String operation, GraphRequest request, StreamContext context, String threadId) {
		if (!isActiveContext(threadId, context)) {
			return false;
		}
		Span span = langfuseReporter.startLLMSpan(operation, request);
		context.setSpan(span);
		if (isActiveContext(threadId, context)) {
			return true;
		}
		// A stop can race between starting and publishing the span to StreamContext.
		// In that case the stop path could not see it, so finish it here.
		if (span != null && span.isRecording()) {
			langfuseReporter.endSpanSuccess(span, threadId, context.getCollectedOutput());
		}
		return false;
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
			throw new IllegalStateException(
					"Unable to determine human-feedback checkpoint state for threadId: " + request.getThreadId(), e);
		}
	}

	private void releaseCheckpoint(RunnableConfig config, boolean durableCleanupScheduled) {
		String threadId = config.threadId().orElse("unknown");
		boolean cleanupScheduled = durableCleanupScheduled;
		if (!cleanupScheduled) {
			cleanupScheduled = scheduleCheckpointCleanup(threadId);
		}
		try {
			checkpointSaver.release(config);
		}
		catch (Exception e) {
			try {
				if (checkpointSaver.list(config).isEmpty()) {
					if (!cleanupScheduled) {
						scheduleCheckpointCleanup(threadId);
					}
					return;
				}
			}
			catch (RuntimeException verificationFailure) {
				e.addSuppressed(verificationFailure);
			}
			log.warn("Unable to release checkpoint for threadId: {}; scheduling durable cleanup", threadId, e);
			if (!cleanupScheduled) {
				scheduleCheckpointCleanup(threadId);
			}
			return;
		}
		if (!cleanupScheduled) {
			scheduleCheckpointCleanup(threadId);
		}
	}

	private boolean scheduleCheckpointCleanup(String threadId) {
		try {
			outboxService.enqueue("GRAPH_RUN", threadId, MemoryEventType.GRAPH_CHECKPOINT_RELEASE, null);
			return true;
		}
		catch (RuntimeException outboxFailure) {
			log.error("Unable to schedule checkpoint cleanup for threadId: {}", threadId, outboxFailure);
			return false;
		}
	}

	private Integer resolveActiveDatasourceId(Integer agentId) {
		return agentDatasourceMapper.selectActiveDatasourceIdByAgentId(agentId.longValue());
	}

}
