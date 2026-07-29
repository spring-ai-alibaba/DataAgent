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

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.entity.MemoryItem;
import com.alibaba.cloud.ai.dataagent.enums.MemoryScopeType;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional semantic index. Relational records are always re-checked after recall.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryVectorIndexService {

	private static final String TURN_DOCUMENT_PREFIX = "memory-turn-";

	private static final String ITEM_DOCUMENT_PREFIX = "memory-item-";

	private final VectorStore vectorStore;

	private final DataAgentProperties properties;

	public void indexTurn(ConversationTurn turn) {
		if (!enabled() || !properties.getMemory().isUserScopeEnabled() || turn.getOwnerId() == null
				|| !Boolean.TRUE.equals(turn.getMemoryEligible())) {
			return;
		}
		String text = StringUtils.defaultIfBlank(turn.getCanonicalQuery(), turn.getRawQuery()) + "\n"
				+ StringUtils.defaultString(turn.getResultSummary());
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.EPISODIC_MEMORY);
		metadata.put(Constant.AGENT_ID, turn.getAgentId().toString());
		metadata.put(DocumentMetadataConstant.MEMORY_OWNER_ID, turn.getOwnerId().toString());
		metadata.put(DocumentMetadataConstant.TURN_ID, turn.getId());
		if (turn.getDatasourceId() != null) {
			metadata.put(Constant.DATASOURCE_ID, turn.getDatasourceId().toString());
		}
		replace(new Document(TURN_DOCUMENT_PREFIX + turn.getId(), text, metadata));
	}

	public void indexMemoryItem(MemoryItem item) {
		if (!enabled() || (item.getScopeType() == MemoryScopeType.USER_AGENT
				&& (!properties.getMemory().isUserScopeEnabled() || item.getOwnerId() == null))) {
			return;
		}
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.LONG_TERM_MEMORY);
		metadata.put(Constant.AGENT_ID, item.getAgentId().toString());
		metadata.put(DocumentMetadataConstant.MEMORY_ITEM_ID, item.getId());
		metadata.put(DocumentMetadataConstant.MEMORY_SCOPE_TYPE, item.getScopeType().name());
		if (item.getDatasourceId() != null) {
			metadata.put(Constant.DATASOURCE_ID, item.getDatasourceId().toString());
		}
		if (item.getOwnerId() != null) {
			metadata.put(DocumentMetadataConstant.MEMORY_OWNER_ID, item.getOwnerId().toString());
		}
		replace(new Document(ITEM_DOCUMENT_PREFIX + item.getId(), item.getMemoryKey() + "\n" + item.getValueJson(),
				metadata));
	}

	public List<String> recallTurnIds(String query, Long ownerId, Integer agentId, Integer datasourceId, int topK) {
		if (!enabled() || ownerId == null || StringUtils.isBlank(query)) {
			return List.of();
		}
		Filter.Expression filter = scopedFilter(DocumentMetadataConstant.EPISODIC_MEMORY, ownerId, agentId,
				datasourceId);
		return search(query, filter, topK).stream()
			.map(document -> document.getMetadata().get(DocumentMetadataConstant.TURN_ID))
			.filter(java.util.Objects::nonNull)
			.map(Object::toString)
			.distinct()
			.toList();
	}

	public List<Long> recallMemoryItemIds(String query, Long ownerId, Integer agentId, Integer datasourceId, int topK) {
		if (!enabled() || StringUtils.isBlank(query)) {
			return List.of();
		}
		Filter.Expression filter = longTermFilter(ownerId, agentId, datasourceId);
		List<Long> ids = new ArrayList<>();
		for (Document document : search(query, filter, topK)) {
			Object id = document.getMetadata().get(DocumentMetadataConstant.MEMORY_ITEM_ID);
			if (id instanceof Number number) {
				ids.add(number.longValue());
			}
			else if (id != null) {
				ids.add(Long.valueOf(id.toString()));
			}
		}
		return ids.stream().distinct().toList();
	}

	public void deleteTurn(String turnId) {
		delete(TURN_DOCUMENT_PREFIX + turnId);
	}

	public void deleteMemoryItem(Long itemId) {
		delete(ITEM_DOCUMENT_PREFIX + itemId);
	}

	private List<Document> search(String query, Filter.Expression filter, int topK) {
		try {
			return vectorStore.similaritySearch(SearchRequest.builder()
				.query(query)
				.topK(Math.max(1, topK))
				.similarityThreshold(properties.getMemory().getVectorSimilarityThreshold())
				.filterExpression(filter)
				.build());
		}
		catch (RuntimeException e) {
			log.warn("Memory vector recall unavailable; continuing with relational memory: {}", e.getMessage());
			return List.of();
		}
	}

	private Filter.Expression scopedFilter(String vectorType, Long ownerId, Integer agentId, Integer datasourceId) {
		List<Filter.Expression> filters = new ArrayList<>();
		filters.add(new FilterExpressionBuilder().eq(DocumentMetadataConstant.VECTOR_TYPE, vectorType).build());
		filters.add(new FilterExpressionBuilder().eq(Constant.AGENT_ID, agentId.toString()).build());
		if (ownerId != null) {
			filters.add(new FilterExpressionBuilder().eq(DocumentMetadataConstant.MEMORY_OWNER_ID, ownerId.toString())
				.build());
		}
		if (datasourceId != null) {
			filters.add(new FilterExpressionBuilder().eq(Constant.DATASOURCE_ID, datasourceId.toString()).build());
		}
		Filter.Expression result = filters.get(0);
		for (int i = 1; i < filters.size(); i++) {
			result = new Filter.Expression(Filter.ExpressionType.AND, result, filters.get(i));
		}
		return result;
	}

	private Filter.Expression longTermFilter(Long ownerId, Integer agentId, Integer datasourceId) {
		FilterExpressionBuilder builder = new FilterExpressionBuilder();
		Filter.Expression base = and(
				builder.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.LONG_TERM_MEMORY).build(),
				builder.eq(Constant.AGENT_ID, agentId.toString()).build());
		Filter.Expression allowedScopes = builder
			.eq(DocumentMetadataConstant.MEMORY_SCOPE_TYPE, MemoryScopeType.AGENT.name())
			.build();
		if (datasourceId != null) {
			Filter.Expression datasourceScope = and(
					builder.eq(DocumentMetadataConstant.MEMORY_SCOPE_TYPE, MemoryScopeType.DATASOURCE.name()).build(),
					builder.eq(Constant.DATASOURCE_ID, datasourceId.toString()).build());
			allowedScopes = new Filter.Expression(Filter.ExpressionType.OR, allowedScopes, datasourceScope);
		}
		if (properties.getMemory().isUserScopeEnabled() && ownerId != null) {
			Filter.Expression userScope = and(
					builder.eq(DocumentMetadataConstant.MEMORY_SCOPE_TYPE, MemoryScopeType.USER_AGENT.name()).build(),
					builder.eq(DocumentMetadataConstant.MEMORY_OWNER_ID, ownerId.toString()).build());
			allowedScopes = new Filter.Expression(Filter.ExpressionType.OR, allowedScopes, userScope);
		}
		return and(base, allowedScopes);
	}

	private Filter.Expression and(Filter.Expression left, Filter.Expression right) {
		return new Filter.Expression(Filter.ExpressionType.AND, left, right);
	}

	private void replace(Document document) {
		try {
			vectorStore.delete(List.of(document.getId()));
			vectorStore.add(List.of(document));
		}
		catch (RuntimeException e) {
			throw new IllegalStateException("Failed to update memory vector index", e);
		}
	}

	private void delete(String id) {
		if (!enabled()) {
			return;
		}
		try {
			vectorStore.delete(List.of(id));
		}
		catch (RuntimeException e) {
			throw new IllegalStateException("Failed to delete memory vector document " + id, e);
		}
	}

	private boolean enabled() {
		return properties.getMemory().isVectorIndexEnabled();
	}

}
