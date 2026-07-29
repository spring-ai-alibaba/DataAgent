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

import com.alibaba.cloud.ai.dataagent.entity.ChatMessage;
import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.entity.TurnRun;
import com.alibaba.cloud.ai.dataagent.enums.TurnStatus;
import com.alibaba.cloud.ai.dataagent.mapper.*;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.chat.SessionTitleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
	private SessionTitleService sessionTitleService;

	private ConversationTurnService service;

	@BeforeEach
	void setUp() {
		DataAgentProperties properties = new DataAgentProperties();
		service = new ConversationTurnService(turnMapper, runMapper, artifactMapper, chatSessionMapper,
				chatMessageMapper, outboxService, sessionTitleService, properties);
	}

	@Test
	void beginTurnCreatesAuthoritativeTurnRunAndServerOwnedUserMessage() {
		when(chatSessionMapper.selectBySessionId("conversation-1"))
			.thenReturn(ChatSession.builder().id("conversation-1").agentId(7).userId(99L).build());

		String turnId = service.beginTurn("conversation-1", 7, "run-1", "  revenue last month  ", true);

		assertThat(turnId).isNotBlank();
		ArgumentCaptor<ConversationTurn> turnCaptor = ArgumentCaptor.forClass(ConversationTurn.class);
		verify(turnMapper).insert(turnCaptor.capture());
		assertThat(turnCaptor.getValue().getRawQuery()).isEqualTo("revenue last month");
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

		service.beginTurn("conversation-1", 7, "run-1", "same question", false);

		verify(chatMessageMapper).updateMetadata(eq(42L), contains("run-1"));
		verify(chatMessageMapper, never()).insert(any());
	}

	@Test
	void onlyVerifiedSuccessfulTurnEmitsProjectionEvent() {
		when(turnMapper.selectById("turn-1"))
			.thenReturn(ConversationTurn.builder().id("turn-1").conversationId("conversation-1").rawQuery("q").build());
		TurnMemorySnapshot snapshot = mock(TurnMemorySnapshot.class);
		when(snapshot.getCanonicalQuery()).thenReturn("canonical");
		when(snapshot.queryFrameJson()).thenReturn("{}");
		when(snapshot.sqlArtifactJson()).thenReturn("[]");
		when(snapshot.resultArtifactJson()).thenReturn("{\"rows\":1}");
		when(snapshot.hasVerifiedEvidence("verified report")).thenReturn(true);

		service.completeTurn("turn-1", "run-1", snapshot, "verified report", "[[]]");

		verify(turnMapper).complete(argThat(turn -> turn.getStatus() == TurnStatus.SUCCEEDED
				&& Boolean.TRUE.equals(turn.getMemoryEligible()) && "verified report".equals(turn.getFinalAnswer())));
		verify(outboxService).enqueue("CONVERSATION_TURN", "turn-1", MemoryEventType.TURN_SUCCEEDED, null);
	}

	@Test
	void finalTextWithoutExecutionEvidenceNeverEmitsProjectionEvent() {
		when(turnMapper.selectById("turn-1"))
			.thenReturn(ConversationTurn.builder().id("turn-1").conversationId("conversation-1").rawQuery("q").build());
		TurnMemorySnapshot snapshot = mock(TurnMemorySnapshot.class);
		when(snapshot.getFinalAnswer()).thenReturn("请先配置数据源");
		when(snapshot.getCanonicalQuery()).thenReturn("q");
		when(snapshot.queryFrameJson()).thenReturn("{}");
		when(snapshot.sqlArtifactJson()).thenReturn("[]");
		when(snapshot.resultArtifactJson()).thenReturn("{}");
		when(snapshot.hasVerifiedEvidence("")).thenReturn(false);

		service.completeTurn("turn-1", "run-1", snapshot, "", null);

		verify(turnMapper).complete(argThat(turn -> turn.getStatus() == TurnStatus.SUCCEEDED
				&& Boolean.FALSE.equals(turn.getMemoryEligible())));
		verifyNoInteractions(outboxService);
	}

	@Test
	void resumeTurnCanRecoverTurnIdFromDurableRun() {
		when(runMapper.selectById("run-1")).thenReturn(TurnRun.builder().runId("run-1").turnId("turn-1").build());

		String turnId = service.resumeTurn(null, "run-1", false);

		assertThat(turnId).isEqualTo("turn-1");
		verify(runMapper).updateStatus("run-1", TurnStatus.RUNNING, null);
		verify(turnMapper).markRunning("turn-1", "run-1");
	}

	@Test
	void waitingForReviewNeverProjectsPlannerOutputAsMemory() {
		service.markWaitingReview("turn-1", "run-1", "[[]]");

		verify(turnMapper).markWaitingReview("turn-1");
		verify(runMapper).updateStatus("run-1", TurnStatus.WAITING_REVIEW, null);
		verifyNoInteractions(outboxService);
	}

}
