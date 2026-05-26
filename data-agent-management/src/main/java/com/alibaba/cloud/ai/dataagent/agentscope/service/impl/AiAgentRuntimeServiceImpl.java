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

import com.alibaba.cloud.ai.dataagent.agentscope.dto.AgentRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.runtime.AgentScopeMemoryFactory;
import com.alibaba.cloud.ai.dataagent.agentscope.runtime.AgentRuntimeEventPublisher;
import com.alibaba.cloud.ai.dataagent.agentscope.runtime.AgentRuntimeExtensionFactory;
import com.alibaba.cloud.ai.dataagent.agentscope.runtime.PreparedMemory;
import com.alibaba.cloud.ai.dataagent.agentscope.runtime.QueryClarifyService;
import com.alibaba.cloud.ai.dataagent.agentscope.runtime.QueryClarifyService.QueryClarifyAssessment;
import com.alibaba.cloud.ai.dataagent.agentscope.runtime.AgentScopeToolkitFactory;
import com.alibaba.cloud.ai.dataagent.agentscope.service.AgentScopeModelFactory;
import com.alibaba.cloud.ai.dataagent.agentscope.service.AgentService;
import com.alibaba.cloud.ai.dataagent.agentscope.session.AgentRuntimeRegistry;
import com.alibaba.cloud.ai.dataagent.agentscope.session.AgentScopeNativeSessionService;
import com.alibaba.cloud.ai.dataagent.agentscope.template.AgentRunContext;
import com.alibaba.cloud.ai.dataagent.agentscope.template.AgentRuntimeExtensions;
import com.alibaba.cloud.ai.dataagent.agentscope.template.ManagedAgent;
import com.alibaba.cloud.ai.dataagent.agentscope.template.ManagedAgentRegistry;
import com.alibaba.cloud.ai.dataagent.agentscope.vo.AgentResponse;
import com.alibaba.cloud.ai.dataagent.constant.AgentRuntimeConstant;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.entity.ChatMessage;
import com.alibaba.cloud.ai.dataagent.observability.AnswerTraceExplainStore;
import com.alibaba.cloud.ai.dataagent.observability.SessionTraceStore;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.DynamicModelFactory;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.ModelConfigDataService;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatMessageService;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
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
public class AiAgentRuntimeServiceImpl implements AgentService {

	private static final String RUNTIME_NODE_NAME = "AgentScopeRuntime";

	private static final String ANSWER_EXPLAIN_MESSAGE_TYPE = "answer-explain";

	private static final String STREAM_EVENT_MESSAGE = "message";

	private static final String ROOT_SPAN_NAME = "data-agent.agent.run";

	private static final String AGENT_STATUS_PUBLISHED = "published";

	private static final String AGENT_STATUS_OFFLINE = "offline";

	private final AgentRuntimeRegistry runtimeRegistry;

	private final ModelConfigDataService modelConfigDataService;

	private final DynamicModelFactory dynamicModelFactory;

	private final AgentScopeModelFactory agentScopeModelFactory;

	private final AgentScopeToolkitFactory agentScopeToolkitFactory;

	private final ManagedAgentRegistry managedAgentRegistry;

	private final AgentRuntimeExtensionFactory agentRuntimeExtensionFactory;

	private final AgentScopeMemoryFactory agentScopeMemoryFactory;

	private final com.alibaba.cloud.ai.dataagent.service.agent.AgentService agentService;

	@Qualifier("agentScopeTracer")
	private final Tracer tracer;

	private final AnswerTraceExplainStore answerTraceExplainStore;

	private final ChatSessionService chatSessionService;

	private final ChatMessageService chatMessageService;

	private final ObjectMapper objectMapper;

	private final QueryClarifyService queryClarifyService;

	private final AgentScopeNativeSessionService nativeSessionService;

	@Override
	public void graphStreamProcess(Sinks.Many<ServerSentEvent<AgentResponse>> sink, AgentRequest agentRequest) {
		initializeRuntimeRequest(agentRequest);
		String threadId = agentRequest.getThreadId();
		String runtimeRequestId = agentRequest.getRuntimeRequestId();
		StreamTextTracker streamTextTracker = new StreamTextTracker();
		runtimeRegistry.register(threadId, runtimeRequestId);
		AgentRuntimeEventPublisher eventPublisher = response -> {
			if (!runtimeRegistry.isActive(threadId, runtimeRequestId)) {
				return;
			}
			if (response != null && response.getTextType() == TextType.TEXT
					&& StringUtils.hasText(response.getText())) {
				streamTextTracker.record(response.getNodeName(), response.getText());
			}
			sink.tryEmitNext(ServerSentEvent.builder(response).event(STREAM_EVENT_MESSAGE).build());
		};

		Mono.fromCallable(() -> executeAgent(agentRequest, eventPublisher))
			.doFinally(signalType -> runtimeRegistry.finish(threadId, runtimeRequestId))
			.subscribeOn(Schedulers.boundedElastic())
			.subscribe(result -> emitSuccess(sink, agentRequest, result, streamTextTracker),
					error -> emitError(sink, agentRequest, error));
	}

