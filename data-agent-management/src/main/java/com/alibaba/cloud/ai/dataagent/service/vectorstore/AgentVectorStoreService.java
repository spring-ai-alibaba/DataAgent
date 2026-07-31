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

import com.alibaba.cloud.ai.dataagent.dto.search.AgentSearchRequest;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Map;

public interface AgentVectorStoreService {

	/**
	 * 查询某个Agent的文档 总入口
	 */
	List<Document> search(AgentSearchRequest searchRequest);

	Boolean deleteDocumentsByVectorType(String agentId, String vectorType) throws Exception;

	Boolean deleteDocumentsByMetadata(String agentId, Map<String, Object> metadata);

	/**
	 * @deprecated use {@link #deleteDocumentsByMetadata(String, Map)}.
	 */
	@Deprecated
	default Boolean deleteDocumentsByMetedata(String agentId, Map<String, Object> metadata) {
		return deleteDocumentsByMetadata(agentId, metadata);
	}

	Boolean deleteDocumentsByMetadata(Map<String, Object> metadata);

	/**
	 * Get documents for specified agent
	 */
	List<Document> getDocumentsForAgent(String agentId, String query, String vectorType);

	List<Document> getDocumentsForAgent(String agentId, String query, String vectorType, int topK, double threshold);

	/**
	 * Execute a semantic search with an already-built metadata filter.
	 */
	List<Document> similaritySearch(String query, Filter.Expression filterExpression, int topK, double threshold);

	// 通过元数据过滤精确查找
	List<Document> getDocumentsOnlyByFilter(Filter.Expression filterExpression, Integer topK);

	/**
	 * @deprecated use {@link #hasTableDocuments(Integer, List)}.
	 */
	@Deprecated
	boolean hasSchemaDocuments(String datasourceId);

	/**
	 * Check whether all selected tables have schema vectors for the datasource.
	 */
	boolean hasTableDocuments(Integer datasourceId, List<String> tableNames);

	/**
	 * Replace all documents selected by metadata without deleting the currently usable
	 * documents before the new vectors have been written successfully.
	 */
	void replaceDocumentsByMetadata(Map<String, Object> metadata, List<Document> documents);

	void addDocuments(String agentId, List<Document> documents);

}
