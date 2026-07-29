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

import com.alibaba.cloud.ai.dataagent.entity.MemoryItem;
import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.enums.MemoryScopeType;
import com.alibaba.cloud.ai.dataagent.enums.MemoryStatus;
import com.alibaba.cloud.ai.dataagent.enums.TurnStatus;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.mapper.MemoryItemMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.List;

/**
 * Review-gated source of truth for stable cross-session memories.
 */
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

	private final MemoryItemMapper mapper;

	private final MemoryOutboxService outboxService;

	private final MemoryVectorIndexService vectorIndexService;

	private final DataAgentProperties properties;

	private final ConversationTurnMapper turnMapper;

	private final AgentDatasourceMapper agentDatasourceMapper;

	@Transactional
	public MemoryItem createCandidate(MemoryItem item) {
		validate(item);
		item.setStatus(MemoryStatus.CANDIDATE);
		mapper.insert(item);
		return mapper.selectById(item.getId());
	}

	@Transactional
	public MemoryItem confirm(Long id) {
		MemoryItem item = requireItem(id);
		if (item.getStatus() == MemoryStatus.CONFIRMED) {
			return item;
		}
		if (item.getStatus() != MemoryStatus.CANDIDATE) {
			throw new IllegalStateException("Only CANDIDATE memory can be confirmed");
		}
		if (item.getSupersedesId() != null) {
			MemoryItem superseded = requireItem(item.getSupersedesId());
			if (superseded.getStatus() != MemoryStatus.CONFIRMED) {
				throw new IllegalArgumentException("Superseded memory must be CONFIRMED");
			}
			if (!item.getAgentId().equals(superseded.getAgentId()) || !sameScope(item, superseded)
					|| !item.getMemoryKey().equals(superseded.getMemoryKey())) {
				throw new IllegalArgumentException("Superseded memory must have the same agent, scope and key");
			}
			mapper.markSuperseded(superseded.getId());
			outboxService.enqueue("MEMORY_ITEM", superseded.getId().toString(),
					MemoryEventType.MEMORY_INVALIDATED, null);
		}
		mapper.updateStatus(id, MemoryStatus.CONFIRMED);
		outboxService.enqueue("MEMORY_ITEM", id.toString(), MemoryEventType.MEMORY_CONFIRMED, null);
		return mapper.selectById(id);
	}

	@Transactional
	public MemoryItem invalidate(Long id) {
		MemoryItem item = requireItem(id);
		mapper.updateStatus(id, MemoryStatus.INVALIDATED);
		outboxService.enqueue("MEMORY_ITEM", id.toString(), MemoryEventType.MEMORY_INVALIDATED, null);
		item.setStatus(MemoryStatus.INVALIDATED);
		return item;
	}

	public MemoryItem findById(Long id) {
		return requireItem(id);
	}

	public List<MemoryItem> list(Integer agentId, MemoryStatus status) {
		Assert.notNull(agentId, "agentId is required");
		return mapper.selectByAgentId(agentId, status);
	}

	public List<MemoryItem> recall(Long ownerId, Integer agentId, Integer datasourceId, int limit) {
		Assert.notNull(agentId, "agentId is required");
		return mapper.selectConfirmedForContext(ownerId, agentId, datasourceId, Math.max(1, limit));
	}

	public List<MemoryItem> recallRelevant(String query, Long ownerId, Integer agentId, Integer datasourceId, int limit) {
		List<MemoryItem> relational = recall(ownerId, agentId, datasourceId, limit);
		List<Long> semanticIds = vectorIndexService.recallMemoryItemIds(query, ownerId, agentId, datasourceId, limit);
		if (semanticIds.isEmpty()) {
			return relational;
		}
		List<MemoryItem> semantic = mapper.selectConfirmedByIds(semanticIds)
			.stream()
			.filter(item -> isAllowed(item, ownerId, agentId, datasourceId))
			.toList();
		java.util.LinkedHashMap<Long, MemoryItem> merged = new java.util.LinkedHashMap<>();
		semantic.forEach(item -> merged.put(item.getId(), item));
		relational.forEach(item -> merged.putIfAbsent(item.getId(), item));
		return merged.values().stream().limit(Math.max(1, limit)).toList();
	}

	private MemoryItem requireItem(Long id) {
		Assert.notNull(id, "memory item id is required");
		MemoryItem item = mapper.selectById(id);
		if (item == null) {
			throw new IllegalArgumentException("Memory item not found: " + id);
		}
		return item;
	}

	private void validate(MemoryItem item) {
		Assert.notNull(item, "memory item is required");
		Assert.notNull(item.getScopeType(), "memory scope is required");
		Assert.notNull(item.getMemoryKind(), "memory kind is required");
		Assert.notNull(item.getAgentId(), "agentId is required");
		Assert.hasText(item.getMemoryKey(), "memory key is required");
		Assert.hasText(item.getValueJson(), "memory value is required");
		if (item.getMemoryKey().length() > 255) {
			throw new IllegalArgumentException("memory key must not exceed 255 characters");
		}
		if (item.getConfidence() == null) {
			item.setConfidence(BigDecimal.ONE);
		}
		if (item.getConfidence().compareTo(BigDecimal.ZERO) < 0
				|| item.getConfidence().compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("memory confidence must be between 0 and 1");
		}
		if (item.getScopeType() == MemoryScopeType.USER_AGENT
				&& !properties.getMemory().isUserScopeEnabled()) {
			throw new IllegalArgumentException(
					"USER_AGENT memory is disabled until a trusted server-derived owner identity is configured");
		}
		if (item.getScopeType() == MemoryScopeType.DATASOURCE && item.getDatasourceId() == null) {
			throw new IllegalArgumentException("DATASOURCE memory requires datasourceId");
		}
		if (item.getScopeType() == MemoryScopeType.DATASOURCE && agentDatasourceMapper
			.selectByAgentIdAndDatasourceId(item.getAgentId().longValue(), item.getDatasourceId()) == null) {
			throw new IllegalArgumentException("Datasource memory must reference a datasource bound to the agent");
		}
		if (item.getSchemaFingerprint() != null && StringUtils.isBlank(item.getSchemaFingerprint())) {
			item.setSchemaFingerprint(null);
		}
		ConversationTurn source = null;
		if (StringUtils.isNotBlank(item.getSourceTurnId())) {
			source = turnMapper.selectById(item.getSourceTurnId());
			if (source == null || source.getStatus() != TurnStatus.SUCCEEDED
					|| !Boolean.TRUE.equals(source.getMemoryEligible())
					|| !item.getAgentId().equals(source.getAgentId())) {
				throw new IllegalArgumentException("sourceTurnId must reference a successful turn of the same agent");
			}
		}
		if (item.getScopeType() == MemoryScopeType.USER_AGENT) {
			if (source == null || source.getOwnerId() == null) {
				throw new IllegalArgumentException(
						"USER_AGENT memory requires a verified source turn with server-derived owner identity");
			}
			item.setOwnerId(source.getOwnerId());
		}
		if (item.getScopeType() == MemoryScopeType.DATASOURCE && source != null) {
			if (!item.getDatasourceId().equals(source.getDatasourceId())) {
				throw new IllegalArgumentException("Datasource memory must use the source turn datasource");
			}
			if (item.getSchemaFingerprint() == null) {
				item.setSchemaFingerprint(source.getSchemaFingerprint());
			}
		}
	}

	private boolean isAllowed(MemoryItem item, Long ownerId, Integer agentId, Integer datasourceId) {
		if (!agentId.equals(item.getAgentId())) {
			return false;
		}
		return switch (item.getScopeType()) {
			case AGENT -> true;
			case DATASOURCE -> datasourceId != null && datasourceId.equals(item.getDatasourceId());
			case USER_AGENT -> ownerId != null && ownerId.equals(item.getOwnerId());
		};
	}

	private boolean sameScope(MemoryItem first, MemoryItem second) {
		if (first.getScopeType() != second.getScopeType()) {
			return false;
		}
		return switch (first.getScopeType()) {
			case AGENT -> true;
			case DATASOURCE -> java.util.Objects.equals(first.getDatasourceId(), second.getDatasourceId());
			case USER_AGENT -> java.util.Objects.equals(first.getOwnerId(), second.getOwnerId());
		};
	}

}
