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
package com.alibaba.cloud.ai.service.impl;

import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.constant.Constant;
import com.alibaba.cloud.ai.entity.AgentKnowledge;
import com.alibaba.cloud.ai.request.SchemaInitRequest;
import com.alibaba.cloud.ai.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.connector.bo.DbQueryParameter;
import com.alibaba.cloud.ai.connector.bo.TableInfoBO;
import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.util.SchemaProcessorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Vector Storage Service Specializes in handling agent-related vector storage
 * operations, ensuring data isolation
 */
// todo: 需要与AgentVectorStoreService和AgentKnowledgeService合并
@Service
public class AgentVectorService {

	private static final Logger log = LoggerFactory.getLogger(AgentVectorService.class);

	@Autowired
	private AgentVectorStoreService vectorStoreService;

	/**
	 * Add knowledge document to vector store for agent
	 * @param agentId agent ID
	 * @param knowledge knowledge content
	 */
	public void addKnowledgeToVector(Long agentId, AgentKnowledge knowledge) {
		try {
			String agentIdStr = String.valueOf(agentId);
			log.info("Adding knowledge to vector store for agent: {}, knowledge ID: {}", agentIdStr, knowledge.getId());

			// Create document
			Document document = createDocumentFromKnowledge(agentIdStr, knowledge);

			// Add to vector store
			vectorStoreService.addDocuments(agentIdStr, List.of(document));

			log.info("Successfully added knowledge to vector store for agent: {}", agentIdStr);
		}
		catch (Exception e) {
			log.error("Failed to add knowledge to vector store for agent: {}, knowledge ID: {}", agentId,
					knowledge.getId(), e);
			throw new RuntimeException("Failed to add knowledge to vector store: " + e.getMessage(), e);
		}
	}

	/**
	 * Delete specific knowledge document of agent
	 * @param agentId agent ID
	 * @param knowledgeId knowledge ID
	 */
	public void deleteKnowledgeFromVector(Long agentId, Integer knowledgeId) {
		try {
			String agentIdStr = String.valueOf(agentId);
			String documentId = agentIdStr + ":knowledge:" + knowledgeId;

			log.info("Deleting knowledge from vector store for agent: {}, knowledge ID: {}", agentIdStr, knowledgeId);

			Map<String, Object> metadata = new HashMap<>(Map.ofEntries(Map.entry(Constant.AGENT_ID, agentIdStr),
					Map.entry(Constant.KNOWLEDGE_ID, knowledgeId)));

			vectorStoreService.deleteDocumentsByMetedata(agentIdStr, metadata);

			log.info("Successfully deleted knowledge from vector store for agent: {}", agentIdStr);
		}
		catch (Exception e) {
			log.error("Failed to delete knowledge from vector store for agent: {}, knowledge ID: {}", agentId,
					knowledgeId, e);
			throw new RuntimeException("Failed to delete knowledge from vector store: " + e.getMessage(), e);
		}
	}

	/**
	 * Delete all vector data of agent
	 * @param agentId agent ID
	 */
	public void deleteAllVectorDataForAgent(Long agentId) {
		try {
			String agentIdStr = String.valueOf(agentId);
			log.info("Deleting all vector data for agent: {}", agentIdStr);

			vectorStoreService.deleteDocumentsByMetedata(String.valueOf(agentId), new HashMap<>());

			log.info("Successfully deleted all vector data for agent: {}", agentIdStr);
		}
		catch (Exception e) {
			log.error("Failed to delete all vector data for agent: {}", agentId, e);
			throw new RuntimeException("Failed to delete all vector data: " + e.getMessage(), e);
		}
	}

	public boolean isAlreadyInitialized(Long agentId) {
		try {
			String agentIdStr = String.valueOf(agentId);
			return vectorStoreService.hasDocuments(agentIdStr);
		}
		catch (Exception e) {
			log.error("Failed to check initialization status for agent: {}, assuming not initialized", agentId, e);
			return false;
		}
	}

	/**
	 * Get agent vector storage statistics
	 * @param agentId agent ID
	 * @return statistics
	 */
	public Map<String, Object> getVectorStatistics(Long agentId) {
		try {
			String agentIdStr = String.valueOf(agentId);
			Map<String, Object> stats = new HashMap<>();

			int docNum = vectorStoreService.estimateDocuments(agentIdStr);

			stats.put("docNum", docNum);
			stats.put("agentId", agentId);
			stats.put("hasData", docNum > 0);

			log.info("Successfully retrieved vector statistics for agent: {}, detail: {}", agentIdStr, stats);

			return stats;
		}
		catch (Exception e) {
			log.error("Failed to get vector statistics for agent: {}", agentId, e);
			throw new RuntimeException("Failed to get vector statistics: " + e.getMessage(), e);
		}
	}

	/**
	 * Create Document from AgentKnowledge
	 */
	private Document createDocumentFromKnowledge(String agentId, AgentKnowledge knowledge) {
		String documentId = agentId + ":knowledge:" + knowledge.getId();
		String content = knowledge.getContent();
		if (content == null || content.trim().isEmpty()) {
			content = knowledge.getTitle(); // If content is empty, use title
		}

		Map<String, Object> metadata = new HashMap<>();
		metadata.put("agentId", agentId);
		metadata.put("knowledgeId", knowledge.getId());
		metadata.put("title", knowledge.getTitle());
		metadata.put("type", knowledge.getType());
		metadata.put("category", knowledge.getCategory());
		metadata.put("tags", knowledge.getTags());
		metadata.put("status", knowledge.getStatus());
		metadata.put("vectorType", "knowledge:" + knowledge.getType());
		metadata.put("sourceUrl", knowledge.getSourceUrl());
		metadata.put("fileType", knowledge.getFileType());
		metadata.put("embeddingStatus", knowledge.getEmbeddingStatus());
		metadata.put("createTime", knowledge.getCreateTime());

		return new Document(documentId, content, metadata);
	}

