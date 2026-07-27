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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.dto.search.AgentSearchRequest;
import com.alibaba.cloud.ai.dataagent.dto.search.HybridSearchRequest;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.HybridRetrievalStrategy;
import com.alibaba.cloud.ai.dataagent.service.vector.MetadataDocumentRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;

import static com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService.buildFilterExpressionString;

@Slf4j
@Service
public class AgentVectorStoreServiceImpl implements AgentVectorStoreService {

	private final VectorStore vectorStore;

	private final Optional<HybridRetrievalStrategy> hybridRetrievalStrategy;

	private final DataAgentProperties dataAgentProperties;

	private final DynamicFilterService dynamicFilterService;

	private final MetadataDocumentRetriever metadataDocumentRetriever;

	@Autowired
	public AgentVectorStoreServiceImpl(VectorStore vectorStore,
			Optional<HybridRetrievalStrategy> hybridRetrievalStrategy, DataAgentProperties dataAgentProperties,
			DynamicFilterService dynamicFilterService, MetadataDocumentRetriever metadataDocumentRetriever) {
		this.vectorStore = vectorStore;
		this.hybridRetrievalStrategy = hybridRetrievalStrategy;
		this.dataAgentProperties = dataAgentProperties;
		this.dynamicFilterService = dynamicFilterService;
		this.metadataDocumentRetriever = metadataDocumentRetriever;
		log.info("VectorStore type: {}", vectorStore.getClass().getSimpleName());
	}

	AgentVectorStoreServiceImpl(VectorStore vectorStore, Optional<HybridRetrievalStrategy> hybridRetrievalStrategy,
			DataAgentProperties dataAgentProperties, DynamicFilterService dynamicFilterService) {
		this(vectorStore, hybridRetrievalStrategy, dataAgentProperties, dynamicFilterService,
				new MetadataDocumentRetriever(new StandardEnvironment()));
	}

	@Override
	public List<Document> search(AgentSearchRequest searchRequest) {
		Assert.hasText(searchRequest.getAgentId(), "AgentId cannot be empty");
		Assert.hasText(searchRequest.getDocVectorType(), "DocVectorType cannot be empty");

		Filter.Expression filter = dynamicFilterService.buildDynamicFilter(searchRequest.getAgentId(),
				searchRequest.getDocVectorType());
		// 根据agentId vectorType找不到要 召回 的业务知识或者智能体知识
		if (filter == null) {
			log.warn(
					"Dynamic filter returned null (no valid ids), returning empty result directly.AgentId: {}, VectorType: {}",
					searchRequest.getAgentId(), searchRequest.getDocVectorType());
			return Collections.emptyList();
		}

		HybridSearchRequest hybridRequest = HybridSearchRequest.builder()
			.query(searchRequest.getQuery())
			.topK(searchRequest.getTopK())
			.similarityThreshold(searchRequest.getSimilarityThreshold())
			.filterExpression(filter)
			.build();

		if (dataAgentProperties.getVectorStore().isEnableHybridSearch() && hybridRetrievalStrategy.isPresent()) {
			return hybridRetrievalStrategy.get().retrieve(hybridRequest);
		}
		log.debug("Hybrid search is not enabled. use vector-search only");
		List<Document> results = vectorStore.similaritySearch(hybridRequest.toVectorSearchRequest());
		log.debug("Search completed with vectorType: {}, found {} documents for SearchRequest: {}",
				searchRequest.getDocVectorType(), results.size(), searchRequest);
		return results;

	}

	@Override
	public Boolean deleteDocumentsByVectorType(String agentId, String vectorType) throws Exception {
		Assert.notNull(agentId, "AgentId cannot be null.");
		Assert.notNull(vectorType, "VectorType cannot be null.");

		Map<String, Object> metadata = new HashMap<>(Map.ofEntries(Map.entry(Constant.AGENT_ID, agentId),
				Map.entry(DocumentMetadataConstant.VECTOR_TYPE, vectorType)));

		return this.deleteDocumentsByMetadata(agentId, metadata);
	}