	@Override
	public void stopStreamProcessing(String threadId, String runtimeRequestId) {
		runtimeRegistry.markCancelled(threadId, runtimeRequestId);
	}

	private void emitSuccess(Sinks.Many<ServerSentEvent<AgentResponse>> sink, AgentRequest request, String result,
			StreamTextTracker streamTextTracker) {
		String threadId = request.getThreadId();
		String runtimeRequestId = request.getRuntimeRequestId();
		if (!runtimeRegistry.isActive(threadId, runtimeRequestId)) {
			return;
		}
		if (shouldEmitFinalResponse(result, streamTextTracker)) {
			AgentResponse response = AgentResponse.builder()
				.agentId(request.getAgentId())
				.threadId(threadId)
				.nodeName(RUNTIME_NODE_NAME)
				.textType(TextType.TEXT)
				.text(result)
				.build();
			sink.tryEmitNext(ServerSentEvent.builder(response).event(STREAM_EVENT_MESSAGE).build());
		}
		sink.tryEmitNext(ServerSentEvent.builder(AgentResponse.complete(request.getAgentId(), threadId))
			.event(STREAM_EVENT_COMPLETE)
			.build());
		sink.tryEmitComplete();
	}

	private boolean shouldEmitFinalResponse(String result, StreamTextTracker streamTextTracker) {
		return StringUtils.hasText(result) && !streamTextTracker.containsFinalAnswer(result);
	}

	private void emitError(Sinks.Many<ServerSentEvent<AgentResponse>> sink, AgentRequest request, Throwable error) {
		String threadId = request.getThreadId();
		String runtimeRequestId = request.getRuntimeRequestId();
		if (runtimeRegistry.isCancelled(threadId, runtimeRequestId)) {
			log.info("AgentScope runtime cancelled, suppress error propagation. threadId={}, runtimeRequestId={}",
					threadId, runtimeRequestId);
			return;
		}
		log.error("AgentScope runtime failed, threadId={}", threadId, error);
		if (runtimeRegistry.isActive(threadId, runtimeRequestId)) {
			String message = error.getMessage() == null ? "AgentScope 运行失败。" : error.getMessage();
			sink.tryEmitNext(ServerSentEvent.builder(AgentResponse.error(request.getAgentId(), threadId, message))
				.event(STREAM_EVENT_ERROR)
				.build());
			sink.tryEmitComplete();
		}
	}

	private String executeAgent(AgentRequest request) {
		return executeAgent(request, null);
	}

	private void initializeRuntimeRequest(AgentRequest request) {
		if (!StringUtils.hasText(request.getThreadId())) {
			throw new IllegalArgumentException("threadId must not be empty");
		}
		if (!StringUtils.hasText(request.getRuntimeRequestId())) {
			request.setRuntimeRequestId(UUID.randomUUID().toString());
		}
	}

