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
package com.alibaba.cloud.ai.dataagent.agent.service.impl;

import com.alibaba.cloud.ai.dataagent.agent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.agent.runtime.AgentRuntimeEventPublisher;
import com.alibaba.cloud.ai.dataagent.agent.runtime.AgentRuntimeExtensionFactory;
import com.alibaba.cloud.ai.dataagent.agent.service.AgentScopeModelFactory;
import com.alibaba.cloud.ai.dataagent.agent.service.GraphService;
import com.alibaba.cloud.ai.dataagent.agent.session.AgentSessionRegistry;
import com.alibaba.cloud.ai.dataagent.agent.template.AgentRunContext;
import com.alibaba.cloud.ai.dataagent.agent.template.AgentRuntimeExtensions;
import com.alibaba.cloud.ai.dataagent.agent.template.CommonAgent;
import com.alibaba.cloud.ai.dataagent.agent.template.ManagedAgent;
import com.alibaba.cloud.ai.dataagent.agent.template.ManagedAgentRegistry;
import com.alibaba.cloud.ai.dataagent.agent.vo.GraphNodeResponse;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.management.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.management.entity.Agent;
import com.alibaba.cloud.ai.dataagent.management.entity.UserPromptConfig;
import com.alibaba.cloud.ai.dataagent.management.service.agent.AgentService;
import com.alibaba.cloud.ai.dataagent.management.service.aimodelconfig.DynamicModelFactory;
import com.alibaba.cloud.ai.dataagent.management.service.aimodelconfig.ModelConfigDataService;
import com.alibaba.cloud.ai.dataagent.management.service.prompt.UserPromptService;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.Model;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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

	private static final Duration AGENT_CALL_TIMEOUT = Duration.ofSeconds(120);

	private static final String SCENE_PLANNER = "planner";

	private static final String SCENE_SQL_GENERATOR = "sql-generator";

	private static final String AGENT_STATUS_PUBLISHED = "published";

	private final AgentSessionRegistry sessionRegistry;

	private final ModelConfigDataService modelConfigDataService;

	private final DynamicModelFactory dynamicModelFactory;

	private final AgentScopeModelFactory agentScopeModelFactory;

	private final ManagedAgentRegistry managedAgentRegistry;

	private final AgentRuntimeExtensionFactory agentRuntimeExtensionFactory;

	private final AgentService agentService;

	private final UserPromptService userPromptService;

	@Override
	public String nl2sql(String naturalQuery, String agentId) {
		log.info("NL2SQL runtime invoked for agentId={}", agentId);
		GraphRequest request = GraphRequest.builder()
			.agentId(agentId)
			.agentType(resolveAgentType(agentId, null))
			.query(naturalQuery)
			.scene(SCENE_SQL_GENERATOR)
			.nl2sqlOnly(true)
			.build();
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
		sessionRegistry.register(threadId, runtimeRequestId);
		AgentRuntimeEventPublisher eventPublisher = response -> {
			if (!sessionRegistry.isActive(threadId, runtimeRequestId)) {
				return;
			}
			sink.tryEmitNext(ServerSentEvent.builder(response).event(STREAM_EVENT_MESSAGE).build());
		};

		Mono.fromCallable(() -> executeAgent(graphRequest, eventPublisher))
			.doFinally(signalType -> sessionRegistry.finish(threadId, runtimeRequestId))
			.subscribeOn(Schedulers.boundedElastic())
			.subscribe(result -> emitSuccess(sink, graphRequest, result), error -> emitError(sink, graphRequest, error));
	}

	@Override
	public void stopStreamProcessing(String threadId, String runtimeRequestId) {
		sessionRegistry.markCancelled(threadId, runtimeRequestId);
	}

	private void emitSuccess(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest request, String result) {
		String threadId = request.getThreadId();
		String runtimeRequestId = request.getRuntimeRequestId();
		if (!sessionRegistry.isActive(threadId, runtimeRequestId)) {
			return;
		}
		GraphNodeResponse response = GraphNodeResponse.builder()
			.agentId(request.getAgentId())
			.threadId(threadId)
			.nodeName(RUNTIME_NODE_NAME)
			.textType(TextType.TEXT)
			.text(result)
			.build();
		sink.tryEmitNext(ServerSentEvent.builder(response).event(STREAM_EVENT_MESSAGE).build());
		sink.tryEmitNext(ServerSentEvent
			.builder(GraphNodeResponse.complete(request.getAgentId(), threadId))
			.event(STREAM_EVENT_COMPLETE)
			.build());
		sink.tryEmitComplete();
	}

	private void emitError(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest request, Throwable error) {
		String threadId = request.getThreadId();
		String runtimeRequestId = request.getRuntimeRequestId();
		log.error("AgentScope runtime failed, threadId={}", threadId, error);
		if (sessionRegistry.isCancelled(threadId, runtimeRequestId)) {
			return;
		}
		if (sessionRegistry.isActive(threadId, runtimeRequestId)) {
			String message = error.getMessage() == null ? "AgentScope runtime failed." : error.getMessage();
			sink.tryEmitNext(ServerSentEvent
				.builder(GraphNodeResponse.error(request.getAgentId(), threadId, message))
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
			ModelConfigDTO modelConfig = modelConfigDataService.getActiveConfigByType(ModelType.CHAT);
			validateModelConfig(modelConfig);
			String agentType = resolveAgentType(request.getAgentId(), request.getAgentType());
			String scene = resolveScene(request);
			request.setAgentType(agentType);
			request.setScene(scene);

			Model model = agentScopeModelFactory.create(dynamicModelFactory.createChatModel(modelConfig),
					modelConfig.getModelName());
			ManagedAgent managedAgent = managedAgentRegistry.getRequired(agentType);
			AgentRuntimeExtensions runtimeExtensions = agentRuntimeExtensionFactory.create(request, agentType, scene,
					eventPublisher);
			Msg response = managedAgent.run(new AgentRunContext(request.getAgentId(), agentType, request.getThreadId(), model,
					resolveManagedSystemPrompt(agentType, scene, request.getAgentId()), buildUserPrompt(request),
					AGENT_CALL_TIMEOUT, runtimeExtensions));
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

	private String resolveManagedSystemPrompt(String agentType, String scene, String requestAgentId) {
		Long agentId = parseAgentId(requestAgentId);
		UserPromptConfig activeConfig = resolveActivePromptConfig(agentType, agentId);
		if (activeConfig != null && StringUtils.hasText(activeConfig.getSystemPrompt())) {
			log.info("Using managed prompt config, agentType={}, runtimeScene={}, agentId={}, configId={}", agentType,
					scene, agentId, activeConfig.getId());
			return activeConfig.getSystemPrompt();
		}
		log.info(
				"No managed prompt config found, keep CommonAgent system prompt empty. agentType={}, runtimeScene={}, agentId={}",
				agentType, scene, agentId);
		return "";
	}

	private String buildUserPrompt(GraphRequest request) {
		return request.getQuery() == null ? "" : request.getQuery();
	}

	private UserPromptConfig resolveActivePromptConfig(String agentType, Long agentId) {
		UserPromptConfig scopedConfig = userPromptService.getActiveConfig(agentType, agentId);
		if (scopedConfig != null) {
			return scopedConfig;
		}
		if (agentId != null) {
			return userPromptService.getActiveConfig(agentType, null);
		}
		return null;
	}

	private String resolveScene(GraphRequest request) {
		if (StringUtils.hasText(request.getScene())) {
			return request.getScene();
		}
		return request.isNl2sqlOnly() ? SCENE_SQL_GENERATOR : SCENE_PLANNER;
	}

	private String resolveAgentType(String agentId, String requestAgentType) {
		Long numericAgentId = parseAgentId(agentId);
		Agent agent = null;
		if (numericAgentId != null) {
			agent = agentService.findById(numericAgentId);
			validateAgentStatus(agent, agentId);
		}
		if (StringUtils.hasText(requestAgentType)) {
			return requestAgentType;
		}
		if (agent != null && StringUtils.hasText(agent.getAgentType())) {
			return agent.getAgentType();
		}
		return CommonAgent.AGENT_TYPE;
	}

	private void validateAgentStatus(Agent agent, String requestAgentId) {
		if (agent == null) {
			return;
		}
		String status = agent.getStatus();
		if (AGENT_STATUS_PUBLISHED.equalsIgnoreCase(status)) {
			return;
		}
		String resolvedStatus = StringUtils.hasText(status) ? status : "unknown";
		throw new IllegalStateException(
				"Agent %s is not published and cannot be run. Current status: %s".formatted(requestAgentId, resolvedStatus));
	}

	private Long parseAgentId(String agentId) {
		if (!StringUtils.hasText(agentId)) {
			return null;
		}
		try {
			return Long.valueOf(agentId);
		}
		catch (NumberFormatException ex) {
			log.warn("Agent id is not numeric, fallback to global prompt config. agentId={}", agentId);
			return null;
		}
	}

	private String extractText(Msg response) {
		if (response == null || !StringUtils.hasText(response.getTextContent())) {
			return "AgentScope returned an empty response.";
		}
		return response.getTextContent();
	}

}
