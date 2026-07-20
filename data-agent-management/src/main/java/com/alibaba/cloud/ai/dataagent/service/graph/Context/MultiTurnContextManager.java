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
package com.alibaba.cloud.ai.dataagent.service.graph.Context;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages multi-turn dialogue context for each thread. The context keeps a lightweight
 * history of user questions and the corresponding planner outputs so downstream prompts
 * can reference prior turns.
 */
@Slf4j
@Component
@AllArgsConstructor
public class MultiTurnContextManager {

	private final DataAgentProperties properties;

	private final ChatMemory chatMemory;

	private final ChatMemoryRepository chatMemoryRepository;

	private final Map<String, PendingTurn> pendingTurns = new ConcurrentHashMap<>();

	/**
	 * Start tracking a new turn for the given thread.
	 * @param threadId conversation thread id
	 * @param userQuestion latest user question
	 */
	public void beginTurn(String threadId, String userQuestion) {
		if (StringUtils.isAnyBlank(threadId, userQuestion)) {
			return;
		}
		pendingTurns.put(threadId, new PendingTurn(userQuestion.trim()));
	}

	/**
	 * Append planner output chunk for the current turn.
	 * @param threadId conversation thread id
	 * @param chunk planner streaming chunk
	 */
	public void appendPlannerChunk(String threadId, String chunk) {
		if (StringUtils.isAnyBlank(threadId, chunk)) {
			return;
		}
		PendingTurn pending = pendingTurns.get(threadId);
		if (pending != null) {
			pending.planBuilder.append(chunk);
		}
	}

	/**
	 * Finalize current turn and add to history if planner output is available.
	 * @param threadId conversation thread id
	 */
	public void finishTurn(String threadId) {
		PendingTurn pending = pendingTurns.remove(threadId);
		if (pending == null) {
			return;
		}
		String plan = StringUtils.trimToEmpty(pending.planBuilder.toString());
		if (StringUtils.isBlank(plan)) {
			log.debug("No planner output recorded for thread {}, skipping history update", threadId);
			return;
		}

		String trimmedPlan = StringUtils.abbreviate(plan, properties.getMaxplanlength());
		chatMemory.add(threadId, List.of(new UserMessage(pending.userQuestion), new AssistantMessage(trimmedPlan)));
	}

	/**
	 * Remove any pending turn data without touching persisted history. Typically used
	 * when a run is aborted.
	 * @param threadId conversation thread id
	 */
	public void discardPending(String threadId) {
		pendingTurns.remove(threadId);
	}

	/**
	 * Restart the latest turn so a new planner output can replace it (e.g. after human
	 * feedback). The last stored turn will be removed and its question reused.
	 * @param threadId conversation thread id
	 */
	public void restartLastTurn(String threadId) {
		List<Message> messages = new ArrayList<>(chatMemoryRepository.findByConversationId(threadId));
		int lastUserIndex = -1;
		for (int i = messages.size() - 1; i >= 0; i--) {
			if (messages.get(i).getMessageType() == MessageType.USER) {
				lastUserIndex = i;
				break;
			}
		}
		if (lastUserIndex < 0) {
			return;
		}

		Message lastUserMessage = messages.get(lastUserIndex);
		chatMemoryRepository.saveAll(threadId, messages.subList(0, lastUserIndex));
		pendingTurns.put(threadId, new PendingTurn(lastUserMessage.getText()));
	}

	/**
	 * Build multi-turn context string for prompt injection.
	 * @param threadId conversation thread id
	 * @return formatted history string
	 */
	public String buildContext(String threadId) {
		List<Message> messages = chatMemory.get(threadId);
		if (messages.isEmpty()) {
			return "(无)";
		}
		String context = messages.stream()
			.filter(message -> message.getMessageType() == MessageType.USER
					|| message.getMessageType() == MessageType.ASSISTANT)
			.map(message -> formatMessage(message))
			.collect(Collectors.joining("\n"));
		return StringUtils.defaultIfBlank(context, "(无)");
	}

	private String formatMessage(Message message) {
		String prefix = message.getMessageType() == MessageType.USER ? "用户: " : "AI计划: ";
		return prefix + message.getText();
	}

	private static class PendingTurn {

		private final String userQuestion;

		private final StringBuilder planBuilder = new StringBuilder();

		private PendingTurn(String userQuestion) {
			this.userQuestion = userQuestion;
		}

	}

}