	private String executeAgent(AgentRequest request, AgentRuntimeEventPublisher eventPublisher) {
		runtimeRegistry.markRunning(request.getThreadId(), request.getRuntimeRequestId(), Thread.currentThread());
		answerTraceExplainStore.openScope(request);
		Span rootSpan = startRuntimeSpan(request);
		PreparedMemory preparedMemory = null;
		try {
			try (Scope ignored = rootSpan.makeCurrent()) {
				preparedMemory = agentScopeMemoryFactory.create(request);
				if (runtimeRegistry.isCancelled(request.getThreadId(), request.getRuntimeRequestId())) {
					rootSpan.setStatus(StatusCode.OK, "cancelled");
					return "";
				}
				boolean clarifyCheckEnabled = request.isClarifyCheckEnabled()
						|| StringUtils.hasText(request.getHumanFeedbackContent());
				QueryClarifyAssessment clarifyAssessment = queryClarifyService.assess(request.getQuery(),
						request.getHumanFeedbackContent(), clarifyCheckEnabled);
				answerTraceExplainStore.recordClarifyAssessment(request, clarifyAssessment);
				rootSpan.setAttribute("dataagent.query_clarify.enabled", clarifyCheckEnabled);
				rootSpan.setAttribute("dataagent.query_clarify.risk_level", clarifyAssessment.riskLevel().value());
				rootSpan.setAttribute("dataagent.query_clarify.blocked", clarifyAssessment.shouldBlockExecution());
				if (clarifyAssessment.shouldBlockExecution()) {
					return blockForClarification(request, eventPublisher, rootSpan, clarifyAssessment, preparedMemory);
				}
				Agent managedAgentConfig = resolveManagedAgent(request.getAgentId());
				ModelConfigDTO modelConfig = modelConfigDataService.getActiveConfigByType(ModelType.CHAT);
				validateModelConfig(modelConfig);
				Map<String, ToolCallback> toolCallbacks = agentScopeToolkitFactory
					.getToolCallbacks(request.getAgentId());
				Model model = agentScopeModelFactory.create(dynamicModelFactory.createChatModel(modelConfig),
						modelConfig.getModelName(), toolCallbacks);
				ManagedAgent managedAgent = managedAgentRegistry.getRequired();
				AgentRuntimeExtensions runtimeExtensions = agentRuntimeExtensionFactory.create(request, eventPublisher,
						toolCallbacks, preparedMemory);
				Msg response;
				try {
					response = managedAgent.run(new AgentRunContext(request.getAgentId(), request.getThreadId(), model,
							resolveManagedSystemPrompt(managedAgentConfig, request.getAgentId()),
							buildUserPrompt(request), AgentRuntimeConstant.AGENT_CALL_TIMEOUT, runtimeExtensions));
				}
				catch (RuntimeException ex) {
					if (runtimeRegistry.isCancelled(request.getThreadId(), request.getRuntimeRequestId())
							&& isInterruptedCancellation(ex)) {
						Thread.interrupted();
						rootSpan.setStatus(StatusCode.OK, "cancelled");
						log.info("Agent execution interrupted by cancellation, threadId={}, runtimeRequestId={}",
								request.getThreadId(), request.getRuntimeRequestId());
						return "";
					}
					throw ex;
				}
				if (runtimeRegistry.isCancelled(request.getThreadId(), request.getRuntimeRequestId())) {
					rootSpan.setStatus(StatusCode.OK, "cancelled");
					return "";
				}
				persistNativeMemorySafely(request, runtimeExtensions.memory());
				rootSpan.setStatus(StatusCode.OK);
				String answer = extractText(response);
				answerTraceExplainStore.recordFinalAnswer(answer);
				persistAnswerExplainSnapshot(request);
				mirrorExplainSummary(rootSpan, request);
				return answer;
			}
		}
		catch (RuntimeException ex) {
			recordRuntimeFailure(rootSpan, ex);
			throw ex;
		}
		finally {
			rootSpan.end();
			runtimeRegistry.clearRunning(request.getThreadId(), request.getRuntimeRequestId());
			answerTraceExplainStore.closeScope();
		}
	}

	private Span startRuntimeSpan(AgentRequest request) {
		Span span = tracer.spanBuilder(ROOT_SPAN_NAME).startSpan();
		span.setAttribute(SessionTraceStore.ATTR_THREAD_ID, request.getThreadId());
		span.setAttribute(SessionTraceStore.ATTR_RUNTIME_REQUEST_ID, request.getRuntimeRequestId());
		span.setAttribute(SessionTraceStore.ATTR_AGENT_ID, request.getAgentId() == null ? "" : request.getAgentId());
		span.setAttribute("dataagent.runtime.human_feedback", request.isHumanFeedback());
		return span;
	}

	private void recordRuntimeFailure(Span rootSpan, Throwable throwable) {
		if (rootSpan == null) {
			return;
		}
		rootSpan.setStatus(StatusCode.ERROR,
				throwable.getMessage() == null ? "runtime failed" : throwable.getMessage());
		rootSpan.recordException(throwable);
	}

