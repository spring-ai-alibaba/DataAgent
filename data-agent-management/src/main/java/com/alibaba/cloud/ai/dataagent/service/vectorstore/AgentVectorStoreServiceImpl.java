/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;

import static com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService.buildFilterExpressionString;

@Slf4j
@Service
public class AgentVectorStoreServiceImpl implements AgentVectorStoreService {

	private static final String DEFAULT = "default";

	private final VectorStore vectorStore;

	private final Optional<HybridRetrievalStrategy> hybridRetrievalStrategy;

	private final DataAgentProperties dataAgentProperties;

	private final DynamicFilterService dynamicFilterService;

	public AgentVectorStoreServiceImpl(VectorStore vectorStore,
			Optional<HybridRetrievalStrategy> hybridRetrievalStrategy, DataAgentProperties dataAgentProperties,
			DynamicFilterService dynamicFilterService) {
		this.vectorStore = vectorStore;
		this.hybridRetrievalStrategy = hybridRetrievalStrategy;
		this.dataAgentProperties = dataAgentProperties;
		this.dynamicFilterService = dynamicFilterService;
		log.info("VectorStore type: {}", vectorStore.getClass().getSimpleName());
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

		return this.deleteDocumentsByMetedata(agentId, metadata);
	}

	@Override
	public void addDocuments(String agentId, List<Document> documents) {
		Assert.notNull(agentId, "AgentId cannot be null.");
		Assert.notEmpty(documents, "Documents cannot be empty.");
		// 校验文档中metadata中包含的agentId
		for (Document document : documents) {
			Assert.notNull(document.getMetadata(), "Document metadata cannot be null.");
			Assert.isTrue(document.getMetadata().containsKey(Constant.AGENT_ID),
					"Document metadata must contain agentId.");
			Assert.isTrue(document.getMetadata().get(Constant.AGENT_ID).equals(agentId),
					"Document metadata agentId does not match.");
		}
		vectorStore.add(documents);
	}

	@Override
	public Boolean deleteDocumentsByMetedata(String agentId, Map<String, Object> metadata) {
		Assert.hasText(agentId, "AgentId cannot be empty.");
		Assert.notNull(metadata, "Metadata cannot be null.");
		// 添加agentId元数据过滤条件, 用于删除指定agentId下的所有数据，因为metadata中用户调用可能忘记添加agentId
		metadata.put(Constant.AGENT_ID, agentId);
		String filterExpression = buildFilterExpressionString(metadata);

		// es的可以直接元数据删除
		if (vectorStore instanceof SimpleVectorStore) {
			// 目前SimpleVectorStore不支持通过元数据删除，使用会抛出UnsupportedOperationException,现在是通过id删除
			batchDelDocumentsWithFilter(filterExpression);
		}
		else {
			vectorStore.delete(filterExpression);
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
			batch = vectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
				.query(DEFAULT)// 使用默认的查询字符串，因为有的嵌入模型不支持空字符串
				.filterExpression(filterExpression)
				.similarityThreshold(0.0)// 设置最低相似度阈值以获取元数据匹配的所有文档
				.topK(dataAgentProperties.getVectorStore().getBatchDelTopkLimit())
				.build());

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
	public List<Document> getDocumentsOnlyByFilter(Filter.Expression filterExpression, Integer topK) {
		Assert.notNull(filterExpression, "filterExpression cannot be null.");
		if (topK == null)
			topK = dataAgentProperties.getVectorStore().getDefaultTopkLimit();
		SearchRequest searchRequest = SearchRequest.builder()
			.query(DEFAULT)
			.topK(topK)
			.filterExpression(filterExpression)
			.similarityThreshold(0.0)
			.build();
		return vectorStore.similaritySearch(searchRequest);
	}

	@Override
	public boolean hasDocuments(String agentId) {
		// 类似 MySQL 的 LIMIT 1,只检查是否存在文档
		List<Document> docs = vectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
			.query(DEFAULT)// 使用默认的查询字符串，因为有的嵌入模型不支持空字符串
			.filterExpression(buildFilterExpressionString(Map.of(Constant.AGENT_ID, agentId)))
			.topK(1) // 只获取1个文档
			.similarityThreshold(0.0)
			.build());
		return !docs.isEmpty();
	}

}
