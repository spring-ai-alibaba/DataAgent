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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The single application boundary around Spring AI {@link ChatMemory}.
 *
 * <p>
 * Only accepted user/final-assistant pairs are committed. Graph-internal prompts and
 * partial streaming output must never be written to conversational memory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationMemoryGateway {

	private static final String MEMORY_KEY_NAMESPACE = "data-agent-chat-memory:v2:";

	private final ChatMemory chatMemory;

	private final ConversationTurnMapper turnMapper;

	private final DataAgentProperties properties;

	/**
	 * Loads the framework-owned recent message window.
	 */
	public List<Message> loadRecent(String conversationId) {
		if (StringUtils.isBlank(conversationId)) {
			return List.of();
		}
		try {
			List<Message> frameworkMessages = conversationalMessages(chatMemory.get(memoryKey(conversationId)));
			if (isCompleteTurnSequence(frameworkMessages)) {
				int maxMessages = Math.max(2, properties.resolveRecentTurns() * 2);
				List<Message> boundedFrameworkMessages = newestMessages(frameworkMessages, maxMessages);
				if (boundedFrameworkMessages.size() == maxMessages) {
					return boundedFrameworkMessages;
				}
				// MessageWindowChatMemory trims only when add(...) is called. After a
				// projection loss, upgrade, or an increased window size, the framework
				// repository can contain a structurally valid but underfilled window. The
				// summary intentionally excludes the latest N turns, so consult the
				// durable
				// source until the framework window has naturally filled again.
				List<Message> durableMessages = loadDurableFallback(conversationId);
				return durableMessages.isEmpty() ? boundedFrameworkMessages : durableMessages;
			}
			if (frameworkMessages.isEmpty()) {
				log.debug("Framework memory for conversation {} is empty; checking the durable turn fallback",
						conversationId);
			}
			else {
				log.warn("Framework memory for conversation {} is incomplete; using the durable turn fallback",
						conversationId);
			}
		}
		catch (RuntimeException ex) {
			// The durable turn log remains authoritative. A corrupt or temporarily
			// unavailable framework projection must not make the current request fail or
			// leave a gap between the rolling summary and the recent window.
			log.warn("Unable to load recent framework memory for conversation {}; using the durable turn fallback",
					conversationId, ex);
		}
		return loadDurableFallback(conversationId);
	}

	public void commitSuccessfulTurn(String conversationId, String userText, String assistantText) {
		if (StringUtils.isAnyBlank(conversationId, userText, assistantText)) {
			return;
		}
		chatMemory.add(memoryKey(conversationId),
				List.of(new UserMessage(userText.trim()), new AssistantMessage(assistantText)));
	}

	public void clear(String conversationId) {
		if (StringUtils.isNotBlank(conversationId)) {
			chatMemory.clear(memoryKey(conversationId));
			// The pre-v2 implementation stored Planner output under the raw conversation
			// ID.
			// Never read that data as final assistant answers, but remove it on explicit
			// conversation deletion so legacy rows do not become retention residue.
			chatMemory.clear(conversationId);
		}
	}

	static String memoryKey(String conversationId) {
		// Spring AI's official JDBC schemas use VARCHAR(36) for conversation_id.
		// A namespaced UUID keeps the framework schema intact while isolating current
		// final-answer memory from legacy rows stored under the raw conversation ID.
		return UUID.nameUUIDFromBytes((MEMORY_KEY_NAMESPACE + conversationId).getBytes(StandardCharsets.UTF_8))
			.toString();
	}

	private List<Message> conversationalMessages(List<Message> messages) {
		if (messages == null || messages.isEmpty()) {
			return List.of();
		}
		return messages.stream()
			.filter(message -> message instanceof UserMessage || message instanceof AssistantMessage)
			.toList();
	}

	private boolean isCompleteTurnSequence(List<Message> messages) {
		if (messages.isEmpty() || messages.size() % 2 != 0) {
			return false;
		}
		for (int i = 0; i < messages.size(); i += 2) {
			if (!(messages.get(i) instanceof UserMessage) || !(messages.get(i + 1) instanceof AssistantMessage)) {
				return false;
			}
		}
		return true;
	}

	private List<Message> newestMessages(List<Message> messages, int maxMessages) {
		if (messages.size() <= maxMessages) {
			return messages;
		}
		return List.copyOf(messages.subList(messages.size() - maxMessages, messages.size()));
	}

	private List<Message> loadDurableFallback(String conversationId) {
		try {
			List<ConversationTurn> newestFirst = turnMapper.selectRecentContextTurns(conversationId,
					Math.max(1, properties.resolveRecentTurns()));
			List<ConversationTurn> chronological = new ArrayList<>(newestFirst);
			Collections.reverse(chronological);
			List<Message> messages = new ArrayList<>(chronological.size() * 2);
			for (ConversationTurn turn : chronological) {
				if (StringUtils.isAnyBlank(turn.getRawQuery(), turn.getFinalAnswer())) {
					continue;
				}
				messages.add(new UserMessage(turn.getRawQuery()));
				messages.add(new AssistantMessage(turn.getFinalAnswer()));
			}
			return List.copyOf(messages);
		}
		catch (RuntimeException ex) {
			log.warn("Unable to load durable recent-turn fallback for conversation {}; continuing without it",
					conversationId, ex);
			return List.of();
		}
	}

}