	@Override
	public void addDocuments(String agentId, List<Document> documents) {
		Assert.notNull(agentId, "AgentId cannot be null.");
		Assert.notEmpty(documents, "Documents cannot be empty.");
		validateDocumentMetadata(agentId, documents);
		vectorStore.add(documents);
	}

	private void validateDocumentMetadata(String ownerId, List<Document> documents) {
		// 验证文档中 metadata 的一致性
		for (Document document : documents) {
			Assert.notNull(document.getMetadata(), "Document metadata cannot be null.");

			String vectorType = (String) document.getMetadata().get(DocumentMetadataConstant.VECTOR_TYPE);

			// 根据 vectorType 验证不同的字段
			if (DocumentMetadataConstant.TABLE.equals(vectorType)
					|| DocumentMetadataConstant.COLUMN.equals(vectorType)) {
				// 表和列必须包含 datasourceId
				Assert.isTrue(document.getMetadata().containsKey(Constant.DATASOURCE_ID),
						"Document metadata must contain datasourceId for TABLE/COLUMN type.");
				Assert.isTrue(ownerId.equals(document.getMetadata().get(Constant.DATASOURCE_ID).toString()),
						"Document metadata datasourceId does not match.");
			}
			else {
				// 知识库和业务术语必须包含 agentId
				Assert.isTrue(document.getMetadata().containsKey(Constant.AGENT_ID),
						"Document metadata must contain agentId.");
				Assert.isTrue(document.getMetadata().get(Constant.AGENT_ID).equals(ownerId),
						"Document metadata agentId does not match.");
			}
		}
	}

	@Override
	public Boolean deleteDocumentsByMetadata(Map<String, Object> metadata) {
		Assert.notNull(metadata, "Metadata cannot be null.");
		String filterExpression = buildFilterExpressionString(metadata);

		if (vectorStore instanceof MetadataAwareSimpleVectorStore simpleVectorStore) {
			int deleted = simpleVectorStore.deleteByMetadata(metadata);
			log.info("Deleted {} documents by metadata from SimpleVectorStore", deleted);
		}
		else if (vectorStore instanceof SimpleVectorStore) {
			batchDelDocumentsWithFilter(filterExpression);
		}
		else {
			try {
				vectorStore.delete(filterExpression);
			}
			catch (Exception e) {
				// Collection 不存在时（首次初始化），delete 会报错，这是正常的，忽略即可
				String msg = e.getMessage();
				if (msg != null && msg.contains("collection not found")) {
					log.info("Collection not found, skip delete (first initialization): {}", filterExpression);
				}
				else {
					throw e;
				}
			}
		}

		return true;
	}

	@Override
	public Boolean deleteDocumentsByMetadata(String agentId, Map<String, Object> metadata) {
		Assert.hasText(agentId, "AgentId cannot be empty.");
		Assert.notNull(metadata, "Metadata cannot be null.");
		// 在内部副本中补充 agentId，兼容 Map.of 等不可变入参，也避免修改调用方状态
		Map<String, Object> scopedMetadata = new HashMap<>(metadata);
		scopedMetadata.put(Constant.AGENT_ID, agentId);
		String filterExpression = buildFilterExpressionString(scopedMetadata);

		if (vectorStore instanceof MetadataAwareSimpleVectorStore simpleVectorStore) {
			int deleted = simpleVectorStore.deleteByMetadata(scopedMetadata);
			log.info("Deleted {} documents for agent {} from SimpleVectorStore", deleted, agentId);
		}
		else if (vectorStore instanceof SimpleVectorStore) {
			// Keep compatibility with user-supplied SimpleVectorStore beans.
			batchDelDocumentsWithFilter(filterExpression);
		}
		else {
			try {
				vectorStore.delete(filterExpression);
			}
			catch (Exception e) {
				String msg = e.getMessage();
				if (msg != null && msg.contains("collection not found")) {
					log.info("Collection not found, skip delete (first initialization): {}", filterExpression);
				}
				else {
					throw e;
				}
			}
		}

		return true;
	}

