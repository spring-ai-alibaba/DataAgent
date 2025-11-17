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

import com.alibaba.cloud.ai.dataagent.common.request.AgentSearchRequest;
import com.alibaba.cloud.ai.dataagent.common.request.SchemaInitRequest;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

public interface AgentVectorStoreService {

	/**
	 * 查询某个Agent的文档 总入口
	 */
	List<Document> search(AgentSearchRequest searchRequest);

	// TODO 2025-11-10 后续应该移动到 schemaservice，本service 只负责数据处理
	Boolean schema(String agentId, SchemaInitRequest schemaInitRequest) throws Exception;

	Boolean deleteDocumentsByVectorType(String agentId, String vectorType) throws Exception;

	Boolean deleteDocumentsByMetedata(String agentId, Map<String, Object> metadata) throws Exception;

	/**
	 * Get documents for specified agent
	 */
	List<Document> getDocumentsForAgent(String agentId, String query, String vectorType);

	List<Document> getDocumentsOnlyByFilter(String filterExpression, int topK);

	List<Document> getTableDocuments(String agentId, List<String> tableNames);

	List<Document> getColumnDocuments(String agentId, String upstreamTableName, List<String> columnNames);

	boolean hasDocuments(String agentId);

	void addDocuments(String agentId, List<Document> documents);

}
