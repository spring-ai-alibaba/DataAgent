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
package com.alibaba.cloud.ai.dataagent.agentscope.service.impl;

import com.alibaba.cloud.ai.dataagent.agentscope.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.runtime.AgentRuntimeEventPublisher;
import com.alibaba.cloud.ai.dataagent.agentscope.runtime.AgentRuntimeExtensionFactory;
import com.alibaba.cloud.ai.dataagent.agentscope.runtime.AgentScopeToolkitFactory;
import com.alibaba.cloud.ai.dataagent.agentscope.service.AgentScopeModelFactory;
import com.alibaba.cloud.ai.dataagent.agentscope.service.GraphService;
import com.alibaba.cloud.ai.dataagent.agentscope.session.AgentSessionRegistry;
import com.alibaba.cloud.ai.dataagent.agentscope.template.AgentRunContext;
import com.alibaba.cloud.ai.dataagent.agentscope.template.AgentRuntimeExtensions;
import com.alibaba.cloud.ai.dataagent.agentscope.template.ManagedAgent;
import com.alibaba.cloud.ai.dataagent.agentscope.template.ManagedAgentRegistry;
import com.alibaba.cloud.ai.dataagent.agentscope.vo.GraphNodeResponse;
import com.alibaba.cloud.ai.dataagent.constant.AgentRuntimeConstant;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.service.agent.AgentService;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.DynamicModelFactory;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.ModelConfigDataService;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.Model;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_COMPLETE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_ERROR;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentRuntimeServiceImpl implements GraphService {

	private static final String RUNTIME_NODE_NAME = "AgentScopeRuntime";

	private static final String STREAM_EVENT_MESSAGE = "message";

	private static final String AGENT_STATUS_PUBLISHED = "published";

	private static final String AGENT_STATUS_OFFLINE = "offline";

	private final AgentSessionRegistry sessionRegistry;

	private final ModelConfigDataService modelConfigDataService;

	private final DynamicModelFactory dynamicModelFactory;

	private final AgentScopeModelFactory agentScopeModelFactory;

	private final AgentScopeToolkitFactory agentScopeToolkitFactory;

	private final ManagedAgentRegistry managedAgentRegistry;

	private final AgentRuntimeExtensionFactory agentRuntimeExtensionFactory;

	private final AgentService agentService;

	@Override
	public String nl2sql(String naturalQuery, String agentId) {
		log.info("NL2SQL runtime invoked for agentId={}", agentId);
		GraphRequest request = GraphRequest.builder().agentId(agentId).query(naturalQuery).nl2sqlOnly(true).build();
		initializeRuntimeRequest(request);
		sessionRegistry.register(request.getThreadId(), request.getRuntimeRequestId());
		try {
			return executeAgent(request);
		}
		finally {
			sessionRegistry.finish(request.getThreadId(), request.getRuntimeRequestId());
		}
	}

	@Override
	public void graphStreamProcess(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest graphRequest) {
		initializeRuntimeRequest(graphRequest);
		String threadId = graphRequest.getThreadId();
		String runtimeRequestId = graphRequest.getRuntimeRequestId();
		StreamTextTracker streamTextTracker = new StreamTextTracker();
		sessionRegistry.register(threadId, runtimeRequestId);
		AgentRuntimeEventPublisher eventPublisher = response -> {
			if (!sessionRegistry.isActive(threadId, runtimeRequestId)) {
				return;
			}
			if (response != null && response.getTextType() == TextType.TEXT
					&& StringUtils.hasText(response.getText())) {
				streamTextTracker.record(response.getNodeName(), response.getText());
			}
			sink.tryEmitNext(ServerSentEvent.builder(response).event(STREAM_EVENT_MESSAGE).build());
		};

		Mono.fromCallable(() -> executeAgent(graphRequest, eventPublisher))
			.doFinally(signalType -> sessionRegistry.finish(threadId, runtimeRequestId))
			.subscribeOn(Schedulers.boundedElastic())
			.subscribe(result -> emitSuccess(sink, graphRequest, result, streamTextTracker),
					error -> emitError(sink, graphRequest, error));
	}

	@Override
	public void stopStreamProcessing(String threadId, String runtimeRequestId) {
		sessionRegistry.markCancelled(threadId, runtimeRequestId);
	}

	private void emitSuccess(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest request, String result,
			StreamTextTracker streamTextTracker) {
		String threadId = request.getThreadId();
		String runtimeRequestId = request.getRuntimeRequestId();
		if (!sessionRegistry.isActive(threadId, runtimeRequestId)) {
			return;
		}
		if (shouldEmitFinalResponse(result, streamTextTracker)) {
			GraphNodeResponse response = GraphNodeResponse.builder()
				.agentId(request.getAgentId())
				.threadId(threadId)
				.nodeName(RUNTIME_NODE_NAME)
				.textType(TextType.TEXT)
				.text(result)
				.build();
			sink.tryEmitNext(ServerSentEvent.builder(response).event(STREAM_EVENT_MESSAGE).build());
		}
		sink.tryEmitNext(ServerSentEvent.builder(GraphNodeResponse.complete(request.getAgentId(), threadId))
			.event(STREAM_EVENT_COMPLETE)
			.build());
		sink.tryEmitComplete();
	}

	private boolean shouldEmitFinalResponse(String result, StreamTextTracker streamTextTracker) {
		return StringUtils.hasText(result) && !streamTextTracker.matchesAnyNodeAccumulation(result);
	}

	private void emitError(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest request, Throwable error) {
		String threadId = request.getThreadId();
		String runtimeRequestId = request.getRuntimeRequestId();
		if (sessionRegistry.isCancelled(threadId, runtimeRequestId)) {
			log.info("AgentScope runtime cancelled, suppress error propagation. threadId={}, runtimeRequestId={}",
					threadId, runtimeRequestId);
			return;
		}
		log.error("AgentScope runtime failed, threadId={}", threadId, error);
		if (sessionRegistry.isActive(threadId, runtimeRequestId)) {
			String message = error.getMessage() == null ? "AgentScope runtime failed." : error.getMessage();
			sink.tryEmitNext(ServerSentEvent.builder(GraphNodeResponse.error(request.getAgentId(), threadId, message))
				.event(STREAM_EVENT_ERROR)
				.build());
			sink.tryEmitComplete();
		}
	}

	private String executeAgent(GraphRequest request) {
		return executeAgent(request, null);
	}

	private void initializeRuntimeRequest(GraphRequest request) {
		if (!StringUtils.hasText(request.getThreadId())) {
			request.setThreadId(UUID.randomUUID().toString());
		}
		if (!StringUtils.hasText(request.getRuntimeRequestId())) {
			request.setRuntimeRequestId(UUID.randomUUID().toString());
		}
	}

	private String executeAgent(GraphRequest request, AgentRuntimeEventPublisher eventPublisher) {
		sessionRegistry.markRunning(request.getThreadId(), request.getRuntimeRequestId(), Thread.currentThread());
		try {
			if (sessionRegistry.isCancelled(request.getThreadId(), request.getRuntimeRequestId())) {
				return "";
			}
			Agent managedAgentConfig = resolveManagedAgent(request.getAgentId());
			ModelConfigDTO modelConfig = modelConfigDataService.getActiveConfigByType(ModelType.CHAT);
			validateModelConfig(modelConfig);
			Map<String, ToolCallback> toolCallbacks = agentScopeToolkitFactory.getToolCallbacks(request.getAgentId());
			Model model = agentScopeModelFactory.create(dynamicModelFactory.createChatModel(modelConfig),
					modelConfig.getModelName(), toolCallbacks);
			ManagedAgent managedAgent = managedAgentRegistry.getRequired();
			AgentRuntimeExtensions runtimeExtensions = agentRuntimeExtensionFactory.create(request, eventPublisher,
					toolCallbacks);
			Msg response;
			try {
				response = managedAgent.run(new AgentRunContext(request.getAgentId(), request.getThreadId(), model,
						resolveManagedSystemPrompt(managedAgentConfig, request.getAgentId()), buildUserPrompt(request),
						AgentRuntimeConstant.AGENT_CALL_TIMEOUT, runtimeExtensions));
			}
			catch (RuntimeException ex) {
				if (sessionRegistry.isCancelled(request.getThreadId(), request.getRuntimeRequestId())
						&& isInterruptedCancellation(ex)) {
					Thread.interrupted();
					log.info("Agent execution interrupted by cancellation, threadId={}, runtimeRequestId={}",
							request.getThreadId(), request.getRuntimeRequestId());
					return "";
				}
				throw ex;
			}
			if (sessionRegistry.isCancelled(request.getThreadId(), request.getRuntimeRequestId())) {
				return "";
			}
			return extractText(response);
		}
		finally {
			sessionRegistry.clearRunning(request.getThreadId(), request.getRuntimeRequestId());
		}
	}

	private void validateModelConfig(ModelConfigDTO modelConfig) {
		if (modelConfig == null) {
			throw new IllegalStateException("No active CHAT model configured. Please configure it in the dashboard.");
		}
		if (!StringUtils.hasText(modelConfig.getApiKey())) {
			throw new IllegalStateException("Active CHAT model apiKey is empty.");
		}
		if (!StringUtils.hasText(modelConfig.getModelName())) {
			throw new IllegalStateException("Active CHAT model modelName is empty.");
		}
	}

	private Agent resolveManagedAgent(String requestAgentId) {
		Long agentId = parseAgentId(requestAgentId);
		if (agentId == null) {
			return null;
		}
		Agent agent = agentService.findById(agentId);
		validateAgentStatus(agent, requestAgentId);
		return agent;
	}

	private String resolveManagedSystemPrompt(Agent agent, String requestAgentId) {
		if (agent == null || !StringUtils.hasText(agent.getPrompt())) {
			log.info("No agent prompt found, keep CommonAgent system prompt empty. agentId={}", requestAgentId);
			return "";
		}
		log.info("Using agent prompt from base setting. agentId={}", requestAgentId);
		return agent.getPrompt();
	}

	private String buildUserPrompt(GraphRequest request) {
		return request.getQuery() == null ? "" : request.getQuery();
	}

	private void validateAgentStatus(Agent agent, String requestAgentId) {
		if (agent == null) {
			return;
		}
		String status = agent.getStatus();
		if (!StringUtils.hasText(status) || AGENT_STATUS_PUBLISHED.equalsIgnoreCase(status)
				|| "draft".equalsIgnoreCase(status)) {
			return;
		}
		if (AGENT_STATUS_OFFLINE.equalsIgnoreCase(status)) {
			throw new IllegalStateException(
					"Agent %s is offline and cannot be run. Current status: %s".formatted(requestAgentId, status));
		}
		String resolvedStatus = StringUtils.hasText(status) ? status : "unknown";
		throw new IllegalStateException(
				"Agent %s cannot be run. Current status: %s".formatted(requestAgentId, resolvedStatus));
	}

	private Long parseAgentId(String agentId) {
		if (!StringUtils.hasText(agentId)) {
			return null;
		}
		try {
			return Long.valueOf(agentId);
		}
		catch (NumberFormatException ex) {
			log.warn("Agent id is not numeric, skip agent-specific prompt lookup. agentId={}", agentId);
			return null;
		}
	}

	private String extractText(Msg response) {
		if (response == null || !StringUtils.hasText(response.getTextContent())) {
			return "AgentScope returned an empty response.";
		}
		return response.getTextContent();
	}

	private boolean isInterruptedCancellation(Throwable throwable) {
		Throwable current = Exceptions.unwrap(throwable);
		while (current != null) {
			if (current instanceof InterruptedException || current instanceof CancellationException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private static final class StreamTextTracker {

		private final Map<String, StringBuilder> accumulatedByNode = new LinkedHashMap<>();

		synchronized void record(String nodeName, String text) {
			if (!StringUtils.hasText(text)) {
				return;
			}
			accumulatedByNode.computeIfAbsent(nodeName, key -> new StringBuilder()).append(text);
		}

		synchronized boolean matchesAnyNodeAccumulation(String candidate) {
			if (!StringUtils.hasText(candidate)) {
				return false;
			}
			for (StringBuilder accumulated : accumulatedByNode.values()) {
				if (candidate.equals(accumulated.toString())) {
					return true;
				}
			}
			return false;
		}

	}

}
