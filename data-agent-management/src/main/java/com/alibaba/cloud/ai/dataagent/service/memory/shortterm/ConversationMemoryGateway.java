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

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The single application boundary around Spring AI {@link ChatMemory}.
 *
 * <p>
 * Only accepted user/final-assistant pairs are committed. Graph-internal prompts and
 * partial streaming output must never be written to conversational memory.
 */
@Service
@RequiredArgsConstructor
public class ConversationMemoryGateway {

	private final ChatMemory chatMemory;

	/**
	 * Loads the framework-owned recent message window.
	 */
	public List<Message> loadRecent(String conversationId) {
		if (StringUtils.isBlank(conversationId)) {
			return List.of();
		}
		return conversationalMessages(chatMemory.get(conversationId));
	}

	public void commitSuccessfulTurn(String conversationId, String userText, String assistantText) {
		if (StringUtils.isAnyBlank(conversationId, userText, assistantText)) {
			return;
		}
		chatMemory.add(conversationId,
				List.of(new UserMessage(userText.trim()), new AssistantMessage(assistantText)));
	}

	public void clear(String conversationId) {
		if (StringUtils.isNotBlank(conversationId)) {
			chatMemory.clear(conversationId);
		}
	}

	private List<Message> conversationalMessages(List<Message> messages) {
		if (messages == null || messages.isEmpty()) {
			return List.of();
		}
		return messages.stream()
			.filter(message -> message instanceof UserMessage || message instanceof AssistantMessage)
			.toList();
	}

}
