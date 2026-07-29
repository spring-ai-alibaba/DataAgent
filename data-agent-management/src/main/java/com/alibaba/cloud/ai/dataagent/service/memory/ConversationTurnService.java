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
package com.alibaba.cloud.ai.dataagent.service.memory;

import com.alibaba.cloud.ai.dataagent.entity.*;
import com.alibaba.cloud.ai.dataagent.enums.TurnArtifactType;
import com.alibaba.cloud.ai.dataagent.enums.TurnStatus;
import com.alibaba.cloud.ai.dataagent.mapper.*;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.chat.SessionTitleService;
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

	private final SessionTitleService sessionTitleService;

	private final DataAgentProperties properties;

	@Transactional
	public String beginTurn(String conversationId, Integer agentId, String runId, String rawQuery, boolean titleNeeded) {
		chatSessionMapper.lockBySessionId(conversationId);
		ChatSession session = chatSessionMapper.selectBySessionId(conversationId);
		if (session == null) {
			log.debug("Conversation {} is not a persisted chat session; skipping durable turn creation", conversationId);
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
			.ownerId(session.getUserId())
			.acceptedRunId(runId)
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
	public String resumeTurn(String turnId, String runId, boolean rejectedPlan) {
		if (StringUtils.isBlank(runId)) {
			throw new IllegalArgumentException("Graph run ID is required");
		}
		TurnRun run = runMapper.selectById(runId);
		String resolvedTurnId = StringUtils.defaultIfBlank(turnId, run != null ? run.getTurnId() : null);
		if (run == null || StringUtils.isBlank(resolvedTurnId) || !resolvedTurnId.equals(run.getTurnId())) {
			throw new IllegalArgumentException("Run does not belong to turn " + turnId);
		}
		if (turnMapper.markRunning(resolvedTurnId, runId) != 1) {
			throw new IllegalStateException("Turn is no longer waiting for review: " + resolvedTurnId);
		}
		int runUpdated = rejectedPlan ? runMapper.incrementAttempt(runId) : runMapper.resume(runId);
		if (runUpdated != 1) {
			throw new IllegalStateException("Run is no longer waiting for review: " + runId);
		}
		return resolvedTurnId;
	}

	@Transactional
	public void markWaitingReview(String turnId, String runId, String timelineJson) {
		if (StringUtils.isAnyBlank(turnId, runId)) {
			return;
		}
		if (turnMapper.markWaitingReview(turnId, runId) != 1) {
			throw new IllegalStateException("Turn is no longer running: " + turnId);
		}
		if (runMapper.markWaitingReview(runId) != 1) {
			throw new IllegalStateException("Run is no longer running: " + runId);
		}
		storeArtifact(turnId, runId, TurnArtifactType.TIMELINE, timelineJson);
		saveTimelineMessage(turnId, runId, timelineJson);
	}

	@Transactional
	public void completeTurn(String turnId, String runId, TurnMemorySnapshot snapshot, String reportContent,
			String timelineJson) {
		if (StringUtils.isAnyBlank(turnId, runId)) {
			return;
		}
		ConversationTurn existing = turnMapper.selectById(turnId);
		if (existing == null) {
			throw new IllegalStateException("Turn no longer exists: " + turnId);
		}
		if (existing.getStatus() == TurnStatus.SUCCEEDED && runId.equals(existing.getAcceptedRunId())) {
			return;
		}
		if (existing.getStatus() == TurnStatus.FAILED || existing.getStatus() == TurnStatus.CANCELLED) {
			throw new IllegalStateException("Turn is already terminal: " + turnId);
		}
		String finalAnswer = StringUtils.defaultIfBlank(snapshot.getFinalAnswer(), reportContent);
		String resultSummary = bounded(StringUtils.defaultIfBlank(finalAnswer, snapshot.resultArtifactJson()),
				properties.getMemory().getMaxResultSummaryLength());
		boolean memoryEligible = snapshot.hasVerifiedEvidence(reportContent);
		LocalDateTime now = LocalDateTime.now();
		ConversationTurn completed = ConversationTurn.builder()
			.id(turnId)
			.acceptedRunId(runId)
			.datasourceId(snapshot.getDatasourceId())
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
		saveTimelineMessage(turnId, runId, timelineJson);
		if (StringUtils.isNotBlank(snapshot.getFinalAnswer())) {
			saveChatMessage(existing.getConversationId(), "assistant", snapshot.getFinalAnswer(), "text", turnId, runId);
		}
		if (memoryEligible) {
			outboxService.enqueue("CONVERSATION_TURN", turnId, MemoryEventType.TURN_SUCCEEDED, null);
		}
	}

	@Transactional
	public void failTurn(String turnId, String runId, Throwable error, String timelineJson) {
		if (StringUtils.isAnyBlank(turnId, runId)) {
			return;
		}
		String message = error != null
				? StringUtils.abbreviate(StringUtils.defaultIfBlank(error.getMessage(), error.getClass().getSimpleName()),
						4000)
				: "Unknown graph error";
		if (turnMapper.markTerminal(turnId, runId, TurnStatus.FAILED) != 1) {
			log.debug("Ignoring failure for a stale or terminal turn: {}", turnId);
			return;
		}
		if (runMapper.markTerminal(runId, TurnStatus.FAILED, message) != 1) {
			throw new IllegalStateException("Run is no longer active: " + runId);
		}
		storeArtifact(turnId, runId, TurnArtifactType.TIMELINE, timelineJson);
		ConversationTurn turn = turnMapper.selectById(turnId);
		if (turn != null) {
			saveTimelineMessage(turnId, runId, timelineJson);
			saveChatMessage(turn.getConversationId(), "assistant", message, "error", turnId, runId);
		}
	}

	@Transactional
	public void cancelTurn(String turnId, String runId, String timelineJson) {
		if (StringUtils.isAnyBlank(turnId, runId)) {
			return;
		}
		String reason = "Cancelled by user or disconnected client";
		if (turnMapper.markTerminal(turnId, runId, TurnStatus.CANCELLED) != 1) {
			log.debug("Ignoring cancellation for a stale or terminal turn: {}", turnId);
			return;
		}
		if (runMapper.markTerminal(runId, TurnStatus.CANCELLED, reason) != 1) {
			throw new IllegalStateException("Run is no longer active: " + runId);
		}
		storeArtifact(turnId, runId, TurnArtifactType.TIMELINE, timelineJson);
		ConversationTurn turn = turnMapper.selectById(turnId);
		if (turn != null) {
			saveTimelineMessage(turnId, runId, timelineJson);
			saveChatMessage(turn.getConversationId(), "assistant", "用户已终止本次对话。", "warning", turnId, runId);
		}
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

	private void saveTimelineMessage(String turnId, String runId, String timelineJson) {
		if (StringUtils.isBlank(timelineJson)) {
			return;
		}
		ConversationTurn turn = turnMapper.selectById(turnId);
		if (turn != null) {
			saveChatMessage(turn.getConversationId(), "assistant", timelineJson, "timeline", turnId, runId);
		}
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
