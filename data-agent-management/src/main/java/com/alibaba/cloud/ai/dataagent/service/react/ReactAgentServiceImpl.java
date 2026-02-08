/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.service.react;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_COMPLETE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_ERROR;

import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.dto.tool.ToolCallDTO;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.interceptor.ToolsInterceptor;
import com.alibaba.cloud.ai.dataagent.interceptor.ToolsInterceptor.ToolCallCallback;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.ReactAgentRegistry;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.MultiTurnContextManager;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.StreamContext;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import com.alibaba.cloud.ai.dataagent.workflow.node.PlannerNode;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Service
public class ReactAgentServiceImpl implements ReactAgentService {

	private final ReactAgentRegistry reactAgentRegistry;

	private final Map<String, StreamContext> streamContextMap = new ConcurrentHashMap<>();

	private final ExecutorService executor;

	private final MultiTurnContextManager multiTurnContextManager;

	private final ToolsInterceptor toolsInterceptor;

	public ReactAgentServiceImpl(ExecutorService executor, ReactAgentRegistry reactAgentRegistry,
			MultiTurnContextManager multiTurnContextManager, ToolsInterceptor toolsInterceptor)
			throws GraphStateException {
		this.executor = executor;
		this.reactAgentRegistry = reactAgentRegistry;
		this.multiTurnContextManager = multiTurnContextManager;
		this.toolsInterceptor = toolsInterceptor;

		// 添加工具调用回调
		this.toolsInterceptor.addToolCallCallback(new ToolCallCallback() {
			@Override
			public void onToolCall(String threadId, String toolName, String input, String output) {
				ReactAgentServiceImpl.this.handleToolCall(threadId, toolName, input, output);
			}

		});
	}

	@Override
	public void reactStreamProcess(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest request) {
		String threadId = request.getThreadId();
		String agentId = request.getAgentId();
		String query = request.getQuery();

		ReactAgent reactAgent = reactAgentRegistry.getReactAgent();
		RunnableConfig config = RunnableConfig.builder()
			.threadId(threadId)
			.addMetadata("thread_id", threadId)
			.addMetadata("agent_id", agentId)
			.addMetadata("current_time", new Date().toString())
			.build();
		// 创建并存储 StreamContext
		StreamContext context = streamContextMap.computeIfAbsent(threadId, k -> new StreamContext());
		context.setSink(sink);
		// 过滤掉开始和结束事件
		Flux<?> nodeOutputFlux = null;
		try {
			nodeOutputFlux = reactAgent.stream(query, config)
				.filter(nodeOutput -> !nodeOutput.isSTART() && !nodeOutput.isEND());
		}
		catch (GraphRunnerException e) {
			log.error("Error streaming node outputs for threadId: {}", threadId, e);
			throw new RuntimeException(e);
		}
		subscribeToFlux(context, nodeOutputFlux, agentId, threadId);
	}

	@Override
	public void stopStreamProcessing(String threadId) {
		log.info("Stopping stream processing for threadId: {}", threadId);
		StreamContext context = streamContextMap.remove(threadId);
		if (context != null) {
			context.cleanup();
		}
	}

