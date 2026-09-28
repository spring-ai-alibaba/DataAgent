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
package com.alibaba.cloud.ai.dataagent.service.memory.longterm;

import com.alibaba.cloud.ai.dataagent.entity.MemoryItem;
import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.enums.MemoryKind;
import com.alibaba.cloud.ai.dataagent.enums.MemoryScopeType;
import com.alibaba.cloud.ai.dataagent.enums.MemoryStatus;
import com.alibaba.cloud.ai.dataagent.enums.TurnStatus;
import com.alibaba.cloud.ai.dataagent.exception.MemoryConflictException;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.mapper.DatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.MemoryItemMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryEventType;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryOutboxService;
import com.alibaba.cloud.ai.dataagent.service.memory.semantic.MemoryVectorIndexService;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Review-gated source of truth for stable cross-session memories.
 */
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

	private static final int MAX_MEMORY_VALUE_LENGTH = 16_000;

	private final MemoryItemMapper mapper;

	private final MemoryOutboxService outboxService;

	private final MemoryVectorIndexService vectorIndexService;

	private final DataAgentProperties properties;

	private final ConversationTurnMapper turnMapper;

	private final AgentDatasourceMapper agentDatasourceMapper;

	private final DatasourceMapper datasourceMapper;

	private final SchemaService schemaService;

	@Transactional
	public MemoryItem createCandidate(MemoryItem item) {
		validate(item);
		item.setStatus(MemoryStatus.CANDIDATE);
		mapper.insert(item);
		return mapper.selectById(item.getId());
	}

	@Transactional
	public MemoryItem confirm(Long id) {
		MemoryItem item = requireItemForUpdate(id);
		if (item.getStatus() == MemoryStatus.CONFIRMED) {
			return item;
		}
		if (item.getStatus() != MemoryStatus.CANDIDATE) {
			throw new MemoryConflictException("Only CANDIDATE memory can be confirmed");
		}
		validateCandidateStillCurrent(item);
		ensureIdentity(item);
		MemoryItem active = mapper.selectConfirmedByIdentityHashForUpdate(item.getIdentityHash());
		if (item.getSupersedesId() != null) {
			MemoryItem superseded = requireItemForUpdate(item.getSupersedesId());
			ensureIdentity(superseded);
			if (superseded.getStatus() != MemoryStatus.CONFIRMED) {
				throw new IllegalArgumentException("Superseded memory must be CONFIRMED");
			}
			if (!item.getIdentityHash().equals(superseded.getIdentityHash())) {
				throw new IllegalArgumentException("Superseded memory must have the same agent, scope, kind and key");
			}
			if (active != null && !active.getId().equals(superseded.getId())) {
				throw new MemoryConflictException("Another confirmed memory already owns this identity");
			}
			if (isExpired(superseded)) {
				invalidateNoLongerApplicable(superseded);
			}
			else if (mapper.markSuperseded(superseded.getId()) != 1) {
				throw new MemoryConflictException("Superseded memory changed concurrently");
			}
			else {
				enqueueInvalidation(superseded);
			}
		}
		else if (active != null) {
			if (isExpired(active) || hasStaleSchema(active, item)) {
				invalidateNoLongerApplicable(active);
			}
			else {
				throw new MemoryConflictException("A confirmed memory already exists for this scope, kind and key");
			}
		}
		try {
			if (mapper.confirmCandidate(id) != 1) {
				throw new MemoryConflictException("Memory candidate changed concurrently");
			}
		}
		catch (DataIntegrityViolationException conflict) {
			throw new MemoryConflictException("A confirmed memory already exists for this scope, kind and key",
					conflict);
		}
		outboxService.enqueue("MEMORY_ITEM", id.toString(), MemoryEventType.MEMORY_CONFIRMED, null);
		return mapper.selectById(id);
	}

	@Transactional
	public MemoryItem invalidate(Long id) {
		MemoryItem item = requireItemForUpdate(id);
		if (item.getStatus() == MemoryStatus.INVALIDATED) {
			return item;
		}
		if (item.getStatus() == MemoryStatus.SUPERSEDED) {
			throw new MemoryConflictException("SUPERSEDED memory cannot be invalidated");
		}
		if (mapper.invalidate(id) != 1) {
			throw new MemoryConflictException("Memory changed concurrently");
		}
		outboxService.enqueue("MEMORY_ITEM", id.toString(), MemoryEventType.MEMORY_INVALIDATED, null);
		item.setStatus(MemoryStatus.INVALIDATED);
		item.setActiveIdentityHash(null);
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
		String schemaRevision = schemaService.getSchemaRevision(datasourceId);
		return recall(ownerId, agentId, datasourceId, schemaRevision, limit);
	}

	public List<MemoryItem> recallRelevant(String query, Long ownerId, Integer agentId, Integer datasourceId,
			int limit) {
		Assert.notNull(agentId, "agentId is required");
		String schemaRevision = schemaService.getSchemaRevision(datasourceId);
		List<MemoryItem> relational = recall(ownerId, agentId, datasourceId, schemaRevision, limit);
		List<Long> semanticIds = vectorIndexService.recallMemoryItemIds(query, ownerId, agentId, datasourceId, limit);
		if (semanticIds.isEmpty()) {
			return relational;
		}
		Map<Long, MemoryItem> semanticById = mapper.selectConfirmedByIds(semanticIds)
			.stream()
			.filter(item -> isAllowed(item, ownerId, agentId, datasourceId, schemaRevision))
			.collect(Collectors.toMap(MemoryItem::getId, Function.identity(), (left, right) -> left));
		List<MemoryItem> semantic = semanticIds.stream().map(semanticById::get).filter(Objects::nonNull).toList();
		LinkedHashMap<Long, MemoryItem> merged = new LinkedHashMap<>();
		// Current-datasource rules remain deterministic and highest priority. Within the
		// remaining budget, semantically relevant memories should displace unrelated
		// relational fallback rows.
		relational.stream()
			.filter(item -> item.getScopeType() == MemoryScopeType.DATASOURCE)
			.forEach(item -> merged.put(item.getId(), item));
		semantic.forEach(item -> merged.putIfAbsent(item.getId(), item));
		relational.forEach(item -> merged.putIfAbsent(item.getId(), item));
		return merged.values().stream().limit(Math.max(1, limit)).toList();
	}

	private List<MemoryItem> recall(Long ownerId, Integer agentId, Integer datasourceId, String schemaRevision,
			int limit) {
		return mapper.selectConfirmedForContext(ownerId, agentId, datasourceId, schemaRevision, Math.max(1, limit));
	}

	@Transactional
	public void deleteByConversation(String conversationId) {
		List<MemoryItem> items = mapper.selectByConversationId(conversationId);
		enqueueInvalidations(items);
		mapper.deleteByConversationId(conversationId);
	}

	@Transactional
	public void deleteByAgent(Integer agentId) {
		List<MemoryItem> items = mapper.selectByAgentId(agentId, null);
		enqueueInvalidations(items);
		mapper.deleteByAgentId(agentId);
	}

	@Transactional
	public void deleteByDatasource(Integer datasourceId) {
		List<MemoryItem> items = mapper.selectByDatasourceId(datasourceId);
		enqueueInvalidations(items);
		mapper.deleteByDatasourceId(datasourceId);
	}

	@Transactional
	public void deleteByAgentAndDatasource(Integer agentId, Integer datasourceId) {
		List<MemoryItem> items = mapper.selectByAgentAndDatasource(agentId, datasourceId);
		enqueueInvalidations(items);
		mapper.deleteByAgentAndDatasource(agentId, datasourceId);
	}

	private void enqueueInvalidations(List<MemoryItem> items) {
		items.forEach(this::enqueueInvalidation);
	}

	private void enqueueInvalidation(MemoryItem item) {
		outboxService.enqueue("MEMORY_ITEM", item.getId().toString(), MemoryEventType.MEMORY_INVALIDATED, null);
	}

	private void invalidateNoLongerApplicable(MemoryItem item) {
		if (mapper.invalidate(item.getId()) != 1) {
			throw new MemoryConflictException("Obsolete memory changed concurrently");
		}
		enqueueInvalidation(item);
	}

	private boolean isExpired(MemoryItem item) {
		return item.getValidUntil() != null && !item.getValidUntil().isAfter(LocalDateTime.now());
	}

	private boolean hasStaleSchema(MemoryItem active, MemoryItem replacement) {
		return replacement.getScopeType() == MemoryScopeType.DATASOURCE
				&& isSchemaSensitive(replacement.getMemoryKind())
				&& !StringUtils.equals(active.getSchemaFingerprint(), replacement.getSchemaFingerprint());
	}

	private MemoryItem requireItem(Long id) {
		Assert.notNull(id, "memory item id is required");
		MemoryItem item = mapper.selectById(id);
		if (item == null) {
			throw new IllegalArgumentException("Memory item not found: " + id);
		}
		return item;
	}

	private MemoryItem requireItemForUpdate(Long id) {
		Assert.notNull(id, "memory item id is required");
		MemoryItem item = mapper.selectByIdForUpdate(id);
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
		if ("null".equals(item.getValueJson().trim())) {
			throw new IllegalArgumentException("memory value must not be JSON null");
		}
		if (item.getValueJson().length() > MAX_MEMORY_VALUE_LENGTH) {
			throw new IllegalArgumentException(
					"memory value must not exceed " + MAX_MEMORY_VALUE_LENGTH + " characters");
		}
		if (item.getMemoryKey().length() > 255) {
			throw new IllegalArgumentException("memory key must not exceed 255 characters");
		}
		item.setMemoryKey(item.getMemoryKey().trim());
		if (item.getConfidence() == null) {
			item.setConfidence(BigDecimal.ONE);
		}
		if (item.getConfidence().compareTo(BigDecimal.ZERO) < 0 || item.getConfidence().compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("memory confidence must be between 0 and 1");
		}
		if (isExpired(item)) {
			throw new IllegalArgumentException("memory validUntil must be in the future");
		}
		if (item.getScopeType() == MemoryScopeType.USER_AGENT && !properties.getMemory().isUserScopeEnabled()) {
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
			item.setDatasourceId(null);
		}
		if (item.getScopeType() == MemoryScopeType.DATASOURCE) {
			validateDatasourceRevision(item, source);
		}
		if (item.getScopeType() == MemoryScopeType.AGENT) {
			item.setOwnerId(null);
			item.setDatasourceId(null);
			item.setSchemaFingerprint(null);
		}
		else if (item.getScopeType() == MemoryScopeType.DATASOURCE) {
			item.setOwnerId(null);
		}
		else {
			item.setSchemaFingerprint(null);
		}
		item.setIdentityHash(null);
		ensureIdentity(item);
		item.setActiveIdentityHash(null);
	}

	private void validateCandidateStillCurrent(MemoryItem item) {
		if (isExpired(item)) {
			throw new MemoryConflictException("Memory candidate expired before confirmation");
		}
		if (item.getScopeType() != MemoryScopeType.DATASOURCE) {
			return;
		}
		if (agentDatasourceMapper.selectByAgentIdAndDatasourceId(item.getAgentId().longValue(),
				item.getDatasourceId()) == null) {
			throw new MemoryConflictException("Datasource is no longer bound to the agent");
		}
		Datasource datasource = datasourceMapper.selectByIdForUpdate(item.getDatasourceId());
		if (datasource == null) {
			throw new MemoryConflictException("Datasource no longer exists");
		}
		if (isSchemaSensitive(item.getMemoryKind())
				&& !StringUtils.equals(item.getSchemaFingerprint(), datasource.getSchemaRevision())) {
			throw new MemoryConflictException("Datasource schema changed after the memory candidate was created");
		}
	}

	private void validateDatasourceRevision(MemoryItem item, ConversationTurn source) {
		if (source != null && !item.getDatasourceId().equals(source.getDatasourceId())) {
			throw new IllegalArgumentException("Datasource memory must use the source turn datasource");
		}
		if (!isSchemaSensitive(item.getMemoryKind())) {
			item.setSchemaFingerprint(null);
			return;
		}
		String currentRevision = schemaService.getSchemaRevision(item.getDatasourceId());
		if (StringUtils.isBlank(currentRevision)) {
			throw new IllegalArgumentException(
					"Schema-sensitive datasource memory requires an initialized datasource schema");
		}
		if (source != null && !currentRevision.equals(source.getSchemaFingerprint())) {
			throw new IllegalArgumentException("Source turn schema is no longer current for the datasource");
		}
		item.setSchemaFingerprint(currentRevision);
	}

	private boolean isAllowed(MemoryItem item, Long ownerId, Integer agentId, Integer datasourceId,
			String schemaRevision) {
		if (!agentId.equals(item.getAgentId())) {
			return false;
		}
		return switch (item.getScopeType()) {
			case AGENT -> true;
			case DATASOURCE -> datasourceId != null && datasourceId.equals(item.getDatasourceId())
					&& (!isSchemaSensitive(item.getMemoryKind())
							|| StringUtils.equals(schemaRevision, item.getSchemaFingerprint()));
			case USER_AGENT -> ownerId != null && ownerId.equals(item.getOwnerId());
		};
	}

	private boolean isSchemaSensitive(MemoryKind memoryKind) {
		return memoryKind == MemoryKind.CORRECTION || memoryKind == MemoryKind.QUERY_PATTERN;
	}

	private void ensureIdentity(MemoryItem item) {
		if (StringUtils.isNotBlank(item.getIdentityHash())) {
			return;
		}
		String scopeOwner = item.getScopeType() == MemoryScopeType.USER_AGENT ? String.valueOf(item.getOwnerId()) : "-";
		String scopeDatasource = item.getScopeType() == MemoryScopeType.DATASOURCE
				? String.valueOf(item.getDatasourceId()) : "-";
		String identity = item.getScopeType() + "|" + scopeOwner + "|" + item.getAgentId() + "|" + scopeDatasource + "|"
				+ item.getMemoryKind() + "|" + item.getMemoryKey().trim().toLowerCase(Locale.ROOT);
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			item.setIdentityHash(HexFormat.of().formatHex(digest.digest(identity.getBytes(StandardCharsets.UTF_8))));
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to hash memory identity", e);
		}
	}

}
