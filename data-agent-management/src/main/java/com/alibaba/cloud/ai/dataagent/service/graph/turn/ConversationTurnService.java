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
package com.alibaba.cloud.ai.dataagent.service.graph.turn;

import com.alibaba.cloud.ai.dataagent.entity.*;
import com.alibaba.cloud.ai.dataagent.enums.TurnArtifactType;
import com.alibaba.cloud.ai.dataagent.enums.TurnStatus;
import com.alibaba.cloud.ai.dataagent.mapper.*;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.chat.SessionTitleService;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryEventType;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryOutboxService;
import com.alibaba.cloud.ai.dataagent.service.memory.shortterm.ConversationMemoryGateway;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the durable lifecycle of a user turn. UI messages and model memory are projections
 * of this record rather than independent sources of truth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationTurnService {

	private final ConversationTurnMapper turnMapper;

	private final TurnRunMapper runMapper;

	private final TurnArtifactMapper artifactMapper;

	private final ChatSessionMapper chatSessionMapper;

	private final ChatMessageMapper chatMessageMapper;

	private final MemoryOutboxService outboxService;

	private final ConversationMemoryGateway memoryGateway;

	private final SessionTitleService sessionTitleService;

	private final DataAgentProperties properties;

	@Transactional
	public String beginTurn(String conversationId, Integer agentId, Integer datasourceId, String runId, String rawQuery,
			boolean titleNeeded) {
		chatSessionMapper.lockBySessionId(conversationId);
		ChatSession session = chatSessionMapper.selectBySessionId(conversationId);
		if (session == null) {
			if (chatSessionMapper.selectAnyBySessionId(conversationId) != null) {
				throw new IllegalStateException("Conversation has been deleted: " + conversationId);
			}
			log.debug("Conversation {} is not a persisted chat session; skipping durable turn creation",
					conversationId);
			return null;
		}
		if (!agentId.equals(session.getAgentId())) {
			throw new IllegalArgumentException("Conversation does not belong to agent " + agentId);
		}
		String turnId = UUID.randomUUID().toString();
		ConversationTurn turn = ConversationTurn.builder()
			.id(turnId)
			.conversationId(conversationId)
			.agentId(agentId)
			.ownerId(properties.getMemory().isUserScopeEnabled() ? session.getUserId() : null)
			.acceptedRunId(runId)
			.datasourceId(datasourceId)
			.rawQuery(rawQuery.trim())
			.status(TurnStatus.RUNNING)
			.memoryEligible(false)
			.build();
		turnMapper.insert(turn);
		runMapper.insert(TurnRun.builder().runId(runId).turnId(turnId).status(TurnStatus.RUNNING).build());
		saveOrAttachUserMessage(conversationId, rawQuery, turnId, runId);
		chatSessionMapper.updateSessionTime(conversationId, LocalDateTime.now());
		if (titleNeeded) {
			sessionTitleService.scheduleTitleGeneration(conversationId, rawQuery);
		}
		return turnId;
	}

	@Transactional
	public TurnExecutionScope resumeTurn(String turnId, String runId, boolean rejectedPlan,
			Integer authenticatedAgentId, String requestedConversationId) {
		if (StringUtils.isBlank(runId)) {
			throw new IllegalArgumentException("Graph run ID is required");
		}
		if (authenticatedAgentId == null) {
			throw new IllegalArgumentException("Authenticated agent ID is required");
		}
		TurnRun run = runMapper.selectById(runId);
		String resolvedTurnId = StringUtils.defaultIfBlank(turnId, run != null ? run.getTurnId() : null);
		if (run == null || StringUtils.isBlank(resolvedTurnId) || !resolvedTurnId.equals(run.getTurnId())) {
			throw new IllegalArgumentException("Run does not belong to turn " + turnId);
		}
		ConversationTurn turn = lockTurnInActiveConversation(resolvedTurnId);
		if (turn == null) {
			throw new IllegalStateException("Turn or active conversation no longer exists: " + resolvedTurnId);
		}
		if (!runId.equals(turn.getAcceptedRunId())) {
			throw new IllegalArgumentException("Run does not belong to turn " + resolvedTurnId);
		}
		if (!authenticatedAgentId.equals(turn.getAgentId())) {
			throw new IllegalArgumentException("Turn does not belong to authenticated agent " + authenticatedAgentId);
		}
		if (StringUtils.isNotBlank(requestedConversationId)
				&& !requestedConversationId.equals(turn.getConversationId())) {
			throw new IllegalArgumentException("Turn does not belong to conversation " + requestedConversationId);
		}
		if (turnMapper.markRunning(resolvedTurnId, runId) != 1) {
			throw new IllegalStateException("Turn is no longer waiting for review: " + resolvedTurnId);
		}
		int runUpdated = rejectedPlan ? runMapper.incrementAttempt(runId) : runMapper.resume(runId);
		if (runUpdated != 1) {
			throw new IllegalStateException("Run is no longer waiting for review: " + runId);
		}
		return new TurnExecutionScope(resolvedTurnId, runId, turn.getConversationId(), turn.getAgentId(),
				turn.getDatasourceId(), turn.getOwnerId(), turn.getRawQuery());
	}

	@Transactional
	public void markWaitingReview(String turnId, String runId, String timelineJson) {
		if (StringUtils.isAnyBlank(turnId, runId)) {
			return;
		}
		ConversationTurn turn = lockTurnInActiveConversation(turnId);
		if (turn == null) {
			throw new IllegalStateException("Turn or active conversation no longer exists: " + turnId);
		}
		if (turnMapper.markWaitingReview(turnId, runId) != 1) {
			throw new IllegalStateException("Turn is no longer running: " + turnId);
		}
		if (runMapper.markWaitingReview(runId) != 1) {
			throw new IllegalStateException("Run is no longer running: " + runId);
		}
		storeArtifact(turnId, runId, TurnArtifactType.TIMELINE, timelineJson);
		saveTimelineMessage(turn.getConversationId(), turnId, runId, timelineJson);
	}

	@Transactional
	public void completeTurn(String turnId, String runId, TurnMemorySnapshot snapshot, String reportContent,
			String timelineJson) {
		if (StringUtils.isAnyBlank(turnId, runId)) {
			return;
		}
		ConversationTurn existing = lockTurnInActiveConversation(turnId);
		if (existing == null) {
			throw new IllegalStateException("Turn or active conversation no longer exists: " + turnId);
		}
		if (existing.getStatus() == TurnStatus.SUCCEEDED && runId.equals(existing.getAcceptedRunId())) {
			return;
		}
		if (existing.getStatus() == TurnStatus.FAILED || existing.getStatus() == TurnStatus.CANCELLED) {
			throw new IllegalStateException("Turn is already terminal: " + turnId);
		}
		String finalAnswer = StringUtils.defaultIfBlank(snapshot.getFinalAnswer(), reportContent);
		boolean memoryEligible = snapshot.hasVerifiedEvidence();
		String resultSummary = memoryEligible
				? bounded(snapshot.resultArtifactJson(), properties.getMemory().getMaxResultSummaryLength()) : null;
		LocalDateTime now = LocalDateTime.now();
		ConversationTurn completed = ConversationTurn.builder()
			.id(turnId)
			.acceptedRunId(runId)
			.datasourceId(existing.getDatasourceId())
			.canonicalQuery(StringUtils.defaultIfBlank(snapshot.getCanonicalQuery(), existing.getRawQuery()))
			.queryFrame(snapshot.queryFrameJson())
			.resultSummary(resultSummary)
			.finalAnswer(finalAnswer)
			.schemaFingerprint(snapshot.getSchemaFingerprint())
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(memoryEligible)
			.observedAt(now)
			.completedAt(now)
			.build();
		if (turnMapper.complete(completed) != 1) {
			throw new IllegalStateException("Turn is no longer active: " + turnId);
		}
		if (runMapper.markSucceeded(runId) != 1) {
			throw new IllegalStateException("Run is no longer active: " + runId);
		}
		storeArtifact(turnId, runId, TurnArtifactType.PLAN, snapshot.getPlannerJson());
		storeArtifact(turnId, runId, TurnArtifactType.SQL, snapshot.sqlArtifactJson());
		storeArtifact(turnId, runId, TurnArtifactType.RESULT, snapshot.resultArtifactJson());
		storeArtifact(turnId, runId, TurnArtifactType.REPORT, finalAnswer);
		storeArtifact(turnId, runId, TurnArtifactType.TIMELINE, timelineJson);
		saveTimelineMessage(existing.getConversationId(), turnId, runId, timelineJson);
		if (StringUtils.isNotBlank(finalAnswer)) {
			saveChatMessage(existing.getConversationId(), "assistant", finalAnswer, "text", turnId, runId);
			memoryGateway.commitSuccessfulTurn(existing.getConversationId(), existing.getRawQuery(), finalAnswer);
			outboxService.enqueue("CONVERSATION_TURN", turnId, MemoryEventType.TURN_COMPLETED, null);
		}
		enqueueCheckpointRelease(runId);
	}

	@Transactional
	public void failTurn(String turnId, String runId, Throwable error, String timelineJson) {
		if (StringUtils.isAnyBlank(turnId, runId)) {
			return;
		}
		String message = error != null
				? StringUtils
					.abbreviate(StringUtils.defaultIfBlank(error.getMessage(), error.getClass().getSimpleName()), 4000)
				: "Unknown graph error";
		ConversationTurn turn = lockTurnInActiveConversation(turnId);
		if (turn == null) {
			log.debug("Ignoring failure for a deleted turn or conversation: {}", turnId);
			return;
		}
		if (turnMapper.markTerminal(turnId, runId, TurnStatus.FAILED) != 1) {
			log.debug("Ignoring failure for a stale or terminal turn: {}", turnId);
			return;
		}
		if (runMapper.markTerminal(runId, TurnStatus.FAILED, message) != 1) {
			throw new IllegalStateException("Run is no longer active: " + runId);
		}
		storeArtifact(turnId, runId, TurnArtifactType.TIMELINE, timelineJson);
		saveTimelineMessage(turn.getConversationId(), turnId, runId, timelineJson);
		saveChatMessage(turn.getConversationId(), "assistant", message, "error", turnId, runId);
		enqueueCheckpointRelease(runId);
	}

	@Transactional
	public void cancelTurn(String turnId, String runId, String timelineJson) {
		if (StringUtils.isAnyBlank(turnId, runId)) {
			return;
		}
		String reason = "Cancelled by user or disconnected client";
		ConversationTurn turn = lockTurnInActiveConversation(turnId);
		if (turn == null) {
			log.debug("Ignoring cancellation for a deleted turn or conversation: {}", turnId);
			return;
		}
		if (turnMapper.markTerminal(turnId, runId, TurnStatus.CANCELLED) != 1) {
			log.debug("Ignoring cancellation for a stale or terminal turn: {}", turnId);
			return;
		}
		if (runMapper.markTerminal(runId, TurnStatus.CANCELLED, reason) != 1) {
			throw new IllegalStateException("Run is no longer active: " + runId);
		}
		storeArtifact(turnId, runId, TurnArtifactType.TIMELINE, timelineJson);
		saveTimelineMessage(turn.getConversationId(), turnId, runId, timelineJson);
		saveChatMessage(turn.getConversationId(), "assistant", "用户已终止本次对话。", "warning", turnId, runId);
		enqueueCheckpointRelease(runId);
	}

	private void enqueueCheckpointRelease(String runId) {
		outboxService.enqueue("GRAPH_RUN", runId, MemoryEventType.GRAPH_CHECKPOINT_RELEASE, null);
	}

	/**
	 * Keeps every terminal transition in the same session -&gt; turn lock order as
	 * conversation deletion. This prevents a graph callback from deadlocking with the
	 * session cleanup transaction while it writes FK-backed artifacts and messages.
	 */
	private ConversationTurn lockTurnInActiveConversation(String turnId) {
		ConversationTurn candidate = turnMapper.selectById(turnId);
		if (candidate == null || StringUtils.isBlank(candidate.getConversationId())) {
			return null;
		}
		String conversationId = candidate.getConversationId();
		if (!conversationId.equals(chatSessionMapper.lockBySessionId(conversationId))) {
			return null;
		}
		ConversationTurn locked = turnMapper.selectByIdForUpdate(turnId);
		return locked != null && conversationId.equals(locked.getConversationId()) ? locked : null;
	}

	private void storeArtifact(String turnId, String runId, TurnArtifactType type, String content) {
		if (StringUtils.isBlank(content)) {
			return;
		}
		artifactMapper.deleteByType(turnId, runId, type);
		artifactMapper.insert(TurnArtifact.builder()
			.turnId(turnId)
			.runId(runId)
			.artifactType(type)
			.content(content)
			.contentHash(sha256(content))
			.build());
	}

	private void saveTimelineMessage(String conversationId, String turnId, String runId, String timelineJson) {
		if (StringUtils.isBlank(timelineJson)) {
			return;
		}
		saveChatMessage(conversationId, "assistant", timelineJson, "timeline", turnId, runId);
	}

	private void saveChatMessage(String conversationId, String role, String content, String messageType, String turnId,
			String runId) {
		if (StringUtils.isBlank(content)) {
			return;
		}
		chatMessageMapper.insert(ChatMessage.builder()
			.sessionId(conversationId)
			.role(role)
			.content(content)
			.messageType(messageType)
			.metadata(messageMetadata(turnId, runId))
			.build());
	}

	private void saveOrAttachUserMessage(String conversationId, String rawQuery, String turnId, String runId) {
		ChatMessage latest = chatMessageMapper.selectLatestBySessionId(conversationId);
		if (latest != null && "user".equals(latest.getRole()) && rawQuery.equals(latest.getContent())
				&& StringUtils.isBlank(latest.getMetadata()) && latest.getCreateTime() != null
				&& latest.getCreateTime().isAfter(LocalDateTime.now().minusMinutes(5))) {
			chatMessageMapper.updateMetadata(latest.getId(), messageMetadata(turnId, runId));
			return;
		}
		saveChatMessage(conversationId, "user", rawQuery, "text", turnId, runId);
	}

	private String messageMetadata(String turnId, String runId) {
		try {
			return JsonUtil.getObjectMapper().writeValueAsString(Map.of("turnId", turnId, "runId", runId));
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to serialize chat message metadata", e);
		}
	}

	private String bounded(String value, int maxLength) {
		return StringUtils.abbreviate(StringUtils.defaultString(value), Math.max(500, maxLength));
	}

	private String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to hash turn artifact", e);
		}
	}

}