	private String blockForClarification(AgentRequest request, AgentRuntimeEventPublisher eventPublisher, Span rootSpan,
			QueryClarifyAssessment clarifyAssessment, PreparedMemory preparedMemory) {
		String clarifyText = clarifyAssessment.userMessage();
		appendClarifyTurn(preparedMemory, request, clarifyText);
		persistNativeMemorySafely(request, preparedMemory == null ? null : preparedMemory.memory());
		answerTraceExplainStore.recordFinalAnswer(clarifyText);
		persistAnswerExplainSnapshot(request);
		mirrorExplainSummary(rootSpan, request);
		rootSpan.setStatus(StatusCode.OK, "clarify required");
		if (eventPublisher != null) {
			Map<String, Object> metadata = new LinkedHashMap<>(clarifyAssessment.toMetadata());
			metadata.put("originalQuery", request.getQuery());
			eventPublisher.publish(AgentResponse.builder()
				.agentId(request.getAgentId())
				.threadId(request.getThreadId())
				.nodeName(RUNTIME_NODE_NAME)
				.textType(TextType.TEXT)
				.text(clarifyText)
				.metadata(metadata)
				.build());
		}
		return clarifyText;
	}

	private void mirrorExplainSummary(Span rootSpan, AgentRequest request) {
		if (rootSpan == null || request == null) {
			return;
		}
		answerTraceExplainStore.getMirrorSummary(request.getThreadId(), request.getRuntimeRequestId())
			.ifPresent(summary -> {
				rootSpan.setAttribute("dataagent.answer.explain.available", true);
				rootSpan.setAttribute("dataagent.answer.explain.tool_step_count", summary.getToolStepCount());
				rootSpan.setAttribute("dataagent.answer.explain.relation_evidence_count",
						summary.getRelationEvidenceCount());
				rootSpan.setAttribute("dataagent.answer.explain.used_table_count", summary.getUsedTableCount());
				rootSpan.setAttribute("dataagent.answer.explain.used_column_count", summary.getUsedColumnCount());
				rootSpan.setAttribute("dataagent.answer.explain.knowledge_hit_count", summary.getKnowledgeHitCount());
				if (StringUtils.hasText(summary.getDatasource())) {
					rootSpan.setAttribute("dataagent.answer.explain.datasource", summary.getDatasource());
				}
			});
	}

	private void persistAnswerExplainSnapshot(AgentRequest request) {
		if (request == null || !StringUtils.hasText(request.getThreadId())
				|| !StringUtils.hasText(request.getRuntimeRequestId())) {
			return;
		}
		if (chatSessionService.findBySessionId(request.getThreadId()) == null) {
			return;
		}
		answerTraceExplainStore.getExplain(request.getThreadId(), request.getRuntimeRequestId()).ifPresent(explain -> {
			try {
				chatMessageService.saveMessage(ChatMessage.builder()
					.sessionId(request.getThreadId())
					.role("system")
					.content(objectMapper.writeValueAsString(explain))
					.messageType(ANSWER_EXPLAIN_MESSAGE_TYPE)
					.metadata(buildAnswerExplainMetadata(request))
					.build());
			}
			catch (Exception ex) {
				log.warn("Failed to persist answer explain snapshot. sessionId={}, runtimeRequestId={}",
						request.getThreadId(), request.getRuntimeRequestId(), ex);
			}
		});
	}

	private String buildAnswerExplainMetadata(AgentRequest request) throws Exception {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("kind", "answer-explain");
		metadata.put("runtimeRequestId", request.getRuntimeRequestId());
		metadata.put("visibility", "system-hidden");
		return objectMapper.writeValueAsString(metadata);
	}