	@Autowired
	private DatasourceService datasourceService;

	private final com.alibaba.cloud.ai.connector.accessor.Accessor dbAccessor;

	public AgentVectorService(AccessorFactory accessorFactory, DbConfig dbConfig) {
		this.dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
	}

	/**
	 * Get list of data sources configured for agent Query data source information
	 * associated with agent from database
	 */
	public List<Map<String, Object>> getAgentDatasources(Long agentId) {
		try {
			log.info("Getting datasources for agent: {}", agentId);

			// Call DatasourceService to get data sources associated with agent
			List<com.alibaba.cloud.ai.entity.AgentDatasource> agentDatasources = datasourceService
				.getAgentDatasource(agentId.intValue());

			List<Map<String, Object>> datasources = new ArrayList<>();

			for (com.alibaba.cloud.ai.entity.AgentDatasource agentDatasource : agentDatasources) {
				// Only return active status data sources
				if (agentDatasource.getIsActive() != null) {
					com.alibaba.cloud.ai.entity.Datasource datasource = agentDatasource.getDatasource();
					if (datasource != null) {
						Map<String, Object> dsMap = new HashMap<>();
						dsMap.put("id", datasource.getId());
						dsMap.put("name", datasource.getName());
						dsMap.put("type", datasource.getType());
						dsMap.put("host", datasource.getHost());
						dsMap.put("port", datasource.getPort());
						dsMap.put("databaseName", datasource.getDatabaseName());
						dsMap.put("username", datasource.getUsername());
						dsMap.put("password", datasource.getPassword()); // Add password
																			// field
						dsMap.put("connectionUrl", datasource.getConnectionUrl());
						dsMap.put("status", datasource.getStatus());
						dsMap.put("testStatus", datasource.getTestStatus());
						dsMap.put("description", datasource.getDescription());
						dsMap.put("isActive", agentDatasource.getIsActive());
						dsMap.put("createTime", agentDatasource.getCreateTime());

						datasources.add(dsMap);
					}
				}
			}

			log.info("Found {} active datasources for agent: {}", datasources.size(), agentId);
			return datasources;

		}
		catch (Exception e) {
			log.error("Failed to get datasources for agent: {}", agentId, e);
			throw new RuntimeException("Failed to get datasources: " + e.getMessage(), e);
		}
	}

	/**
	 * Get table list of data source
	 * @param datasourceId data source ID
	 * @return list of table names
	 */
	public List<String> getDatasourceTables(Integer datasourceId) {
		try {
			log.info("Getting tables for datasource: {}", datasourceId);

			// Get data source information
			com.alibaba.cloud.ai.entity.Datasource datasource = datasourceService.getDatasourceById(datasourceId);
			if (datasource == null) {
				throw new RuntimeException("Datasource not found with id: " + datasourceId);
			}

			// Check data source type, currently only supports MySQL
			// if (!"mysql".equalsIgnoreCase(datasource.getType())) {
			// log.warn("Unsupported datasource type: {}, only MySQL is supported
			// currently", datasource.getType());
			// return new ArrayList<>();
			// }

			// Create database configuration
			DbConfig dbConfig = SchemaProcessorUtil.createDbConfigFromDatasource(datasource);

			// Create query parameters
			DbQueryParameter queryParam = DbQueryParameter.from(dbConfig);
			queryParam.setSchema(datasource.getDatabaseName());

			// Query table list
			List<TableInfoBO> tableInfoList = dbAccessor.showTables(dbConfig, queryParam);

			// Extract table names
			List<String> tableNames = tableInfoList.stream()
				.map(TableInfoBO::getName)
				.filter(name -> name != null && !name.trim().isEmpty())
				.sorted()
				.toList();

			log.info("Found {} tables for datasource: {}", tableNames.size(), datasourceId);
			return tableNames;

		}
		catch (Exception e) {
			log.error("Failed to get tables for datasource: {}, reason: {}", datasourceId, e.getMessage());
			throw new RuntimeException("Failed to get tables: " + e.getMessage(), e);
		}
	}

	/**
	 * Initialize database Schema for agent using data source ID
	 * @param agentId agent ID
	 * @param datasourceId data source ID
	 * @param tables table list
	 * @return success status
	 */
	public Boolean initializeSchemaForAgentWithDatasource(Long agentId, Integer datasourceId, List<String> tables) {
		try {
			String agentIdStr = String.valueOf(agentId);
			log.info("Initializing schema for agent: {} with datasource: {}, tables: {}", agentIdStr, datasourceId,
					tables);

			// Get data source information
			com.alibaba.cloud.ai.entity.Datasource datasource = datasourceService.getDatasourceById(datasourceId);
			if (datasource == null) {
				throw new RuntimeException("Datasource not found with id: " + datasourceId);
			}

			// Create database configuration
			DbConfig dbConfig = SchemaProcessorUtil.createDbConfigFromDatasource(datasource);

			// Create SchemaInitRequest
			SchemaInitRequest schemaInitRequest = new SchemaInitRequest();
			schemaInitRequest.setDbConfig(dbConfig);
			schemaInitRequest.setTables(tables);

			log.info("Created SchemaInitRequest for agent: {}, dbConfig: {}, tables: {}", agentIdStr, dbConfig, tables);

			// Call the original initialization method
			return vectorStoreService.schema(agentIdStr, schemaInitRequest);

		}
		catch (Exception e) {
			log.error("Failed to initialize schema for agent: {} with datasource: {}", agentId, datasourceId, e);
			throw new RuntimeException("Failed to initialize schema for agent " + agentId + ": " + e.getMessage(), e);
		}
	}

}