	private void batchDelDocumentsWithFilter(String filterExpression) {
		Set<String> seenDocumentIds = new HashSet<>();
		// 分批获取，因为Milvus等向量数据库的topK有限制
		List<Document> batch;
		int newDocumentsCount;
		int totalDeleted = 0;

		do {
			batch = metadataDocumentRetriever.find(vectorStore,
					new FilterExpressionTextParser().parse(filterExpression),
					dataAgentProperties.getVectorStore().getBatchDelTopkLimit());

			// 过滤掉已经处理过的文档，只删除未处理的文档
			List<String> idsToDelete = new ArrayList<>();
			newDocumentsCount = 0;

			for (Document doc : batch) {
				if (seenDocumentIds.add(doc.getId())) {
					// 如果add返回true，表示这是一个新的文档ID
					idsToDelete.add(doc.getId());
					newDocumentsCount++;
				}
			}

			// 删除这批新文档
			if (!idsToDelete.isEmpty()) {
				vectorStore.delete(idsToDelete);
				totalDeleted += idsToDelete.size();
			}

		}
		while (newDocumentsCount > 0); // 只有当获取到新文档时才继续循环

		log.info("Deleted {} documents with filter expression: {}", totalDeleted, filterExpression);
	}

	@Override
	public List<Document> getDocumentsForAgent(String agentId, String query, String vectorType) {
		// 使用全局默认配置
		int defaultTopK = dataAgentProperties.getVectorStore().getDefaultTopkLimit();
		double defaultThreshold = dataAgentProperties.getVectorStore().getDefaultSimilarityThreshold();

		return getDocumentsForAgent(agentId, query, vectorType, defaultTopK, defaultThreshold);
	}

	@Override
	public List<Document> getDocumentsForAgent(String agentId, String query, String vectorType, int topK,
			double threshold) {
		AgentSearchRequest searchRequest = AgentSearchRequest.builder()
			.agentId(agentId)
			.docVectorType(vectorType)
			.query(query)
			.topK(topK) // 使用传入的参数
			.similarityThreshold(threshold) // 使用传入的参数
			.build();
		return search(searchRequest);
	}

	@Override
	public List<Document> similaritySearch(String query, Filter.Expression filterExpression, int topK,
			double threshold) {
		Assert.hasText(query, "Query cannot be empty.");
		Assert.notNull(filterExpression, "Filter expression cannot be null.");
		Assert.isTrue(topK > 0, "TopK must be greater than zero.");
		Assert.isTrue(threshold >= 0.0 && threshold <= 1.0, "Similarity threshold must be between 0 and 1.");

		SearchRequest searchRequest = SearchRequest.builder()
			.query(query)
			.topK(topK)
			.filterExpression(filterExpression)
			.similarityThreshold(threshold)
			.build();
		return vectorStore.similaritySearch(searchRequest);
	}

	@Override
	public List<Document> getDocumentsOnlyByFilter(Filter.Expression filterExpression, Integer topK) {
		Assert.notNull(filterExpression, "filterExpression cannot be null.");
		if (topK == null)
			topK = dataAgentProperties.getVectorStore().getDefaultTopkLimit();
		return metadataDocumentRetriever.find(vectorStore, filterExpression, topK);
	}

	@Override
	public boolean hasSchemaDocuments(String datasourceId) {
		Filter.Expression filter = new FilterExpressionTextParser()
			.parse(buildFilterExpressionString(Map.of(Constant.DATASOURCE_ID, datasourceId)));
		return !getDocumentsOnlyByFilter(filter, 1).isEmpty();
	}

