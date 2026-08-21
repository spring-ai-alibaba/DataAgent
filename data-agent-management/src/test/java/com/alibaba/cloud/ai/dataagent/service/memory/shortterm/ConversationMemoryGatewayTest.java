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
package com.alibaba.cloud.ai.dataagent.service.memory.shortterm;

import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryGatewayTest {

	@Mock
	private ChatMemory chatMemory;

	@Mock
	private ConversationTurnMapper turnMapper;

	private ConversationMemoryGateway gateway;

	@BeforeEach
	void setUp() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getMemory().setRecentTurns(2);
		gateway = new ConversationMemoryGateway(chatMemory, turnMapper, properties);
	}

	@Test
	void frameworkMessagesAreThePrimaryRecentConversationSource() {
		when(chatMemory.get(ConversationMemoryGateway.memoryKey("conversation-1")))
			.thenReturn(List.of(new SystemMessage("internal"), new UserMessage("question-1"),
					new AssistantMessage("answer-1"), new UserMessage("question-2"),
					new AssistantMessage("answer-2")));

		List<Message> messages = gateway.loadRecent("conversation-1");

		assertThat(messages).hasSize(4);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
		verifyNoInteractions(turnMapper);
	}

	@Test
	void underfilledFrameworkWindowUsesDurableTurnsUntilProjectionCatchesUp() {
		when(chatMemory.get(ConversationMemoryGateway.memoryKey("conversation-1")))
			.thenReturn(List.of(new UserMessage("new question"), new AssistantMessage("new answer")));
		when(turnMapper.selectRecentContextTurns("conversation-1", 2))
			.thenReturn(List.of(turn("new question", "new answer"), turn("old question", "old answer")));

		assertThat(gateway.loadRecent("conversation-1")).extracting(Message::getText)
			.containsExactly("old question", "old answer", "new question", "new answer");
	}

	@Test
	void frameworkReadIsBoundedWhenConfiguredWindowShrinks() {
		when(chatMemory.get(ConversationMemoryGateway.memoryKey("conversation-1")))
			.thenReturn(List.of(new UserMessage("question-1"), new AssistantMessage("answer-1"),
					new UserMessage("question-2"), new AssistantMessage("answer-2"),
					new UserMessage("question-3"), new AssistantMessage("answer-3")));

		assertThat(gateway.loadRecent("conversation-1")).extracting(Message::getText)
			.containsExactly("question-2", "answer-2", "question-3", "answer-3");
		verifyNoInteractions(turnMapper);
	}

	@Test
	void unreadableFrameworkProjectionCannotBreakTheCurrentRequest() {
		when(chatMemory.get(ConversationMemoryGateway.memoryKey("conversation-1")))
			.thenThrow(new IllegalStateException("corrupt projection"));

		assertThat(gateway.loadRecent("conversation-1")).isEmpty();
		verify(turnMapper).selectRecentContextTurns("conversation-1", 2);
	}

	@Test
	void emptyFrameworkProjectionFallsBackToRecentDurableSuccessfulTurnsInChronologicalOrder() {
		when(chatMemory.get(ConversationMemoryGateway.memoryKey("conversation-1"))).thenReturn(List.of());
		when(turnMapper.selectRecentContextTurns("conversation-1", 2))
			.thenReturn(List.of(turn("new question", "new answer"), turn("old question", "old answer")));

		List<Message> messages = gateway.loadRecent("conversation-1");

		assertThat(messages).extracting(Message::getText)
			.containsExactly("old question", "old answer", "new question", "new answer");
	}

	@Test
	void incompleteFrameworkProjectionUsesDurableFallbackInsteadOfInjectingHalfATurn() {
		when(chatMemory.get(ConversationMemoryGateway.memoryKey("conversation-1")))
			.thenReturn(List.of(new AssistantMessage("orphan answer")));
		when(turnMapper.selectRecentContextTurns("conversation-1", 2))
			.thenReturn(List.of(turn("verified question", "verified answer")));

		assertThat(gateway.loadRecent("conversation-1")).extracting(Message::getText)
			.containsExactly("verified question", "verified answer");
	}

	@Test
	void successfulBoundaryCommitStoresOnlyUserAndFinalAssistantMessages() {
		gateway.commitSuccessfulTurn("conversation-1", " question ", "final answer");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
		verify(chatMemory).add(eq(ConversationMemoryGateway.memoryKey("conversation-1")), captor.capture());
		assertThat(captor.getValue()).hasSize(2);
		assertThat(captor.getValue().get(0)).isInstanceOf(UserMessage.class);
		assertThat(captor.getValue().get(0).getText()).isEqualTo("question");
		assertThat(captor.getValue().get(1)).isInstanceOf(AssistantMessage.class);
		assertThat(captor.getValue().get(1).getText()).isEqualTo("final answer");
	}

	@Test
	void blankConversationNeverTouchesFrameworkMemory() {
		assertThat(gateway.loadRecent(" ")).isEmpty();

		verifyNoInteractions(chatMemory);
	}

	@Test
	void clearRemovesBothCurrentAndLegacyFrameworkKeys() {
		gateway.clear("conversation-1");

		verify(chatMemory).clear(ConversationMemoryGateway.memoryKey("conversation-1"));
		verify(chatMemory).clear("conversation-1");
	}

	@Test
	void frameworkMemoryKeyIsNamespacedDeterministicAndFitsOfficialJdbcSchema() {
		String first = ConversationMemoryGateway.memoryKey("conversation-1");

		assertThat(first).hasSize(36).isEqualTo(ConversationMemoryGateway.memoryKey("conversation-1"));
		assertThat(first).isNotEqualTo(ConversationMemoryGateway.memoryKey("conversation-2"));
		assertThat(first).isNotEqualTo("conversation-1");
	}

	private ConversationTurn turn(String query, String answer) {
		return ConversationTurn.builder().rawQuery(query).finalAnswer(answer).build();
	}

}
