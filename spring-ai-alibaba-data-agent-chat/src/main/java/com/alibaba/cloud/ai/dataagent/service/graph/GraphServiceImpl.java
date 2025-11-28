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
import java.util.concurrent.atomic.AtomicBoolean;

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

	private final ConcurrentHashMap<String, TextType> stateMap = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, Disposable> disposableMap = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, Sinks.Many<ServerSentEvent<GraphNodeResponse>>> sinkMap = new ConcurrentHashMap<>();

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
		// 保存 sink 引用，用于后续检查状态
		sinkMap.put(threadId, sink);
		if (StringUtils.hasText(graphRequest.getHumanFeedbackContent())) {
			handleHumanFeedback(sink, graphRequest);
		}
		else {
			handleNewProcess(sink, graphRequest);
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
		// 取消订阅
		Disposable disposable = disposableMap.remove(threadId);
		if (disposable != null && !disposable.isDisposed()) {
			disposable.dispose();
			log.info("Disposed subscription for threadId: {}", threadId);
		}
		// 关闭 sink
		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = sinkMap.remove(threadId);
		if (sink != null) {
			sink.tryEmitComplete();
			log.info("Closed sink for threadId: {}", threadId);
		}
		// 清理状态
		stateMap.remove(threadId);
		log.info("Cleaned up state for threadId: {}", threadId);
	}

	private void handleNewProcess(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest graphRequest) {
		String query = graphRequest.getQuery();
		String agentId = graphRequest.getAgentId();
		String threadId = graphRequest.getThreadId();
		boolean nl2sqlOnly = graphRequest.isNl2sqlOnly();
		boolean humanReviewEnabled = graphRequest.isHumanFeedback() & !(nl2sqlOnly);
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(agentId) || !StringUtils.hasText(query)) {
			throw new IllegalArgumentException("Invalid arguments");
		}
		Flux<NodeOutput> nodeOutputFlux = compiledGraph.fluxStream(Map.of(IS_ONLY_NL2SQL, nl2sqlOnly, INPUT_KEY, query,
				AGENT_ID, agentId, HUMAN_REVIEW_ENABLED, humanReviewEnabled),
				RunnableConfig.builder().threadId(threadId).build());
		CompletableFuture.runAsync(() -> {
			Disposable disposable = nodeOutputFlux.subscribe(output -> handleNodeOutput(graphRequest, output, sink),
					error -> handleStreamError(agentId, threadId, error, sink),
					() -> handleStreamComplete(agentId, threadId, sink));
			// 保存 Disposable 以便后续取消
			disposableMap.put(threadId, disposable);
		}, executor);
	}

	private void handleHumanFeedback(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest graphRequest) {
		String agentId = graphRequest.getAgentId();
		String threadId = graphRequest.getThreadId();
		String feedbackContent = graphRequest.getHumanFeedbackContent();
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(agentId) || !StringUtils.hasText(feedbackContent)) {
			throw new IllegalArgumentException("Invalid arguments");
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
			Disposable disposable = nodeOutputFlux.subscribe(output -> handleNodeOutput(graphRequest, output, sink),
					error -> handleStreamError(agentId, threadId, error, sink),
					() -> handleStreamComplete(agentId, threadId, sink));
			// 保存 Disposable 以便后续取消
			disposableMap.put(threadId, disposable);
		}, executor);
	}

	/**
	 * 处理流式错误
	 */
	private void handleStreamError(String agentId, String threadId, Throwable error,
			Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink) {
		log.error("Error in stream processing for threadId: {}: ", threadId, error);
		// 检查 sink 是否还有订阅者
		if (sink.currentSubscriberCount() > 0) {
			sink.tryEmitNext(ServerSentEvent
				.builder(
						GraphNodeResponse.error(agentId, threadId, "Error in stream processing: " + error.getMessage()))
				.event("error")
				.build());
			sink.tryEmitComplete();
		}
		// 清理资源
		disposableMap.remove(threadId);
		sinkMap.remove(threadId);
		stateMap.remove(threadId);
	}

	/**
	 * 处理流式完成
	 */
	private void handleStreamComplete(String agentId, String threadId,
			Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink) {
		log.info("Stream processing completed successfully for threadId: {}", threadId);
		// 检查 sink 是否还有订阅者
		if (sink.currentSubscriberCount() > 0) {
			sink.tryEmitNext(
					ServerSentEvent.builder(GraphNodeResponse.complete(agentId, threadId)).event("complete").build());
			sink.tryEmitComplete();
		}
		// 清理资源
		disposableMap.remove(threadId);
		sinkMap.remove(threadId);
		stateMap.remove(threadId);
	}

	/**
	 * 处理节点输出
	 */
	private void handleNodeOutput(GraphRequest request, NodeOutput output,
			Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink) {
		log.debug("Received output: {}", output.getClass().getSimpleName());
		if (output instanceof StreamingOutput streamingOutput) {
			handleStreamNodeOutput(request, streamingOutput, sink);
		}
	}

	private void handleStreamNodeOutput(GraphRequest request, StreamingOutput output,
			Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink) {
		String threadId = request.getThreadId();
		// 检查是否已经停止处理
		if (!sinkMap.containsKey(threadId)) {
			log.debug("Stream processing already stopped for threadId: {}, skipping output", threadId);
			return;
		}
		String node = output.node();
		String chunk = output.chunk();
		log.debug("Received Stream output: {}", chunk);
		AtomicBoolean typeSign = new AtomicBoolean(false);
		// 如果是文本标记符号，则更新文本类型
		TextType textType = stateMap.compute(threadId, (k, originType) -> {
			if (originType == null) {
				TextType type = TextType.getTypeByStratSign(chunk);
				if (type != TextType.TEXT) {
					typeSign.set(true);
				}
				return type;
			}
			TextType newType = TextType.getType(originType, chunk);
			if (newType != originType) {
				typeSign.set(true);
			}
			return newType;
		});
		// 文本标记符号不返回给前端
		if (!typeSign.get()) {
			GraphNodeResponse response = GraphNodeResponse.builder()
				.agentId(request.getAgentId())
				.threadId(threadId)
				.nodeName(node)
				.text(chunk)
				.textType(textType)
				.build();
			// 检查发送是否成功，如果失败说明客户端已断开
			Sinks.EmitResult result = sink.tryEmitNext(ServerSentEvent.builder(response).build());
			if (result.isFailure()) {
				log.warn("Failed to emit data to sink for threadId: {}, result: {}. Stopping stream processing.",
						threadId, result);
				// 如果发送失败，停止处理
				stopStreamProcessing(threadId);
			}
		}
	}

}