	private void validateModelConfig(ModelConfigDTO modelConfig) {
		if (modelConfig == null) {
			throw new IllegalStateException("当前未配置可用的 CHAT 模型，请先在控制台完成配置。");
		}
		if (!StringUtils.hasText(modelConfig.getApiKey())) {
			throw new IllegalStateException("当前活动 CHAT 模型的 apiKey 为空。");
		}
		if (!StringUtils.hasText(modelConfig.getModelName())) {
			throw new IllegalStateException("当前活动 CHAT 模型的 modelName 为空。");
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

	private String buildUserPrompt(AgentRequest request) {
		return request.getQuery() == null ? "" : request.getQuery();
	}

	private void persistNativeMemorySafely(AgentRequest request, io.agentscope.core.memory.Memory memory) {
		if (!(memory instanceof InMemoryMemory inMemoryMemory) || request == null
				|| !StringUtils.hasText(request.getThreadId())) {
			return;
		}
		normalizeMemoryForPersistence(request, inMemoryMemory);
		try {
			nativeSessionService.saveMemory(inMemoryMemory, request.getThreadId());
		}
		catch (RuntimeException ex) {
			log.warn("Failed to persist AgentScope native memory. threadId={}, runtimeRequestId={}",
					request.getThreadId(), request.getRuntimeRequestId(), ex);
		}
	}

	private void appendClarifyTurn(PreparedMemory preparedMemory, AgentRequest request, String clarifyText) {
		if (preparedMemory == null || request == null) {
			return;
		}
		InMemoryMemory memory = preparedMemory.memory();
		if (memory == null) {
			return;
		}
		String userInput = resolvePersistedUserInput(request);
		if (StringUtils.hasText(userInput)) {
			memory.addMessage(Msg.builder().name("user").role(MsgRole.USER).textContent(userInput).build());
		}
		if (StringUtils.hasText(clarifyText)) {
			memory.addMessage(Msg.builder().name("assistant").role(MsgRole.ASSISTANT).textContent(clarifyText).build());
		}
	}

	private void normalizeMemoryForPersistence(AgentRequest request, InMemoryMemory memory) {
		if (request == null || memory == null || !StringUtils.hasText(request.getHumanFeedbackContent())
				|| !StringUtils.hasText(request.getQuery())) {
			return;
		}
		List<Msg> originalMessages = memory.getMessages();
		if (originalMessages == null || originalMessages.isEmpty()) {
			return;
		}
		List<Msg> normalizedMessages = new ArrayList<>(originalMessages);
		int replacementIndex = findReplayQueryIndex(normalizedMessages, request.getQuery());
		if (replacementIndex < 0) {
			return;
		}
		Msg replayMessage = normalizedMessages.get(replacementIndex);
		normalizedMessages.set(replacementIndex,
				Msg.builder()
					.name(resolveMsgName(replayMessage, "user"))
					.role(MsgRole.USER)
					.textContent(request.getHumanFeedbackContent())
					.build());
		if (Objects.equals(originalMessages, normalizedMessages)) {
			return;
		}
		memory.clear();
		normalizedMessages.forEach(memory::addMessage);
	}

	private int findReplayQueryIndex(List<Msg> messages, String originalQuery) {
		int firstMatchIndex = -1;
		for (int i = 0; i < messages.size(); i++) {
			Msg message = messages.get(i);
			if (message == null || message.getRole() != MsgRole.USER) {
				continue;
			}
			if (!Objects.equals(message.getTextContent(), originalQuery)) {
				continue;
			}
			if (firstMatchIndex < 0) {
				firstMatchIndex = i;
				continue;
			}
			return i;
		}
		return -1;
	}

	private String resolveMsgName(Msg message, String fallback) {
		if (message != null && StringUtils.hasText(message.getName())) {
			return message.getName();
		}
		return fallback;
	}

	private String resolvePersistedUserInput(AgentRequest request) {
		if (request == null) {
			return null;
		}
		if (StringUtils.hasText(request.getHumanFeedbackContent())) {
			return request.getHumanFeedbackContent();
		}
		return request.getQuery();
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

		private final Map<String, String> lastTextByNode = new LinkedHashMap<>();

		synchronized void record(String nodeName, String text) {
			if (!StringUtils.hasText(text)) {
				return;
			}
			String normalizedNodeName = StringUtils.hasText(nodeName) ? nodeName : "";
			accumulatedByNode.computeIfAbsent(normalizedNodeName, key -> new StringBuilder()).append(text);
			lastTextByNode.put(normalizedNodeName, text);
		}

		synchronized boolean containsFinalAnswer(String candidate) {
			if (!StringUtils.hasText(candidate)) {
				return false;
			}
			String normalizedCandidate = normalize(candidate);
			if (!StringUtils.hasText(normalizedCandidate)) {
				return false;
			}
			for (StringBuilder accumulated : accumulatedByNode.values()) {
				String normalizedAccumulated = normalize(accumulated.toString());
				if (matchesFinalAnswer(normalizedAccumulated, normalizedCandidate)) {
					return true;
				}
			}
			for (String lastText : lastTextByNode.values()) {
				String normalizedLastText = normalize(lastText);
				if (matchesFinalAnswer(normalizedLastText, normalizedCandidate)) {
					return true;
				}
			}
			return false;
		}

		private boolean matchesFinalAnswer(String existingText, String candidate) {
			if (!StringUtils.hasText(existingText) || !StringUtils.hasText(candidate)) {
				return false;
			}
			return existingText.equals(candidate) || existingText.endsWith(candidate)
					|| candidate.endsWith(existingText);
		}

		private String normalize(String text) {
			if (!StringUtils.hasText(text)) {
				return "";
			}
			return text.replace("\r\n", "\n")
				.replace('\r', '\n')
				.replaceAll("[ \\t\\x0B\\f]+", " ")
				.replaceAll(" *\\n *", "\n")
				.trim();
		}

	}

}
