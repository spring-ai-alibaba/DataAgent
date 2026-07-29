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
package com.alibaba.cloud.ai.dataagent.service.memory.semantic;

import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Recalls successful turns from other conversations and revalidates their
 * relational ownership boundary after semantic retrieval.
 */
@Service
@RequiredArgsConstructor
public class EpisodicMemoryService {

	private final ConversationTurnMapper turnMapper;

	private final MemoryVectorIndexService vectorIndexService;

	private final DataAgentProperties properties;

	public List<ConversationTurn> recallRelevant(String query, Long ownerId, Integer agentId, Integer datasourceId,
			String currentConversationId) {
		if (ownerId == null || datasourceId == null) {
			return List.of();
		}
		int limit = Math.max(1, properties.getMemory().getEpisodicTopK());
		List<String> recalledIds = vectorIndexService.recallTurnIds(query, ownerId, agentId, datasourceId, limit);
		List<ConversationTurn> candidates = recalledIds.isEmpty()
				? turnMapper.selectRecentSuccessfulByOwner(ownerId, agentId, datasourceId, limit * 2)
				: turnMapper.selectSuccessfulByIds(recalledIds);
		Map<String, ConversationTurn> filtered = new LinkedHashMap<>();
		for (ConversationTurn candidate : candidates) {
			boolean sameScope = ownerId.equals(candidate.getOwnerId()) && agentId.equals(candidate.getAgentId())
					&& datasourceId.equals(candidate.getDatasourceId());
			if (sameScope && !Objects.equals(currentConversationId, candidate.getConversationId())) {
				filtered.putIfAbsent(candidate.getId(), candidate);
			}
		}
		return filtered.values().stream().limit(limit).toList();
	}

}
