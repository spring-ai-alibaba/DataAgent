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
import com.alibaba.cloud.ai.graph.store.StoreSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministically rebuilds a bounded summary from authoritative successful turns and
 * caches the rebuildable projection through the framework {@link Store}.
 */
@Service
@Slf4j
public class ConversationSummaryService {

	static final String SUMMARY_KEY = "rolling-summary";

	private static final List<String> SUMMARY_NAMESPACE_PREFIX = List.of("data-agent", "conversation-summary");

	private final ConversationTurnMapper turnMapper;

	private final ChatSessionMapper chatSessionMapper;

	private final DataAgentProperties properties;

	private final Store store;

	public ConversationSummaryService(ConversationTurnMapper turnMapper, ChatSessionMapper chatSessionMapper,
			DataAgentProperties properties, @Qualifier("conversationSummaryStore") Store store) {
		this.turnMapper = turnMapper;
		this.chatSessionMapper = chatSessionMapper;
		this.properties = properties;
		this.store = store;
	}

	@Transactional
	public void rebuild(String conversationId) {
		if (!conversationId.equals(chatSessionMapper.lockBySessionId(conversationId))) {
			// A delayed TURN_COMPLETED event must not recreate a derived summary after
			// the conversation was deleted. Cleanup is idempotent and also removes any
			// residue left by an earlier failed forget projection.
			delete(conversationId);
			return;
		}
		Summary current = loadProjection(conversationId);
		List<ConversationTurn> turns = turnMapper.selectContextTimeline(conversationId);
		rebuildFrom(conversationId, turns, current);
	}

	public Summary load(String conversationId) {
		if (StringUtils.isBlank(conversationId)) {
			return null;
		}
		Summary current = null;
		try {
			current = loadProjection(conversationId);
		}
		catch (RuntimeException ex) {
			log.warn("Unable to load the summary projection for {}; rebuilding from durable turns", conversationId, ex);
		}

		String expectedBoundary;
		try {
			expectedBoundary = turnMapper.selectSummaryBoundaryTurnId(conversationId,
					Math.max(1, properties.resolveRecentTurns()));
		}
		catch (RuntimeException ex) {
			log.warn("Unable to verify the conversation summary boundary for {}; using the available projection",
					conversationId, ex);
			return current;
		}
		if (StringUtils.isBlank(expectedBoundary)) {
			deleteProjectionFailSoft(conversationId, current);
			return null;
		}
		if (current != null && expectedBoundary.equals(current.coveredThroughTurnId())) {
			return current;
		}

		Summary rebuilt;
		try {
			rebuilt = calculate(turnMapper.selectContextTimeline(conversationId));
		}
		catch (RuntimeException ex) {
			log.warn("Unable to rebuild the conversation summary for {}; using the available projection",
					conversationId, ex);
			return current;
		}
		try {
			synchronizeProjection(conversationId, current, rebuilt);
		}
		catch (RuntimeException ex) {
			// Correctness comes from the durable turn log. A failed cache write must not
			// discard the summary that was just rebuilt for this request.
			log.warn("Unable to cache the rebuilt conversation summary for {}", conversationId, ex);
		}
		return rebuilt;
	}

	public void delete(String conversationId) {
		if (StringUtils.isNotBlank(conversationId)) {
			store.deleteItem(summaryNamespace(conversationId), SUMMARY_KEY);
		}
	}

	private Summary rebuildFrom(String conversationId, List<ConversationTurn> turns, Summary current) {
		Summary value = calculate(turns);
		synchronizeProjection(conversationId, current, value);
		return value;
	}

	private Summary calculate(List<ConversationTurn> turns) {
		int recentTurns = properties.resolveRecentTurns();
		int summarizedCount = Math.max(0, turns.size() - recentTurns);
		if (summarizedCount == 0) {
			return null;
		}

		List<ConversationTurn> historicalWindow = turns.subList(0, summarizedCount);
		List<ConversationTurn> verifiedHistoricalTurns = historicalWindow.stream()
			.filter(turn -> Boolean.TRUE.equals(turn.getMemoryEligible()))
			.toList();
		if (verifiedHistoricalTurns.isEmpty()) {
			return null;
		}
		String boundedSummary = buildBoundedSummary(verifiedHistoricalTurns);
		return new Summary(boundedSummary, historicalWindow.get(historicalWindow.size() - 1).getId());
	}

	private void synchronizeProjection(String conversationId, Summary current, Summary value) {
		if (value == null) {
			if (current != null) {
				delete(conversationId);
			}
			return;
		}
		if (!Objects.equals(current, value)) {
			store.putItem(StoreItem.of(summaryNamespace(conversationId), SUMMARY_KEY,
					Map.of("summaryText", value.summaryText(), "coveredThroughTurnId", value.coveredThroughTurnId())));
			enforceCacheBound();
		}
	}

	private synchronized void enforceCacheBound() {
		long excess = store.size() - properties.getMemory().getSummaryCacheMaxEntries();
		if (excess <= 0) {
			return;
		}
		int evictionLimit = (int) Math.min(excess, Integer.MAX_VALUE);
		List<StoreItem> oldest = store
			.searchItems(StoreSearchRequest.builder()
				.namespace(SUMMARY_NAMESPACE_PREFIX)
				.sortFields(List.of("updatedAt"))
				.ascending(true)
				.limit(evictionLimit)
				.build())
			.getItems();
		oldest.forEach(item -> store.deleteItem(item.getNamespace(), item.getKey()));
	}

	private Summary loadProjection(String conversationId) {
		return store.getItem(summaryNamespace(conversationId), SUMMARY_KEY).map(StoreItem::getValue).map(value -> {
			String summaryText = Objects.toString(value.get("summaryText"), null);
			String coveredThroughTurnId = Objects.toString(value.get("coveredThroughTurnId"), null);
			if (StringUtils.isBlank(summaryText) || StringUtils.isBlank(coveredThroughTurnId)) {
				return null;
			}
			return new Summary(summaryText, coveredThroughTurnId);
		}).orElse(null);
	}

	private void deleteProjectionFailSoft(String conversationId, Summary current) {
		if (current == null) {
			return;
		}
		try {
			delete(conversationId);
		}
		catch (RuntimeException ex) {
			log.warn("Unable to delete the obsolete conversation summary projection for {}", conversationId, ex);
		}
	}

	static List<String> summaryNamespace(String conversationId) {
		return List.of(SUMMARY_NAMESPACE_PREFIX.get(0), SUMMARY_NAMESPACE_PREFIX.get(1), conversationId);
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
