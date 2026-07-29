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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
	void onlyVerifiedSuccessfulTurnEmitsProjectionEvent() {
		when(turnMapper.selectById("turn-1"))
			.thenReturn(ConversationTurn.builder()
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
		verify(outboxService).enqueue("CONVERSATION_TURN", "turn-1", MemoryEventType.TURN_SUCCEEDED, null);
		verify(memoryGateway).commitSuccessfulTurn("conversation-1", "q", "verified report");
	}

	@Test
	void finalTextWithoutExecutionEvidenceNeverEmitsProjectionEvent() {
		when(turnMapper.selectById("turn-1"))
			.thenReturn(ConversationTurn.builder().id("turn-1").conversationId("conversation-1").rawQuery("q").build());
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

		verify(turnMapper).complete(argThat(turn -> turn.getStatus() == TurnStatus.SUCCEEDED
				&& Boolean.FALSE.equals(turn.getMemoryEligible())));
		verifyNoInteractions(outboxService);
		verify(memoryGateway).commitSuccessfulTurn("conversation-1", "q", "请先配置数据源");
	}

	@Test
	void lateCompletionCannotProjectAfterTheDatabaseRejectsTheTransition() {
		when(turnMapper.selectById("turn-1")).thenReturn(ConversationTurn.builder()
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
	void resumeTurnCanRecoverTurnIdFromDurableRun() {
		when(runMapper.selectById("run-1")).thenReturn(TurnRun.builder().runId("run-1").turnId("turn-1").build());
		when(runMapper.resume("run-1")).thenReturn(1);
		when(turnMapper.markRunning("turn-1", "run-1")).thenReturn(1);

		String turnId = service.resumeTurn(null, "run-1", false);

		assertThat(turnId).isEqualTo("turn-1");
		verify(runMapper).resume("run-1");
		verify(turnMapper).markRunning("turn-1", "run-1");
	}

	@Test
	void waitingForReviewNeverProjectsPlannerOutputAsMemory() {
		when(turnMapper.markWaitingReview("turn-1", "run-1")).thenReturn(1);
		when(runMapper.markWaitingReview("run-1")).thenReturn(1);

		service.markWaitingReview("turn-1", "run-1", "[[]]");

		verify(turnMapper).markWaitingReview("turn-1", "run-1");
		verify(runMapper).markWaitingReview("run-1");
		verifyNoInteractions(outboxService);
	}

	@Test
	void deletingConversationTombstonesTurnIndexesBeforeRemovingDurableTurnsAndMessages() {
		when(turnMapper.selectByConversationId("conversation-1"))
			.thenReturn(List.of(ConversationTurn.builder().id("turn-1").build(),
					ConversationTurn.builder().id("turn-2").build()));

		service.deleteByConversation("conversation-1");

		verify(outboxService).enqueue("CONVERSATION_TURN", "turn-1", MemoryEventType.TURN_INVALIDATED, null);
		verify(outboxService).enqueue("CONVERSATION_TURN", "turn-2", MemoryEventType.TURN_INVALIDATED, null);
		verify(turnMapper).deleteByConversationId("conversation-1");
		verify(chatMessageMapper).deleteBySessionId("conversation-1");
	}

}
