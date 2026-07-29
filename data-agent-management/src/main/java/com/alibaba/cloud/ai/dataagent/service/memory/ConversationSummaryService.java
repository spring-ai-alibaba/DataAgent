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

import com.alibaba.cloud.ai.dataagent.entity.ConversationSummary;
import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationSummaryMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deterministically rebuilds a bounded summary from authoritative successful turns.
 */
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

	private final ConversationTurnMapper turnMapper;

	private final ConversationSummaryMapper summaryMapper;

	private final ChatSessionMapper chatSessionMapper;

	private final DataAgentProperties properties;

	@Transactional
	public void rebuild(String conversationId) {
		chatSessionMapper.lockBySessionId(conversationId);
		ConversationSummary current = summaryMapper.selectByConversationId(conversationId);
		List<ConversationTurn> turns = turnMapper.selectAllSuccessful(conversationId);
		rebuildFrom(conversationId, turns, current);
	}

	/**
	 * Loads the summary and recent turns from one authoritative snapshot. Rebuilding on
	 * demand closes the short interval between committing a turn and asynchronously
	 * projecting its outbox event.
	 */
	@Transactional
	public ContextWindow loadWindow(String conversationId) {
		chatSessionMapper.lockBySessionId(conversationId);
		ConversationSummary current = summaryMapper.selectByConversationId(conversationId);
		List<ConversationTurn> turns = turnMapper.selectAllSuccessful(conversationId);
		return rebuildFrom(conversationId, turns, current);
	}

	private ContextWindow rebuildFrom(String conversationId, List<ConversationTurn> turns, ConversationSummary current) {
		int recentTurns = Math.max(1, properties.getMemory().getRecentTurns());
		int summarizedCount = Math.max(0, turns.size() - recentTurns);
		List<ConversationTurn> recent = List.copyOf(turns.subList(summarizedCount, turns.size()));
		if (summarizedCount == 0) {
			if (current != null) {
				summaryMapper.deleteByConversationId(conversationId);
			}
			return new ContextWindow(null, recent);
		}

		List<ConversationTurn> summarizedTurns = turns.subList(0, summarizedCount);
		String boundedSummary = buildBoundedSummary(summarizedTurns);
		ConversationSummary value = ConversationSummary.builder()
			.conversationId(conversationId)
			.summaryText(boundedSummary)
			.coveredThroughTurnId(summarizedTurns.get(summarizedTurns.size() - 1).getId())
			.build();
		if (current == null || !Objects.equals(current.getSummaryText(), value.getSummaryText())
				|| !Objects.equals(current.getCoveredThroughTurnId(), value.getCoveredThroughTurnId())) {
			save(value, current);
		}
		return new ContextWindow(value, recent);
	}

	private void save(ConversationSummary value, ConversationSummary current) {
		if (current != null) {
			summaryMapper.update(value);
			return;
		}
		try {
			summaryMapper.insert(value);
		}
		catch (DuplicateKeyException concurrentInsert) {
			summaryMapper.update(value);
		}
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
		return "- 问题：" + StringUtils.abbreviate(query, 500) + "\n  已验证结果："
				+ StringUtils.abbreviate(result, 800) + '\n';
	}

	public record ContextWindow(ConversationSummary summary, List<ConversationTurn> recentTurns) {
	}

}
