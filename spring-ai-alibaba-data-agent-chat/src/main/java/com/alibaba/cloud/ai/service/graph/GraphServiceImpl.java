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
package com.alibaba.cloud.ai.service.graph;

import com.alibaba.cloud.ai.dto.GraphRequest;
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
import com.alibaba.cloud.ai.vo.GraphNodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static com.alibaba.cloud.ai.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.constant.Constant.HUMAN_FEEDBACK_NODE;
import static com.alibaba.cloud.ai.constant.Constant.HUMAN_REVIEW_ENABLED;
import static com.alibaba.cloud.ai.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.constant.Constant.IS_ONLY_NL2SQL;
import static com.alibaba.cloud.ai.constant.Constant.ONLY_NL2SQL_OUTPUT;

@Slf4j
@Service
public class GraphServiceImpl implements GraphService {

	private final CompiledGraph compiledGraph;

	private final ExecutorService executor;

	private final ConcurrentHashMap<String, GraphNodeResponse.TextType> stateMap = new ConcurrentHashMap<>();

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
		return state.value(ONLY_NL2SQL_OUTPUT, "");
	}

	@Override
	public void graphStreamProcess(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest graphRequest) {
		if (StringUtils.hasText(graphRequest.getHumanFeedbackContent())) {
			handleHumanFeedback(sink, graphRequest);
		}
		else {
			handleNewProcess(sink, graphRequest);
		}
	}

	private void handleNewProcess(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest graphRequest) {
		if (!StringUtils.hasText(graphRequest.getThreadId())) {
			graphRequest.setThreadId(UUID.randomUUID().toString());
		}
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
		CompletableFuture
			.runAsync(() -> nodeOutputFlux.subscribe(output -> handleNodeOutput(graphRequest, output, sink),
					error -> handleStreamError(agentId, threadId, error, sink),
					() -> handleStreamComplete(agentId, threadId, sink)), executor)
			.thenRun(() -> stateMap.remove(threadId));
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
		CompletableFuture
			.runAsync(() -> nodeOutputFlux.subscribe(output -> handleNodeOutput(graphRequest, output, sink),
					error -> handleStreamError(agentId, threadId, error, sink),
					() -> handleStreamComplete(agentId, threadId, sink)), executor)
			.thenRun(() -> stateMap.remove(threadId));
	}

	/**
	 * 处理流式错误
	 */
	private void handleStreamError(String agentId, String threadId, Throwable error,
			Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink) {
		log.error("Error in stream processing: ", error);
		sink.tryEmitNext(ServerSentEvent
			.builder(GraphNodeResponse.error(agentId, threadId, "Error in stream processing: " + error.getMessage()))
			.event("error")
			.build());
		sink.tryEmitComplete();
	}

	/**
	 * 处理流式完成
	 */
	private void handleStreamComplete(String agentId, String threadId,
			Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink) {
		log.info("Stream processing completed successfully");
		sink.tryEmitNext(
				ServerSentEvent.builder(GraphNodeResponse.complete(agentId, threadId)).event("complete").build());
		sink.tryEmitComplete();
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
		String node = output.node();
		String chunk = output.chunk();
		log.debug("Received Stream output: {}", chunk);
		String finalChunk = chunk;
		GraphNodeResponse.TextType textType = stateMap.compute(request.getThreadId(), (k, v) -> {
			if (v == null) {
				return GraphNodeResponse.TextType.TEXT;
			}
			return GraphNodeResponse.TextType.getType(v, finalChunk);
		});
		chunk = GraphNodeResponse.TextType.trimSign(textType, chunk);
		GraphNodeResponse response = GraphNodeResponse.builder()
			.agentId(request.getAgentId())
			.threadId(request.getThreadId())
			.nodeName(node)
			.text(chunk)
			.textType(textType)
			.build();
		sink.tryEmitNext(ServerSentEvent.builder(response).event(node).build());
	}

}
