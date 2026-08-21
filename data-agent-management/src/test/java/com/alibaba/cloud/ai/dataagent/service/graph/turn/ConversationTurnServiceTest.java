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

import com.alibaba.cloud.ai.dataagent.entity.ChatMessage;
import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.entity.TurnRun;
import com.alibaba.cloud.ai.dataagent.enums.TurnStatus;
import com.alibaba.cloud.ai.dataagent.mapper.*;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.chat.SessionTitleService;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryEventType;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryOutboxService;
import com.alibaba.cloud.ai.dataagent.service.memory.shortterm.ConversationMemoryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationTurnServiceTest {

	@Mock
	private ConversationTurnMapper turnMapper;

	@Mock
	private TurnRunMapper runMapper;

	@Mock
	private TurnArtifactMapper artifactMapper;

	@Mock
	private ChatSessionMapper chatSessionMapper;

	@Mock
	private ChatMessageMapper chatMessageMapper;

	@Mock
	private MemoryOutboxService outboxService;

	@Mock
	private ConversationMemoryGateway memoryGateway;

	@Mock
	private SessionTitleService sessionTitleService;

	private ConversationTurnService service;

	@BeforeEach
	void setUp() {
		DataAgentProperties properties = new DataAgentProperties();
		service = new ConversationTurnService(turnMapper, runMapper, artifactMapper, chatSessionMapper,
				chatMessageMapper, outboxService, memoryGateway, sessionTitleService, properties);
	}

	private void stubTurnForCompletion(ConversationTurn turn) {
		when(turnMapper.selectById(turn.getId())).thenReturn(turn);
		when(chatSessionMapper.lockBySessionId(turn.getConversationId())).thenReturn(turn.getConversationId());
		when(turnMapper.selectByIdForUpdate(turn.getId())).thenReturn(turn);
	}

	private ConversationTurn stubActiveTurn(String statusTurnId) {
		ConversationTurn turn = ConversationTurn.builder()
			.id(statusTurnId)
			.conversationId("conversation-1")
			.acceptedRunId("run-1")
			.rawQuery("q")
			.status(TurnStatus.RUNNING)
			.build();
		stubTurnForCompletion(turn);
		return turn;
	}

	@Test
	void beginTurnCreatesAuthoritativeTurnRunAndServerOwnedUserMessage() {
		when(chatSessionMapper.selectBySessionId("conversation-1"))
			.thenReturn(ChatSession.builder().id("conversation-1").agentId(7).userId(99L).build());

		String turnId = service.beginTurn("conversation-1", 7, 3, "run-1", "  revenue last month  ", true);

		assertThat(turnId).isNotBlank();
		ArgumentCaptor<ConversationTurn> turnCaptor = ArgumentCaptor.forClass(ConversationTurn.class);
		verify(turnMapper).insert(turnCaptor.capture());
		assertThat(turnCaptor.getValue().getRawQuery()).isEqualTo("revenue last month");
		assertThat(turnCaptor.getValue().getDatasourceId()).isEqualTo(3);
		assertThat(turnCaptor.getValue().getStatus()).isEqualTo(TurnStatus.RUNNING);
		verify(runMapper).insert(argThat(run -> "run-1".equals(run.getRunId()) && turnId.equals(run.getTurnId())));
		verify(chatMessageMapper).insert(argThat(message -> "user".equals(message.getRole())
				&& message.getMetadata().contains(turnId) && message.getMetadata().contains("run-1")));
		verify(sessionTitleService).scheduleTitleGeneration("conversation-1", "  revenue last month  ");
	}

	@Test
	void beginTurnAttachesLegacyFrontendMessageInsteadOfDuplicatingIt() {
		when(chatSessionMapper.selectBySessionId("conversation-1"))
			.thenReturn(ChatSession.builder().id("conversation-1").agentId(7).build());
		when(chatMessageMapper.selectLatestBySessionId("conversation-1")).thenReturn(ChatMessage.builder()
			.id(42L)
			.sessionId("conversation-1")
			.role("user")
			.content("same question")
			.createTime(java.time.LocalDateTime.now())
			.build());

		service.beginTurn("conversation-1", 7, 3, "run-1", "same question", false);

		verify(chatMessageMapper).updateMetadata(eq(42L), contains("run-1"));
		verify(chatMessageMapper, never()).insert(any());
	}

	@Test
	void beginTurnRejectsDeletedConversationInsteadOfTreatingItAsLegacy() {
		when(chatSessionMapper.selectBySessionId("deleted-conversation")).thenReturn(null);
		when(chatSessionMapper.selectAnyBySessionId("deleted-conversation"))
			.thenReturn(ChatSession.builder().id("deleted-conversation").status("deleted").build());

		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> service.beginTurn("deleted-conversation", 7, 3, "run-1", "query", false))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("deleted");

		verifyNoInteractions(turnMapper, runMapper, chatMessageMapper);
	}

	@Test
	void verifiedSuccessfulTurnEmitsCompletionProjectionEvent() {
		stubTurnForCompletion(ConversationTurn.builder()
			.id("turn-1")
			.conversationId("conversation-1")
			.datasourceId(3)
			.rawQuery("q")
			.build());
		when(turnMapper.complete(any())).thenReturn(1);
		when(runMapper.markSucceeded("run-1")).thenReturn(1);
		TurnMemorySnapshot snapshot = mock(TurnMemorySnapshot.class);
		when(snapshot.getCanonicalQuery()).thenReturn("canonical");
		when(snapshot.queryFrameJson()).thenReturn("{}");
		when(snapshot.sqlArtifactJson()).thenReturn("[]");
		when(snapshot.resultArtifactJson()).thenReturn("{\"rows\":1}");
		when(snapshot.hasVerifiedEvidence()).thenReturn(true);

		service.completeTurn("turn-1", "run-1", snapshot, "verified report", "[[]]");

		verify(turnMapper).complete(argThat(turn -> turn.getStatus() == TurnStatus.SUCCEEDED
				&& Boolean.TRUE.equals(turn.getMemoryEligible()) && "verified report".equals(turn.getFinalAnswer())
				&& "{\"rows\":1}".equals(turn.getResultSummary())
				&& Integer.valueOf(3).equals(turn.getDatasourceId())));
		verify(outboxService).enqueue("CONVERSATION_TURN", "turn-1", MemoryEventType.TURN_COMPLETED, null);
		verify(outboxService).enqueue("GRAPH_RUN", "run-1", MemoryEventType.GRAPH_CHECKPOINT_RELEASE, null);
		verify(memoryGateway).commitSuccessfulTurn("conversation-1", "q", "verified report");
		InOrder order = inOrder(chatSessionMapper, memoryGateway);
		order.verify(chatSessionMapper).lockBySessionId("conversation-1");
		order.verify(memoryGateway).commitSuccessfulTurn("conversation-1", "q", "verified report");
	}

	@Test
	void finalTextWithoutExecutionEvidenceStillAdvancesConversationWindow() {
		stubTurnForCompletion(
				ConversationTurn.builder().id("turn-1").conversationId("conversation-1").rawQuery("q").build());
		when(turnMapper.complete(any())).thenReturn(1);
		when(runMapper.markSucceeded("run-1")).thenReturn(1);
		TurnMemorySnapshot snapshot = mock(TurnMemorySnapshot.class);
		when(snapshot.getFinalAnswer()).thenReturn("请先配置数据源");
		when(snapshot.getCanonicalQuery()).thenReturn("q");
		when(snapshot.queryFrameJson()).thenReturn("{}");
		when(snapshot.sqlArtifactJson()).thenReturn("[]");
		when(snapshot.resultArtifactJson()).thenReturn("{}");
		when(snapshot.hasVerifiedEvidence()).thenReturn(false);

		service.completeTurn("turn-1", "run-1", snapshot, "", null);

		verify(turnMapper).complete(argThat(
				turn -> turn.getStatus() == TurnStatus.SUCCEEDED && Boolean.FALSE.equals(turn.getMemoryEligible())));
		verify(outboxService).enqueue("CONVERSATION_TURN", "turn-1", MemoryEventType.TURN_COMPLETED, null);
		verify(outboxService).enqueue("GRAPH_RUN", "run-1", MemoryEventType.GRAPH_CHECKPOINT_RELEASE, null);
		verify(memoryGateway).commitSuccessfulTurn("conversation-1", "q", "请先配置数据源");
	}

	@Test
	void successfulTurnWithoutFinalTextStillEnqueuesCheckpointCleanup() {
		stubTurnForCompletion(
				ConversationTurn.builder().id("turn-1").conversationId("conversation-1").rawQuery("q").build());
		when(turnMapper.complete(any())).thenReturn(1);
		when(runMapper.markSucceeded("run-1")).thenReturn(1);

		service.completeTurn("turn-1", "run-1", mock(TurnMemorySnapshot.class), "", null);

		verify(outboxService).enqueue("GRAPH_RUN", "run-1", MemoryEventType.GRAPH_CHECKPOINT_RELEASE, null);
		verify(outboxService, never()).enqueue("CONVERSATION_TURN", "turn-1", MemoryEventType.TURN_COMPLETED, null);
		verifyNoInteractions(memoryGateway);
	}

	@Test
	void lateCompletionCannotProjectAfterTheDatabaseRejectsTheTransition() {
		stubTurnForCompletion(ConversationTurn.builder()
			.id("turn-1")
			.conversationId("conversation-1")
			.acceptedRunId("run-1")
			.rawQuery("q")
			.status(TurnStatus.CANCELLED)
			.build());
		TurnMemorySnapshot snapshot = mock(TurnMemorySnapshot.class);

		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> service.completeTurn("turn-1", "run-1", snapshot, "verified report", null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("already terminal");

		verifyNoInteractions(outboxService);
		verifyNoInteractions(artifactMapper);
		verify(runMapper, never()).markSucceeded("run-1");
	}

	@Test
	void completionCannotWriteFrameworkMemoryAfterConversationWasDeleted() {
		ConversationTurn turn = ConversationTurn.builder()
			.id("turn-1")
			.conversationId("conversation-1")
			.rawQuery("q")
			.build();
		when(turnMapper.selectById("turn-1")).thenReturn(turn);
		when(chatSessionMapper.lockBySessionId("conversation-1")).thenReturn(null);

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> service.completeTurn("turn-1", "run-1", mock(TurnMemorySnapshot.class), "verified report", null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("active conversation no longer exists");

		verify(turnMapper, never()).complete(any());
		verifyNoInteractions(memoryGateway, outboxService);
	}

	@Test
	void repeatedSuccessfulCompletionIsIdempotentBeforeAnyProjectionWrite() {
		ConversationTurn turn = ConversationTurn.builder()
			.id("turn-1")
			.conversationId("conversation-1")
			.acceptedRunId("run-1")
			.status(TurnStatus.SUCCEEDED)
			.build();
		stubTurnForCompletion(turn);

		service.completeTurn("turn-1", "run-1", mock(TurnMemorySnapshot.class), "report", null);

		verify(turnMapper, never()).complete(any());
		verifyNoInteractions(runMapper, artifactMapper, memoryGateway, outboxService);
	}

	@Test
	void resumeTurnCanRecoverTurnIdFromDurableRun() {
		when(runMapper.selectById("run-1")).thenReturn(TurnRun.builder().runId("run-1").turnId("turn-1").build());
		ConversationTurn turn = ConversationTurn.builder()
			.id("turn-1")
			.acceptedRunId("run-1")
			.conversationId("conversation-1")
			.agentId(7)
			.datasourceId(3)
			.rawQuery("q")
			.build();
		stubTurnForCompletion(turn);
		when(runMapper.resume("run-1")).thenReturn(1);
		when(turnMapper.markRunning("turn-1", "run-1")).thenReturn(1);

		TurnExecutionScope scope = service.resumeTurn(null, "run-1", false, 7, "conversation-1");

		assertThat(scope.turnId()).isEqualTo("turn-1");
		assertThat(scope.datasourceId()).isEqualTo(3);
		verify(runMapper).resume("run-1");
		verify(turnMapper).markRunning("turn-1", "run-1");
	}

	@Test
	void resumeTurnRejectsRequestScopeThatDoesNotMatchPersistedTurn() {
		when(runMapper.selectById("run-1")).thenReturn(TurnRun.builder().runId("run-1").turnId("turn-1").build());
		ConversationTurn turn = ConversationTurn.builder()
			.id("turn-1")
			.acceptedRunId("run-1")
			.conversationId("conversation-1")
			.agentId(7)
			.build();
		stubTurnForCompletion(turn);

		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> service.resumeTurn("turn-1", "run-1", false, 8, "conversation-1"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("authenticated agent");

		verify(turnMapper, never()).markRunning(anyString(), anyString());
		verify(runMapper, never()).resume(anyString());
	}

	@Test
	void resumeTurnRejectsDeletedConversationBeforeChangingRunState() {
		when(runMapper.selectById("run-1")).thenReturn(TurnRun.builder().runId("run-1").turnId("turn-1").build());
		when(turnMapper.selectById("turn-1")).thenReturn(ConversationTurn.builder()
			.id("turn-1")
			.acceptedRunId("run-1")
			.conversationId("conversation-1")
			.agentId(7)
			.build());
		when(chatSessionMapper.lockBySessionId("conversation-1")).thenReturn(null);

		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> service.resumeTurn("turn-1", "run-1", false, 7, "conversation-1"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("active conversation");

		verify(turnMapper, never()).selectByIdForUpdate(anyString());
		verify(turnMapper, never()).markRunning(anyString(), anyString());
		verify(runMapper, never()).resume(anyString());
	}

	@Test
	void waitingForReviewNeverProjectsPlannerOutputAsMemory() {
		stubActiveTurn("turn-1");
		when(turnMapper.markWaitingReview("turn-1", "run-1")).thenReturn(1);
		when(runMapper.markWaitingReview("run-1")).thenReturn(1);

		service.markWaitingReview("turn-1", "run-1", "[[]]");

		verify(turnMapper).markWaitingReview("turn-1", "run-1");
		verify(runMapper).markWaitingReview("run-1");
		verifyNoInteractions(outboxService);
	}

	@Test
	void failureUsesSessionThenTurnLockOrderBeforeWritingMessages() {
		stubActiveTurn("turn-1");
		when(turnMapper.markTerminal("turn-1", "run-1", TurnStatus.FAILED)).thenReturn(1);
		when(runMapper.markTerminal(eq("run-1"), eq(TurnStatus.FAILED), anyString())).thenReturn(1);

		service.failTurn("turn-1", "run-1", new IllegalStateException("boom"), "timeline");

		InOrder order = inOrder(chatSessionMapper, turnMapper, chatMessageMapper);
		order.verify(chatSessionMapper).lockBySessionId("conversation-1");
		order.verify(turnMapper).selectByIdForUpdate("turn-1");
		order.verify(turnMapper).markTerminal("turn-1", "run-1", TurnStatus.FAILED);
		order.verify(chatMessageMapper, times(2)).insert(any());
		verify(outboxService).enqueue("GRAPH_RUN", "run-1", MemoryEventType.GRAPH_CHECKPOINT_RELEASE, null);
	}

	@Test
	void cancellationEnqueuesDurableCheckpointCleanup() {
		stubActiveTurn("turn-1");
		when(turnMapper.markTerminal("turn-1", "run-1", TurnStatus.CANCELLED)).thenReturn(1);
		when(runMapper.markTerminal(eq("run-1"), eq(TurnStatus.CANCELLED), anyString())).thenReturn(1);

		service.cancelTurn("turn-1", "run-1", null);

		verify(outboxService).enqueue("GRAPH_RUN", "run-1", MemoryEventType.GRAPH_CHECKPOINT_RELEASE, null);
	}

	@Test
	void cancellationDoesNotWriteAfterConversationWasDeleted() {
		ConversationTurn turn = ConversationTurn.builder().id("turn-1").conversationId("conversation-1").build();
		when(turnMapper.selectById("turn-1")).thenReturn(turn);
		when(chatSessionMapper.lockBySessionId("conversation-1")).thenReturn(null);

		service.cancelTurn("turn-1", "run-1", "timeline");

		verify(turnMapper, never()).markTerminal(anyString(), anyString(), any());
		verifyNoInteractions(runMapper, artifactMapper, chatMessageMapper, outboxService);
	}

}
