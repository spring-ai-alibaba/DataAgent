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
import com.alibaba.cloud.ai.dataagent.mapper.ConversationSummaryMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Deterministically rebuilds a bounded summary from authoritative successful turns.
 */
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

	private final ConversationTurnMapper turnMapper;

	private final ConversationSummaryMapper summaryMapper;

	private final DataAgentProperties properties;

	@Transactional
	public void rebuild(String conversationId) {
		List<ConversationTurn> turns = turnMapper.selectAllSuccessful(conversationId);
		int recentTurns = Math.max(1, properties.getMemory().getRecentTurns());
		int summarizedCount = Math.max(0, turns.size() - recentTurns);
		if (summarizedCount == 0) {
			summaryMapper.deleteByConversationId(conversationId);
			return;
		}

		List<ConversationTurn> summarizedTurns = turns.subList(0, summarizedCount);
		StringBuilder summary = new StringBuilder();
		for (ConversationTurn turn : summarizedTurns) {
			String query = StringUtils.defaultIfBlank(turn.getCanonicalQuery(), turn.getRawQuery());
			String result = StringUtils.defaultIfBlank(turn.getResultSummary(), "(无可用结果摘要)");
			summary.append("- 问题：")
				.append(StringUtils.abbreviate(query, 500))
				.append("\n  已验证结果：")
				.append(StringUtils.abbreviate(result, 800))
				.append('\n');
		}
		String boundedSummary = StringUtils.abbreviate(summary.toString(),
				Math.max(500, properties.getMemory().getMaxSummaryLength()));
		ConversationSummary value = ConversationSummary.builder()
			.conversationId(conversationId)
			.summaryText(boundedSummary)
			.coveredThroughTurnId(summarizedTurns.get(summarizedTurns.size() - 1).getId())
			.build();
		if (summaryMapper.selectByConversationId(conversationId) == null) {
			summaryMapper.insert(value);
		}
		else {
			summaryMapper.update(value);
		}
	}

}
