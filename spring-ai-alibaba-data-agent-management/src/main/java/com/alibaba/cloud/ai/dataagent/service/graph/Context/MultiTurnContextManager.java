/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.graph.Context;

import com.alibaba.cloud.ai.dataagent.config.DataAgentProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
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

	// todo：考虑持久化存储
	private final Map<String, Deque<ConversationTurn>> history = new ConcurrentHashMap<>();

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
		Deque<ConversationTurn> deque = history.computeIfAbsent(threadId, k -> new ArrayDeque<>());
		synchronized (deque) {
			while (deque.size() >= properties.getMaxturnhistory()) {
				deque.pollFirst();
			}
			deque.addLast(new ConversationTurn(pending.userQuestion, trimmedPlan));
		}
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
		Deque<ConversationTurn> deque = history.get(threadId);
		if (deque == null || deque.isEmpty()) {
			return;
		}
		ConversationTurn lastTurn;
		synchronized (deque) {
			lastTurn = deque.pollLast();
		}
		if (lastTurn != null) {
			pendingTurns.put(threadId, new PendingTurn(lastTurn.userQuestion()));
		}
	}

	/**
	 * Build multi-turn context string for prompt injection.
	 * @param threadId conversation thread id
	 * @return formatted history string
	 */
	public String buildContext(String threadId) {
		Deque<ConversationTurn> deque = history.get(threadId);
		if (deque == null || deque.isEmpty()) {
			return "(无)";
		}
		return deque.stream()
			.map(turn -> "用户: " + turn.userQuestion() + "\nAI计划: " + turn.plan())
			.collect(Collectors.joining("\n"));
	}

	private record ConversationTurn(String userQuestion, String plan) {
	}

	private static class PendingTurn {

		private final String userQuestion;

		private final StringBuilder planBuilder = new StringBuilder();

		private PendingTurn(String userQuestion) {
			this.userQuestion = userQuestion;
		}

	}

}