	/**
	 * 订阅 Flux 并原子性地设置 Disposable 线程安全：使用 synchronized 确保 Disposable 设置的原子性
	 * @param context 流式处理上下文
	 * @param nodeOutputFlux 节点输出流
	 * @param agentId 代理ID
	 * @param threadId 线程ID
	 */
	private void subscribeToFlux(StreamContext context, Flux<?> nodeOutputFlux, String agentId, String threadId) {
		CompletableFuture.runAsync(() -> {
			// 在订阅之前检查上下文是否仍然有效
			if (context.isCleaned()) {
				log.debug("StreamContext cleaned before subscription for threadId: {}", threadId);
				return;
			}
			Disposable disposable = nodeOutputFlux.subscribe(output -> handleNodeOutput(output, agentId, threadId),
					error -> handleStreamError(agentId, threadId, error),
					() -> handleStreamComplete(agentId, threadId));
			// 原子性地设置 Disposable，如果已经清理则立即释放
			synchronized (context) {
				if (context.isCleaned()) {
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
	 * 处理节点输出
	 */
	private void handleNodeOutput(Object nodeOutput, String agentId, String threadId) {
		StreamContext context = streamContextMap.get(threadId);
		// 检查是否已经停止处理
		if (context == null || context.getSink() == null) {
			log.debug("Stream processing already stopped for threadId: {}, skipping output", threadId);
			return;
		}
		if (nodeOutput instanceof StreamingOutput streamingOutput) {
			handleStreamNodeOutput(threadId, agentId, streamingOutput);
		}
	}

	/**
	 * 处理流式错误 线程安全：使用 remove 操作确保只有一个线程能获取到 context
	 */
	private void handleStreamError(String agentId, String threadId, Throwable error) {
		log.error("Error in stream processing for threadId: {}: ", threadId, error);
		StreamContext context = streamContextMap.remove(threadId);
		if (context != null && !context.isCleaned() && context.getSink() != null) {
			// 检查 sink 是否还有订阅者
			if (context.getSink().currentSubscriberCount() > 0) {
				context.getSink()
					.tryEmitNext(ServerSentEvent
						.builder(GraphNodeResponse.error(agentId, threadId,
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
	private void handleStreamComplete(String agentId, String threadId) {
		log.info("Stream processing completed successfully for threadId: {}", threadId);
		StreamContext context = streamContextMap.remove(threadId);
		if (context != null && !context.isCleaned() && context.getSink() != null) {
			if (context.getSink().currentSubscriberCount() > 0) {
				context.getSink()
					.tryEmitNext(ServerSentEvent.builder(GraphNodeResponse.complete(agentId, threadId))
						.event(STREAM_EVENT_COMPLETE)
						.build());
				context.getSink().tryEmitComplete();
			}
			context.cleanup();
		}
	}

	private void handleStreamNodeOutput(String threadId, String agentId, StreamingOutput output) {
		StreamContext context = streamContextMap.get(threadId);
		// 检查是否已经停止处理
		if (context == null || context.getSink() == null) {
			log.debug("Stream processing already stopped for threadId: {}, skipping output", threadId);
			return;
		}
		String node = output.node();
		String chunk = output.chunk();

		if (RunnableConfig.AGENT_MODEL_NAME.equals(node)) {
			// 最终的结果使用reportnode进行展示即可
			node = "ReportGeneratorNode";
		}
		log.debug("Received Stream output: node={}, chunk={}", node, chunk);

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
			if (PlannerNode.class.getSimpleName().equals(node)) {
				multiTurnContextManager.appendPlannerChunk(threadId, chunk);
			}
			GraphNodeResponse response = GraphNodeResponse.builder()
				.agentId(agentId)
				.threadId(threadId)
				.nodeName(node)
				.text(chunk)
				.textType(TextType.MARK_DOWN)
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

	/**
	 * 处理工具调用，将工具调用的输入和输出写出到流中
	 */
	private void handleToolCall(String threadId, String toolName, String input, String output) {
		log.debug("handleToolCall: threadId={}, toolName={}, input={}, output={}", threadId, toolName, input, output);
		StreamContext context = streamContextMap.get(threadId);
		// 检查是否已经停止处理
		if (context == null || context.getSink() == null) {
			log.debug("Stream processing already stopped for threadId: {}, skipping tool call output", threadId);
			return;
		}

		try {
			ToolCallDTO toolCallDTO = new ToolCallDTO(toolName, input, output);
			// 创建 GraphNodeResponse
			GraphNodeResponse response = GraphNodeResponse.builder()
				.agentId(context.getAgentId())
				.threadId(threadId)
				.nodeName("ToolCallNode")
				.text(JsonUtil.getObjectMapper().writeValueAsString(toolCallDTO))
				.textType(TextType.JSON)
				.build();

			// 写出到流中
			Sinks.EmitResult result = context.getSink().tryEmitNext(ServerSentEvent.builder(response).build());

			if (result.isFailure()) {
				log.warn(
						"Failed to emit tool call data to sink for threadId: {}, result: {}. Stopping stream processing.",
						threadId, result);
				// 如果发送失败，停止处理
				stopStreamProcessing(threadId);
			}
		}
		catch (Exception e) {
			log.error("Error processing tool call: {}", e.getMessage(), e);
		}
	}
}
