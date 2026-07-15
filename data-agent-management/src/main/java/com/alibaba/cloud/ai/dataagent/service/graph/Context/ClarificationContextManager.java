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

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ClarificationContextManager {

	private final Map<String, ClarificationState> stateMap = new ConcurrentHashMap<>();

	public void startClarification(String threadId, String originalQuery, String question, int clarificationCount) {
		if (StringUtils.isAnyBlank(threadId, originalQuery, question)) {
			return;
		}
		stateMap.compute(threadId, (key, existing) -> {
			ClarificationState state = existing == null ? new ClarificationState(originalQuery.trim()) : existing;
			state.originalQuery = StringUtils.defaultIfBlank(state.originalQuery, originalQuery.trim());
			state.pendingQuestion = question.trim();
			state.awaitingClarification = true;
			state.clarificationCount = Math.max(clarificationCount, state.clarificationCount);
			return state;
		});
	}

	public Optional<ClarificationStateSnapshot> submitAnswer(String threadId, String answer) {
		if (StringUtils.isAnyBlank(threadId, answer)) {
			return Optional.empty();
		}
		ClarificationState state = stateMap.get(threadId);
		if (state == null || !state.awaitingClarification || StringUtils.isBlank(state.pendingQuestion)) {
			return Optional.empty();
		}
		synchronized (state) {
			if (!state.awaitingClarification || StringUtils.isBlank(state.pendingQuestion)) {
				return Optional.empty();
			}
			state.history.add(new ClarificationTurn(state.pendingQuestion, answer.trim()));
			state.pendingQuestion = null;
			state.awaitingClarification = false;
			return Optional.of(toSnapshot(state));
		}
	}

	public boolean isAwaitingClarification(String threadId) {
		ClarificationState state = stateMap.get(threadId);
		return state != null && state.awaitingClarification;
	}

	public int getClarificationCount(String threadId) {
		ClarificationState state = stateMap.get(threadId);
		return state == null ? 0 : state.clarificationCount;
	}

	public Optional<ClarificationStateSnapshot> getSnapshot(String threadId) {
		ClarificationState state = stateMap.get(threadId);
		return state == null ? Optional.empty() : Optional.of(toSnapshot(state));
	}

	public void clear(String threadId) {
		if (StringUtils.isBlank(threadId)) {
			return;
		}
		stateMap.remove(threadId);
	}

	public String buildRefinedQuery(String threadId) {
		ClarificationState state = stateMap.get(threadId);
		if (state == null) {
			return "";
		}
		synchronized (state) {
			StringBuilder builder = new StringBuilder();
			builder.append("原始问题：").append(state.originalQuery).append('\n');
			for (int i = 0; i < state.history.size(); i++) {
				ClarificationTurn turn = state.history.get(i);
				builder.append("澄清问题").append(i + 1).append("：").append(turn.question()).append('\n');
				builder.append("用户补充").append(i + 1).append("：").append(turn.answer()).append('\n');
			}
			builder.append("请基于以上原始问题和补充条件，继续完成数据分析查询。");
			return builder.toString();
		}
	}

	private ClarificationStateSnapshot toSnapshot(ClarificationState state) {
		synchronized (state) {
			return new ClarificationStateSnapshot(state.originalQuery, state.clarificationCount,
					state.awaitingClarification, state.pendingQuestion, new ArrayList<>(state.history));
		}
	}

	public record ClarificationStateSnapshot(String originalQuery, int clarificationCount, boolean awaitingClarification,
			String pendingQuestion, List<ClarificationTurn> history) {
	}

	public record ClarificationTurn(String question, String answer) {
	}

	private static class ClarificationState {

		private String originalQuery;

		private int clarificationCount;

		private boolean awaitingClarification;

		private String pendingQuestion;

		private final List<ClarificationTurn> history = new ArrayList<>();

		private ClarificationState(String originalQuery) {
			this.originalQuery = originalQuery;
		}

	}

}
