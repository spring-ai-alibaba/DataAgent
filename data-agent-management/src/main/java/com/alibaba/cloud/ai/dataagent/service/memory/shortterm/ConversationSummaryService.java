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
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministically rebuilds a bounded summary from authoritative successful turns and
 * persists that cross-session projection through the framework {@link Store}.
 */
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

	static final String SUMMARY_KEY = "rolling-summary";

	private final ConversationTurnMapper turnMapper;

	private final ChatSessionMapper chatSessionMapper;

	private final DataAgentProperties properties;

	private final Store store;

	@Transactional
	public void rebuild(String conversationId) {
		chatSessionMapper.lockBySessionId(conversationId);
		Summary current = load(conversationId);
		List<ConversationTurn> turns = turnMapper.selectAllSuccessful(conversationId);
		rebuildFrom(conversationId, turns, current);
	}

	public Summary load(String conversationId) {
		if (StringUtils.isBlank(conversationId)) {
			return null;
		}
		return store.getItem(summaryNamespace(conversationId), SUMMARY_KEY).map(StoreItem::getValue).map(value -> {
			String summaryText = Objects.toString(value.get("summaryText"), null);
			String coveredThroughTurnId = Objects.toString(value.get("coveredThroughTurnId"), null);
			if (StringUtils.isBlank(summaryText) || StringUtils.isBlank(coveredThroughTurnId)) {
				return null;
			}
			return new Summary(summaryText, coveredThroughTurnId);
		}).orElse(null);
	}

	public void delete(String conversationId) {
		if (StringUtils.isNotBlank(conversationId)) {
			store.deleteItem(summaryNamespace(conversationId), SUMMARY_KEY);
		}
	}

	private Summary rebuildFrom(String conversationId, List<ConversationTurn> turns, Summary current) {
		int recentTurns = properties.resolveRecentTurns();
		int summarizedCount = Math.max(0, turns.size() - recentTurns);
		if (summarizedCount == 0) {
			if (current != null) {
				delete(conversationId);
			}
			return null;
		}

		List<ConversationTurn> summarizedTurns = turns.subList(0, summarizedCount);
		String boundedSummary = buildBoundedSummary(summarizedTurns);
		Summary value = new Summary(boundedSummary, summarizedTurns.get(summarizedTurns.size() - 1).getId());
		if (!Objects.equals(current, value)) {
			store.putItem(StoreItem.of(summaryNamespace(conversationId), SUMMARY_KEY,
					Map.of("summaryText", value.summaryText(), "coveredThroughTurnId", value.coveredThroughTurnId())));
		}
		return value;
	}

	static List<String> summaryNamespace(String conversationId) {
		return List.of("data-agent", "conversation-summary", conversationId);
	}

	private String buildBoundedSummary(List<ConversationTurn> summarizedTurns) {
		int maxLength = Math.max(500, properties.getMemory().getMaxSummaryLength());
		String omissionMarker = "- （较早历史已因上下文预算省略）\n";
		List<String> selectedNewestFirst = new ArrayList<>();
		int selectedLength = 0;
		boolean omitted = false;
		for (int i = summarizedTurns.size() - 1; i >= 0; i--) {
			String entry = entry(summarizedTurns.get(i));
			if (selectedLength + entry.length() <= maxLength) {
				selectedNewestFirst.add(entry);
				selectedLength += entry.length();
			}
			else {
				omitted = true;
				break;
			}
		}
		if (omitted) {
			int contentBudget = maxLength - omissionMarker.length();
			while (!selectedNewestFirst.isEmpty() && selectedLength > contentBudget) {
				selectedLength -= selectedNewestFirst.remove(selectedNewestFirst.size() - 1).length();
			}
			if (selectedNewestFirst.isEmpty()) {
				String newest = entry(summarizedTurns.get(summarizedTurns.size() - 1));
				selectedNewestFirst.add(StringUtils.abbreviate(newest, contentBudget));
			}
		}
		Collections.reverse(selectedNewestFirst);
		StringBuilder summary = new StringBuilder(maxLength);
		if (omitted) {
			summary.append(omissionMarker);
		}
		selectedNewestFirst.forEach(summary::append);
		return summary.toString();
	}

	private String entry(ConversationTurn turn) {
		String query = StringUtils.defaultIfBlank(turn.getCanonicalQuery(), turn.getRawQuery());
		String result = StringUtils.defaultIfBlank(turn.getResultSummary(), "(无可用结果摘要)");
		return "- 问题：" + StringUtils.abbreviate(query, 500) + "\n  已验证结果：" + StringUtils.abbreviate(result, 800) + '\n';
	}

	public record Summary(String summaryText, String coveredThroughTurnId) {
	}

}
