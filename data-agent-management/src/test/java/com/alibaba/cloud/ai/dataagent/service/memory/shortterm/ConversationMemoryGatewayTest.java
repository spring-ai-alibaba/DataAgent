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

	private ConversationMemoryGateway gateway;

	@BeforeEach
	void setUp() {
		gateway = new ConversationMemoryGateway(chatMemory);
	}

	@Test
	void frameworkMessagesAreThePrimaryRecentConversationSource() {
		when(chatMemory.get("conversation-1"))
			.thenReturn(List.of(new SystemMessage("internal"), new UserMessage("question"),
					new AssistantMessage("answer")));

		List<Message> messages = gateway.loadRecent("conversation-1");

		assertThat(messages).hasSize(2);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
	}

	@Test
	void successfulBoundaryCommitStoresOnlyUserAndFinalAssistantMessages() {
		gateway.commitSuccessfulTurn("conversation-1", " question ", "final answer");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
		verify(chatMemory).add(eq("conversation-1"), captor.capture());
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

}
