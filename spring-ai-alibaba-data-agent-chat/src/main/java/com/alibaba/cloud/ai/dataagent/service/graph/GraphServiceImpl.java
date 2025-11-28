/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.graph;

import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.StreamContext;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import reactor.core.Disposable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.HUMAN_FEEDBACK_NODE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.HUMAN_REVIEW_ENABLED;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.IS_ONLY_NL2SQL;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_GENERATE_OUTPUT;

@Slf4j
@Service
public class GraphServiceImpl implements GraphService {

	private final CompiledGraph compiledGraph;

	private final ExecutorService executor;

	private final ConcurrentHashMap<String, StreamContext> streamContextMap = new ConcurrentHashMap<>();

	public GraphServiceImpl(StateGraph stateGraph, ExecutorService executorService) throws GraphStateException {
		this.compiledGraph = stateGraph.compile(CompileConfig.builder().interruptBefore(HUMAN_FEEDBACK_NODE).build());
		this.compiledGraph.setMaxIterations(100);
		this.executor = executorService;
	}

	@Override
	public String nl2sql(String naturalQuery, String agentId) throws GraphRunnerException {
		OverAllState state = compiledGraph
			.call(Map.of(IS_ONLY_NL2SQL, true, INPUT_KEY, naturalQuery, AGENT_ID, agentId),
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
		// 创建或获取 StreamContext
		StreamContext context = streamContextMap.computeIfAbsent(threadId, k -> new StreamContext());
		context.setSink(sink);
		if (StringUtils.hasText(graphRequest.getHumanFeedbackContent())) {
			handleHumanFeedback(graphRequest);
		}
		else {
			handleNewProcess(graphRequest);
		}
	}

	/**
	 * 停止指定 threadId 的流式处理
	 * @param threadId 线程ID
	 */
	public void stopStreamProcessing(String threadId) {
		if (!StringUtils.hasText(threadId)) {
			return;
		}
		log.info("Stopping stream processing for threadId: {}", threadId);
		StreamContext context = streamContextMap.remove(threadId);
		if (context != null) {
			context.cleanup();
			log.info("Cleaned up stream context for threadId: {}", threadId);
		}
	}

	private void handleNewProcess(GraphRequest graphRequest) {
		String query = graphRequest.getQuery();
		String agentId = graphRequest.getAgentId();
		String threadId = graphRequest.getThreadId();
		boolean nl2sqlOnly = graphRequest.isNl2sqlOnly();
		boolean humanReviewEnabled = graphRequest.isHumanFeedback() & !(nl2sqlOnly);
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(agentId) || !StringUtils.hasText(query)) {
			throw new IllegalArgumentException("Invalid arguments");
		}
		StreamContext context = streamContextMap.get(threadId);
		if (context == null || context.getSink() == null) {
			throw new IllegalStateException("StreamContext not found for threadId: " + threadId);
		}
		Flux<NodeOutput> nodeOutputFlux = compiledGraph.fluxStream(Map.of(IS_ONLY_NL2SQL, nl2sqlOnly, INPUT_KEY, query,
				AGENT_ID, agentId, HUMAN_REVIEW_ENABLED, humanReviewEnabled),
				RunnableConfig.builder().threadId(threadId).build());
		CompletableFuture.runAsync(() -> {
			Disposable disposable = nodeOutputFlux.subscribe(output -> handleNodeOutput(graphRequest, output),
					error -> handleStreamError(agentId, threadId, error),
					() -> handleStreamComplete(agentId, threadId));
			// 保存 Disposable 以便后续取消
			context.setDisposable(disposable);
		}, executor);
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
		Map<String, Object> feedbackData = Map.of("feedback", !graphRequest.isRejectedPlan(), "feedback_content",
				feedbackContent);
		OverAllState.HumanFeedback humanFeedback = new OverAllState.HumanFeedback(feedbackData, HUMAN_FEEDBACK_NODE);
		StateSnapshot stateSnapshot = compiledGraph.getState(RunnableConfig.builder().threadId(threadId).build());
		OverAllState resumeState = stateSnapshot.state();
		resumeState.withResume();
		resumeState.withHumanFeedback(humanFeedback);

		Flux<NodeOutput> nodeOutputFlux = compiledGraph.fluxStreamFromInitialNode(resumeState,
				RunnableConfig.builder().threadId(threadId).build());
		CompletableFuture.runAsync(() -> {
			Disposable disposable = nodeOutputFlux.subscribe(output -> handleNodeOutput(graphRequest, output),
					error -> handleStreamError(agentId, threadId, error),
					() -> handleStreamComplete(agentId, threadId));
			// 保存 Disposable 以便后续取消
			context.setDisposable(disposable);
		}, executor);
	}

	/**
	 * 处理流式错误
	 */
	private void handleStreamError(String agentId, String threadId, Throwable error) {
		log.error("Error in stream processing for threadId: {}: ", threadId, error);
		StreamContext context = streamContextMap.remove(threadId);
		if (context != null && context.getSink() != null) {
			// 检查 sink 是否还有订阅者
			if (context.getSink().currentSubscriberCount() > 0) {
				context.getSink()
					.tryEmitNext(ServerSentEvent
						.builder(GraphNodeResponse.error(agentId, threadId,
								"Error in stream processing: " + error.getMessage()))
						.event("error")
						.build());
				context.getSink().tryEmitComplete();
			}
			// 清理资源
			context.cleanup();
		}
	}

	/**
	 * 处理流式完成
	 */
	private void handleStreamComplete(String agentId, String threadId) {
		log.info("Stream processing completed successfully for threadId: {}", threadId);
		StreamContext context = streamContextMap.remove(threadId);
		if (context != null && context.getSink() != null) {
			// 检查 sink 是否还有订阅者
			if (context.getSink().currentSubscriberCount() > 0) {
				context.getSink()
					.tryEmitNext(ServerSentEvent.builder(GraphNodeResponse.complete(agentId, threadId))
						.event("complete")
						.build());
				context.getSink().tryEmitComplete();
			}
			// 清理资源
			context.cleanup();
		}
	}

	/**
	 * 处理节点输出
	 */
	private void handleNodeOutput(GraphRequest request, NodeOutput output) {
		log.debug("Received output: {}", output.getClass().getSimpleName());
		if (output instanceof StreamingOutput streamingOutput) {
			handleStreamNodeOutput(request, streamingOutput);
		}
	}

	private void handleStreamNodeOutput(GraphRequest request, StreamingOutput output) {
		String threadId = request.getThreadId();
		StreamContext context = streamContextMap.get(threadId);
		// 检查是否已经停止处理
		if (context == null || context.getSink() == null) {
			log.debug("Stream processing already stopped for threadId: {}, skipping output", threadId);
			return;
		}
		String node = output.node();
		String chunk = output.chunk();
		log.debug("Received Stream output: {}", chunk);
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
			GraphNodeResponse response = GraphNodeResponse.builder()
				.agentId(request.getAgentId())
				.threadId(threadId)
				.nodeName(node)
				.text(chunk)
				.textType(textType)
				.build();
			// 检查发送是否成功，如果失败说明客户端已断开
			Sinks.EmitResult result = context.getSink().tryEmitNext(ServerSentEvent.builder(response).build());
			if (result.isFailure()) {
				log.warn("Failed to emit data to sink for threadId: {}, result: {}. Stopping stream processing.",
						threadId, result);
				// 如果发送失败，停止处理
				stopStreamProcessing(threadId);
			}
		}
	}

}