	@Override
	public boolean hasTableDocuments(Integer datasourceId, List<String> tableNames) {
		Assert.notNull(datasourceId, "DatasourceId cannot be null.");
		Assert.notEmpty(tableNames, "Table names cannot be empty.");
		Filter.Expression filter = DynamicFilterService.buildFilterExpressionForSearchTables(datasourceId, tableNames);
		List<Document> documents = getDocumentsOnlyByFilter(filter, tableNames.size() + 5);
		Set<String> storedTableNames = documents.stream()
			.map(document -> document.getMetadata().get(DocumentMetadataConstant.NAME))
			.filter(Objects::nonNull)
			.map(Object::toString)
			.collect(java.util.stream.Collectors.toSet());
		return storedTableNames.containsAll(tableNames);
	}

	@Override
	public void replaceDocumentsByMetadata(Map<String, Object> metadata, List<Document> documents) {
		Assert.notEmpty(metadata, "Metadata cannot be empty.");
		Assert.notEmpty(documents, "Replacement documents cannot be empty.");
		String filterExpression = buildFilterExpressionString(metadata);
		List<Document> oldDocuments = getDocumentsOnlyByFilter(new FilterExpressionTextParser().parse(filterExpression),
				dataAgentProperties.getVectorStore().getBatchDelTopkLimit());
		if (oldDocuments.size() >= dataAgentProperties.getVectorStore().getBatchDelTopkLimit()) {
			throw new IllegalStateException(
					"Too many existing vector documents to replace safely; increase batch-del-topk-limit");
		}

		Object ownerId = metadata.getOrDefault(Constant.AGENT_ID, metadata.get(Constant.DATASOURCE_ID));
		Assert.notNull(ownerId, "Replacement metadata must contain agentId or datasourceId.");
		validateReplacementMetadata(metadata, documents);
		validateDocumentMetadata(ownerId.toString(), documents);
		Set<String> oldDocumentIdSet = oldDocuments.stream()
			.map(Document::getId)
			.collect(java.util.stream.Collectors.toSet());
		List<Document> replacementDocuments = ensureFreshDocumentIds(documents, oldDocumentIdSet);
		List<String> newDocumentIds = replacementDocuments.stream().map(Document::getId).toList();
		Set<String> newDocumentIdSet = new HashSet<>(newDocumentIds);
		try {
			vectorStore.add(replacementDocuments);
			List<String> oldDocumentIds = oldDocuments.stream()
				.map(Document::getId)
				.filter(id -> !newDocumentIdSet.contains(id))
				.toList();
			if (!oldDocumentIds.isEmpty()) {
				vectorStore.delete(oldDocumentIds);
			}
		}
		catch (Exception replacementFailure) {
			try {
				if (!newDocumentIds.isEmpty()) {
					vectorStore.delete(newDocumentIds);
				}
			}
			catch (Exception rollbackFailure) {
				replacementFailure.addSuppressed(rollbackFailure);
			}
			throw replacementFailure;
		}
	}

	private void validateReplacementMetadata(Map<String, Object> identityMetadata, List<Document> documents) {
		for (Document document : documents) {
			for (Map.Entry<String, Object> identity : identityMetadata.entrySet()) {
				Assert.isTrue(Objects.equals(identity.getValue(), document.getMetadata().get(identity.getKey())),
						"Replacement document metadata does not match identity key: " + identity.getKey());
			}
		}
	}

	private List<Document> ensureFreshDocumentIds(List<Document> documents, Set<String> oldDocumentIds) {
		Set<String> assignedIds = new HashSet<>();
		return documents.stream().map(document -> {
			String id = document.getId();
			if (oldDocumentIds.contains(id) || !assignedIds.add(id)) {
				String freshId;
				do {
					freshId = UUID.randomUUID().toString();
				}
				while (oldDocumentIds.contains(freshId) || !assignedIds.add(freshId));
				return document.mutate().id(freshId).build();
			}
			return document;
		}).toList();
	}

}
