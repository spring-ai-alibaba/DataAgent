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
package com.alibaba.cloud.ai.dataagent.service.memory.projection;

import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.entity.MemoryItem;
import com.alibaba.cloud.ai.dataagent.entity.MemoryOutboxEvent;
import com.alibaba.cloud.ai.dataagent.enums.MemoryStatus;
import com.alibaba.cloud.ai.dataagent.enums.TurnStatus;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.mapper.MemoryItemMapper;
import com.alibaba.cloud.ai.dataagent.mapper.MemoryOutboxMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.graph.checkpoint.ReleasedCheckpointCleanupService;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryEventType;
import com.alibaba.cloud.ai.dataagent.service.memory.semantic.MemoryVectorIndexService;
import com.alibaba.cloud.ai.dataagent.service.memory.shortterm.ConversationMemoryGateway;
import com.alibaba.cloud.ai.dataagent.service.memory.shortterm.ConversationSummaryService;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryProjectionWorkerTest {

	@Mock
	private MemoryOutboxMapper outboxMapper;

	@Mock
	private ConversationTurnMapper turnMapper;

	@Mock
	private MemoryItemMapper memoryItemMapper;

	@Mock
	private ConversationSummaryService summaryService;

	@Mock
	private MemoryVectorIndexService vectorIndexService;

	@Mock
	private ConversationMemoryGateway memoryGateway;

	@Mock
	private BaseCheckpointSaver checkpointSaver;

	@Mock
	private ReleasedCheckpointCleanupService checkpointCleanupService;

	private MemoryProjectionWorker worker;

	@BeforeEach
	void setUp() {
		worker = new MemoryProjectionWorker(outboxMapper, turnMapper, memoryItemMapper, summaryService,
				vectorIndexService, memoryGateway, checkpointSaver, checkpointCleanupService,
				new DataAgentProperties());
	}

	@Test
	void successfulTurnEventRebuildsDerivedIndexesAndIsMarkedDone() {
		MemoryOutboxEvent event = event(1L, MemoryEventType.TURN_COMPLETED, "turn-1");
		ConversationTurn turn = ConversationTurn.builder()
			.id("turn-1")
			.conversationId("conversation-1")
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(true)
			.build();
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(eq(1L), anyString(), eq(5))).thenReturn(1);
		when(outboxMapper.markDone(eq(1L), anyString())).thenReturn(1);
		when(turnMapper.selectById("turn-1")).thenReturn(turn, turn);

		worker.projectReadyEvents();

		verify(outboxMapper).recoverStale(any(LocalDateTime.class));
		verify(outboxMapper).reviveGuaranteedRetryDeadLetters();
		verify(summaryService).rebuild("conversation-1");
		verify(vectorIndexService).indexTurn(turn);
		verify(outboxMapper).markDone(eq(1L), anyString());
		verify(outboxMapper, never()).markFailed(anyLong(), anyString(), anyString(), any(LocalDateTime.class));
	}

	@Test
	void pollingRevivesLegacyDestructiveDeadLettersBeforeSelectingWork() {
		when(outboxMapper.reviveGuaranteedRetryDeadLetters()).thenReturn(2);

		worker.projectReadyEvents();

		InOrder order = inOrder(outboxMapper);
		order.verify(outboxMapper).recoverStale(any(LocalDateTime.class));
		order.verify(outboxMapper).reviveGuaranteedRetryDeadLetters();
		order.verify(outboxMapper).markExhaustedAsDead(5);
		order.verify(outboxMapper).selectReady(20, 5);
	}

	@Test
	void legacySuccessfulTurnEventRemainsConsumableDuringUpgrade() {
		MemoryOutboxEvent event = event(5L, MemoryEventType.LEGACY_TURN_SUCCEEDED, "turn-1");
		ConversationTurn turn = ConversationTurn.builder()
			.id("turn-1")
			.conversationId("conversation-1")
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(true)
			.build();
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(eq(5L), anyString(), eq(5))).thenReturn(1);
		when(outboxMapper.markDone(eq(5L), anyString())).thenReturn(1);
		when(turnMapper.selectById("turn-1")).thenReturn(turn, turn);

		worker.projectReadyEvents();

		verify(summaryService).rebuild("conversation-1");
		verify(vectorIndexService).indexTurn(turn);
		verify(outboxMapper).markDone(eq(5L), anyString());
	}

	@Test
	void failedProjectionIsRetriedInsteadOfBeingAcknowledged() {
		MemoryOutboxEvent event = event(2L, MemoryEventType.MEMORY_INVALIDATED, "10");
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(eq(2L), anyString(), eq(5))).thenReturn(1);
		when(outboxMapper.markFailed(eq(2L), anyString(), anyString(), any(LocalDateTime.class))).thenReturn(1);
		doThrow(new IllegalStateException("vector unavailable")).when(vectorIndexService).deleteMemoryItem(10L);

		worker.projectReadyEvents();

		verify(outboxMapper).markFailed(eq(2L), anyString(), contains("vector unavailable"), any(LocalDateTime.class));
		verify(outboxMapper, never()).markDone(eq(2L), anyString());
	}

	@Test
	void exhaustedRebuildableProjectionMovesToDeadInsteadOfRetryingForever() {
		MemoryOutboxEvent event = event(3L, MemoryEventType.MEMORY_CONFIRMED, "10");
		event.setAttemptCount(4);
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(eq(3L), anyString(), eq(5))).thenReturn(1);
		when(outboxMapper.markDead(eq(3L), anyString(), anyString())).thenReturn(1);
		when(memoryItemMapper.selectById(10L))
			.thenReturn(MemoryItem.builder().id(10L).status(MemoryStatus.CONFIRMED).build());
		doThrow(new IllegalStateException("vector unavailable")).when(vectorIndexService)
			.indexMemoryItem(any(MemoryItem.class));

		worker.projectReadyEvents();

		verify(outboxMapper).markDead(eq(3L), anyString(), eq("vector unavailable"));
		verify(outboxMapper, never()).markFailed(eq(3L), anyString(), anyString(), any(LocalDateTime.class));
	}

	@Test
	void exhaustedDestructiveProjectionKeepsRetryingWithBoundedBackoff() {
		MemoryOutboxEvent event = event(15L, MemoryEventType.MEMORY_INVALIDATED, "10");
		event.setAttemptCount(4);
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(eq(15L), anyString(), eq(5))).thenReturn(1);
		when(outboxMapper.markFailed(eq(15L), anyString(), anyString(), any(LocalDateTime.class))).thenReturn(1);
		doThrow(new IllegalStateException("vector unavailable")).when(vectorIndexService).deleteMemoryItem(10L);

		worker.projectReadyEvents();

		verify(outboxMapper).markFailed(eq(15L), anyString(), eq("vector unavailable"), any(LocalDateTime.class));
		verify(outboxMapper, never()).markDead(eq(15L), anyString(), anyString());
	}

	@Test
	void conversationForgetEventClearsFrameworkDerivedStores() {
		MemoryOutboxEvent event = event(4L, MemoryEventType.CONVERSATION_FORGOTTEN, "conversation-1");
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(eq(4L), anyString(), eq(5))).thenReturn(1);
		when(outboxMapper.markDone(eq(4L), anyString())).thenReturn(1);

		worker.projectReadyEvents();

		verify(summaryService).delete("conversation-1");
		verify(memoryGateway).clear("conversation-1");
		verify(outboxMapper).markDone(eq(4L), anyString());
	}

	@Test
	void checkpointReleaseEventUsesFrameworkSaverAndIsMarkedDone() throws Exception {
		MemoryOutboxEvent event = event(8L, MemoryEventType.GRAPH_CHECKPOINT_RELEASE, "run-1");
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(eq(8L), anyString(), eq(5))).thenReturn(1);
		when(outboxMapper.markDone(eq(8L), anyString())).thenReturn(1);

		worker.projectReadyEvents();

		verify(checkpointSaver).release(argThat(config -> config.threadId().orElseThrow().equals("run-1")));
		verify(outboxMapper).markDone(eq(8L), anyString());
	}

	@Test
	void alreadyReleasedCheckpointIsAcknowledgedAfterDatabaseVerification() throws Exception {
		MemoryOutboxEvent event = event(9L, MemoryEventType.GRAPH_CHECKPOINT_RELEASE, "run-1");
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(eq(9L), anyString(), eq(5))).thenReturn(1);
		when(outboxMapper.markDone(eq(9L), anyString())).thenReturn(1);
		doThrow(new IllegalStateException("already released")).when(checkpointSaver).release(any(RunnableConfig.class));
		when(checkpointSaver.list(any(RunnableConfig.class))).thenReturn(List.of());

		worker.projectReadyEvents();

		verify(outboxMapper).markDone(eq(9L), anyString());
		verify(outboxMapper, never()).markFailed(eq(9L), anyString(), anyString(), any(LocalDateTime.class));
		verify(checkpointCleanupService, never()).reconcileLegacyReleaseConflict("run-1");
		verify(checkpointSaver, never()).get(any(RunnableConfig.class));
	}

	@Test
	void legacyReleasedAndActiveGenerationConflictIsReconciledBeforeFrameworkReleaseRetry() throws Exception {
		MemoryOutboxEvent event = event(16L, MemoryEventType.GRAPH_CHECKPOINT_RELEASE, "run-1");
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(eq(16L), anyString(), eq(5))).thenReturn(1);
		when(outboxMapper.markDone(eq(16L), anyString())).thenReturn(1);
		when(checkpointSaver.release(any(RunnableConfig.class)))
			.thenThrow(new Exception("Unable to release checkpoint",
					new SQLIntegrityConstraintViolationException("duplicate released generation", "23000")))
			.thenReturn(new BaseCheckpointSaver.Tag("run-1", List.of()));
		when(checkpointCleanupService.reconcileLegacyReleaseConflict("run-1")).thenReturn(true);

		worker.projectReadyEvents();

		verify(checkpointSaver, times(2)).release(argThat(config -> config.threadId().orElseThrow().equals("run-1")));
		verify(checkpointCleanupService).reconcileLegacyReleaseConflict("run-1");
		verify(outboxMapper).markDone(eq(16L), anyString());
		verify(outboxMapper, never()).markFailed(eq(16L), anyString(), anyString(), any(LocalDateTime.class));
	}

	@Test
	void checkpointReleaseFailureIsRetriedWhileCheckpointRemainsActive() throws Exception {
		MemoryOutboxEvent event = event(10L, MemoryEventType.GRAPH_CHECKPOINT_RELEASE, "run-1");
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(eq(10L), anyString(), eq(5))).thenReturn(1);
		when(outboxMapper.markFailed(eq(10L), anyString(), anyString(), any(LocalDateTime.class))).thenReturn(1);
		doThrow(new IllegalStateException("checkpoint database unavailable")).when(checkpointSaver)
			.release(any(RunnableConfig.class));
		when(checkpointSaver.list(any(RunnableConfig.class))).thenReturn(List.of(mock(Checkpoint.class)));

		worker.projectReadyEvents();

		verify(outboxMapper).markFailed(eq(10L), anyString(), contains("Failed to release graph checkpoint"),
				any(LocalDateTime.class));
		verify(outboxMapper, never()).markDone(eq(10L), anyString());
		verify(checkpointCleanupService, never()).reconcileLegacyReleaseConflict("run-1");
	}

	@Test
	void staleTurnProjectionCompensatesIfTheTurnWasInvalidatedDuringVectorWrite() {
		MemoryOutboxEvent event = event(6L, MemoryEventType.TURN_COMPLETED, "turn-1");
		ConversationTurn succeeded = ConversationTurn.builder()
			.id("turn-1")
			.conversationId("conversation-1")
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(true)
			.build();
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(eq(6L), anyString(), eq(5))).thenReturn(1);
		when(outboxMapper.markDone(eq(6L), anyString())).thenReturn(1);
		when(turnMapper.selectById("turn-1")).thenReturn(succeeded).thenReturn(null);

		worker.projectReadyEvents();

		verify(vectorIndexService).indexTurn(succeeded);
		verify(vectorIndexService).deleteTurn("turn-1");
	}

	@Test
	void staleMemoryProjectionCompensatesIfMemoryWasInvalidatedDuringVectorWrite() {
		MemoryOutboxEvent event = event(7L, MemoryEventType.MEMORY_CONFIRMED, "10");
		MemoryItem confirmed = MemoryItem.builder().id(10L).status(MemoryStatus.CONFIRMED).build();
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(eq(7L), anyString(), eq(5))).thenReturn(1);
		when(outboxMapper.markDone(eq(7L), anyString())).thenReturn(1);
		when(memoryItemMapper.selectById(10L)).thenReturn(confirmed).thenReturn(null);

		worker.projectReadyEvents();

		verify(vectorIndexService).indexMemoryItem(confirmed);
		verify(vectorIndexService).deleteMemoryItem(10L);
	}

	@Test
	void completedOutboxCleanupIsBoundedAndNeverTargetsFailedOrDeadRows() {
		MemoryOutboxEvent first = event(11L, MemoryEventType.TURN_COMPLETED, "turn-1");
		MemoryOutboxEvent second = event(12L, MemoryEventType.MEMORY_CONFIRMED, "10");
		when(outboxMapper.selectCompletedBefore(any(LocalDateTime.class), eq(1000))).thenReturn(List.of(first, second));
		when(outboxMapper.deleteCompletedByIds(List.of(11L, 12L))).thenReturn(2);

		worker.purgeCompletedEvents();

		verify(outboxMapper).deleteCompletedByIds(List.of(11L, 12L));
		verifyNoInteractions(checkpointCleanupService);
	}

	@Test
	void completedCheckpointReleaseIsPhysicallyPurgedBeforeItsOutboxRecord() {
		MemoryOutboxEvent event = event(13L, MemoryEventType.GRAPH_CHECKPOINT_RELEASE, "run-1");
		when(outboxMapper.selectCompletedBefore(any(LocalDateTime.class), eq(1000))).thenReturn(List.of(event));
		when(outboxMapper.deleteCompletedByIds(List.of(13L))).thenReturn(1);

		worker.purgeCompletedEvents();

		var cleanupOrder = inOrder(checkpointCleanupService, outboxMapper);
		cleanupOrder.verify(checkpointCleanupService).purgeReleased("run-1");
		cleanupOrder.verify(outboxMapper).deleteCompletedByIds(List.of(13L));
	}

	@Test
	void failedPhysicalCheckpointPurgeKeepsItsOutboxRecordForRetry() {
		MemoryOutboxEvent checkpoint = event(13L, MemoryEventType.GRAPH_CHECKPOINT_RELEASE, "run-1");
		MemoryOutboxEvent regular = event(14L, MemoryEventType.TURN_COMPLETED, "turn-1");
		when(outboxMapper.selectCompletedBefore(any(LocalDateTime.class), eq(1000)))
			.thenReturn(List.of(checkpoint, regular));
		doThrow(new IllegalStateException("checkpoint table unavailable")).when(checkpointCleanupService)
			.purgeReleased("run-1");
		when(outboxMapper.deleteCompletedByIds(List.of(14L))).thenReturn(1);

		worker.purgeCompletedEvents();

		verify(outboxMapper).deleteCompletedByIds(List.of(14L));
	}

	private MemoryOutboxEvent event(Long id, String eventType, String aggregateId) {
		return MemoryOutboxEvent.builder().id(id).eventType(eventType).aggregateId(aggregateId).attemptCount(0).build();
	}

}
