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
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.vo.GraphResponse;
import com.alibaba.cloud.ai.vo.Nl2SqlProcessVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static com.alibaba.cloud.ai.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.constant.Constant.HUMAN_REVIEW_ENABLED;
import static com.alibaba.cloud.ai.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.constant.Constant.IS_ONLY_NL2SQL;
import static com.alibaba.cloud.ai.constant.Constant.ONLY_NL2SQL_OUTPUT;

@Slf4j
@Service
public class GraphServiceImpl implements GraphService {

	private final CompiledGraph compiledGraph;

	private final ExecutorService executor;

	public GraphServiceImpl(StateGraph stateGraph, ExecutorService executorService) throws GraphStateException {
		this.compiledGraph = stateGraph.compile();
		this.compiledGraph.setMaxIterations(100);
		this.executor = executorService;
	}

	@Override
	public String nl2sql(String naturalQuery, String agentId) throws GraphRunnerException {
		if (agentId == null) {
			agentId = "";
		}
		Map<String, Object> stateMap = Map.of(IS_ONLY_NL2SQL, true, INPUT_KEY, naturalQuery, AGENT_ID, agentId);
		Optional<OverAllState> call = this.compiledGraph.call(stateMap);
		OverAllState state = call.orElseThrow(() -> {
			log.error("Nl2SqlService call fail, stateMap: {}", stateMap);
			return new GraphRunnerException("图运行失败");
		});
		return state.value(ONLY_NL2SQL_OUTPUT, "");
	}

	@Override
	public void nl2sqlWithProcess(Consumer<Nl2SqlProcessVO> nl2SqlProcessConsumer, String naturalQuery, String agentId,
			RunnableConfig runnableConfig) {
		Map<String, Object> stateMap = Map.of(IS_ONLY_NL2SQL, true, INPUT_KEY, naturalQuery, AGENT_ID, agentId);
		Consumer<NodeOutput> consumer = (output) -> {
			Nl2SqlProcessVO sqlProcess = this.nodeOutputToNl2sqlProcess(output);
			nl2SqlProcessConsumer.accept(sqlProcess);
		};
		this.compiledGraph.fluxStream(stateMap, runnableConfig).doOnNext(consumer).then().toFuture();
	}

	/**
	 * 将NodeOutput转为Nl2SqlProcess实体类（用于nl2sqlWithProcess的consumer中记录转化过程）
	 * @param output NodeOutput
	 * @return NlSqlProcess
	 */
	private Nl2SqlProcessVO nodeOutputToNl2sqlProcess(NodeOutput output) {
		// 将节点运行结果进行包装
		String nodeRes = "";
		if (output instanceof StreamingOutput streamingOutput) {
			nodeRes = streamingOutput.chunk();
		}
		else {
			nodeRes = output.toString();
		}

		// 如果是结束节点，取出最终生成结果
		if (StateGraph.END.equals(output.node())) {
			String result = output.state().value(ONLY_NL2SQL_OUTPUT, "");
			return Nl2SqlProcessVO.success(result, output.node(), nodeRes);
		}
		return Nl2SqlProcessVO.processing(output.node(), nodeRes);
	}

	@Override
	public void graphStreamProcess(Sinks.Many<ServerSentEvent<GraphResponse>> sink, GraphRequest graphRequest) {
		if (StringUtils.hasText(graphRequest.getHumanFeedbackContent())) {
			handleHumanFeedback(sink, graphRequest);
		}
		else if (graphRequest.isNl2sqlOnly()) {
			handleNewNl2SqlProcess(sink, graphRequest);
		}
		else {
			handleNewProcess(sink, graphRequest);
		}
	}

	private void handleNewProcess(Sinks.Many<ServerSentEvent<GraphResponse>> sink, GraphRequest graphRequest) {
		String query = graphRequest.getQuery();
		String agentId = graphRequest.getAgentId();
		String threadId = graphRequest.getThreadId();
		boolean humanReviewEnabled = graphRequest.isHumanFeedback();
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(agentId) || !StringUtils.hasText(query)) {
			throw new IllegalArgumentException("Invalid arguments");
		}
		Flux<NodeOutput> nodeOutputFlux = compiledGraph.fluxStream(
				Map.of(INPUT_KEY, query, AGENT_ID, agentId, HUMAN_REVIEW_ENABLED, humanReviewEnabled),
				RunnableConfig.builder().threadId(threadId).build());
		CompletableFuture.runAsync(() -> {
			nodeOutputFlux.subscribe(output -> handleNodeOutput(graphRequest, output, sink),
					error -> handleStreamError(agentId, threadId, error, sink),
					() -> handleStreamComplete(agentId, threadId, sink));
		}, executor);
	}

	private void handleNewNl2SqlProcess(Sinks.Many<ServerSentEvent<GraphResponse>> sink, GraphRequest graphRequest) {
	}

	private void handleHumanFeedback(Sinks.Many<ServerSentEvent<GraphResponse>> sink, GraphRequest graphRequest) {

	}

	/**
	 * 处理流式错误
	 */
	private void handleStreamError(String agentId, String threadId, Throwable error,
			Sinks.Many<ServerSentEvent<GraphResponse>> sink) {
		log.error("Error in stream processing: ", error);
		sink.tryEmitNext(ServerSentEvent
			.builder(GraphResponse.error(agentId, threadId, "Error in stream processing: " + error.getMessage()))
			.event("error")
			.build());
		sink.tryEmitComplete();
	}

	/**
	 * 处理流式完成
	 */
	private void handleStreamComplete(String agentId, String threadId,
			Sinks.Many<ServerSentEvent<GraphResponse>> sink) {
		log.info("Stream processing completed successfully");
		sink.tryEmitNext(ServerSentEvent.builder(GraphResponse.complete(agentId, threadId)).event("complete").build());
		sink.tryEmitComplete();
	}

	/**
	 * 处理节点输出
	 */
	private void handleNodeOutput(GraphRequest request, NodeOutput output,
			Sinks.Many<ServerSentEvent<GraphResponse>> sink) {
		log.debug("Received output: {}", output.getClass().getSimpleName());
		if (output instanceof StreamingOutput streamingOutput) {
			handleStreamNodeOutput(request, streamingOutput, sink);
		}
	}

	private void handleStreamNodeOutput(GraphRequest request, StreamingOutput output,
			Sinks.Many<ServerSentEvent<GraphResponse>> sink) {

	}

}
