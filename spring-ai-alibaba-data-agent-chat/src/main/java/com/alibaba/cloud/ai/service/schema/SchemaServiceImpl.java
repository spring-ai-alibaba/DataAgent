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
package com.alibaba.cloud.ai.service.schema;

import com.alibaba.cloud.ai.constant.Constant;
import com.alibaba.cloud.ai.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.dto.schema.ColumnDTO;
import com.alibaba.cloud.ai.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dto.schema.TableDTO;
import com.alibaba.cloud.ai.service.vectorstore.AgentVectorStoreService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Schema service base class, providing common method implementations
 */
@Slf4j
@Service
public class SchemaServiceImpl implements SchemaService {

	protected final ObjectMapper objectMapper;

	/**
	 * Vector storage service
	 */
	protected final AgentVectorStoreService vectorStoreService;

	public SchemaServiceImpl(ObjectMapper objectMapper, AgentVectorStoreService vectorStoreService) {
		this.objectMapper = objectMapper;
		this.vectorStoreService = vectorStoreService;
	}

	/**
	 * Build schema based on RAG - supports agent isolation
	 * @param agentId agent ID
	 * @param query query
	 * @param keywords keyword list
	 * @param dbConfig Database configuration
	 * @return SchemaDTO
	 */
	@Override
	public SchemaDTO mixRagForAgent(String agentId, String query, List<String> keywords, DbConfig dbConfig) {
		SchemaDTO schemaDTO = new SchemaDTO();
		extractDatabaseName(schemaDTO, dbConfig); // Set database name or schema name

		// Get table documents
		List<Document> tableDocuments = new ArrayList<>(getTableDocumentsForAgent(agentId, query));

		// Get column documents
		List<List<Document>> columnDocumentList = getColumnDocumentsByKeywordsForAgent(agentId, keywords);

		buildSchemaFromDocuments(agentId, columnDocumentList, tableDocuments, schemaDTO);

		return schemaDTO;
	}

	@Override
	public void buildSchemaFromDocuments(String agentId, List<List<Document>> columnDocumentList,
			List<Document> tableDocuments, SchemaDTO schemaDTO) {
		// Process column weights and sort by table association
		updateAndSortColumnScoresByTableWeights(columnDocumentList, tableDocuments);

		// Initialize column selector, TODO upper limit 100 has issues
		Map<String, Document> weightedColumns = selectColumnsByRoundRobin(columnDocumentList, 100);

		// 如果外键关系是"订单表.订单ID=订单详情表.订单ID"，那么 relatedNamesFromForeignKeys
		// 将包含"订单表.订单ID"和"订单详情表.订单ID"
		Set<String> relatedNamesFromForeignKeys = extractRelatedNamesFromForeignKeys(tableDocuments);

		// Build table list
		List<TableDTO> tableList = buildTableListFromDocuments(tableDocuments);

		// Supplement missing foreign key corresponding tables
		expandTableDocumentsWithForeignKeys(agentId, tableDocuments, relatedNamesFromForeignKeys);
		expandColumnDocumentsWithForeignKeys(agentId, weightedColumns, relatedNamesFromForeignKeys);

		// Attach weighted columns to corresponding tables
		attachColumnsToTables(weightedColumns, tableList);

		// Finally assemble SchemaDTO
		schemaDTO.setTable(tableList);

		Set<String> foreignKeys = tableDocuments.stream()
			.map(doc -> (String) doc.getMetadata().getOrDefault("foreignKey", ""))
			.flatMap(fk -> Arrays.stream(fk.split("、")))
			.filter(StringUtils::isNotBlank)
			.collect(Collectors.toSet());
		schemaDTO.setForeignKeys(List.of(new ArrayList<>(foreignKeys)));
	}

	/**
	 * Get all table documents by keywords for specified agent
	 */
	@Override
	public List<Document> getTableDocumentsForAgent(String agentId, String query) {
		Assert.notNull(agentId, "agentId cannot be null");
		return vectorStoreService.getDocumentsForAgent(agentId, query, Constant.TABLE);
	}

	/**
	 * Get all column documents by keywords for specified agent
	 */
	@Override
	public List<List<Document>> getColumnDocumentsByKeywordsForAgent(String agentId, List<String> keywords) {

		Assert.notNull(agentId, "agentId cannot be null");

		return keywords.stream()
			.map(kw -> vectorStoreService.getDocumentsForAgent(agentId, kw, Constant.COLUMN))
			.collect(Collectors.toList());
	}

	/**
	 * Expand column documents (supplement missing columns through foreign keys)
	 */
	private void expandColumnDocumentsWithForeignKeys(String agentId, Map<String, Document> weightedColumns,
			Set<String> foreignKeySet) {

		Set<String> existingColumnNames = weightedColumns.keySet();
		Set<String> missingColumns = new HashSet<>();
		for (String key : foreignKeySet) {
			if (!existingColumnNames.contains(key)) {
				missingColumns.add(key);
			}
		}

		for (String columnName : missingColumns) {
			addColumnsDocument(agentId, weightedColumns, columnName);
		}

	}

	/**
	 * Expand table documents (supplement missing tables through foreign keys)
	 */
	private void expandTableDocumentsWithForeignKeys(String agentId, List<Document> tableDocuments,
			Set<String> foreignKeySet) {
		Set<String> uniqueTableNames = tableDocuments.stream()
			.map(doc -> (String) doc.getMetadata().get("name"))
			.collect(Collectors.toSet());

		Set<String> missingTables = new HashSet<>();
		for (String key : foreignKeySet) {
			String[] parts = key.split("\\.");
			if (parts.length == 2) {
				String tableName = parts[0];
				if (!uniqueTableNames.contains(tableName)) {
					missingTables.add(tableName);
				}
			}
		}

		for (String tableName : missingTables) {
			addTableDocument(agentId, tableDocuments, tableName);
		}
	}

	protected void addTableDocument(String agentId, List<Document> tableDocuments, String tableName) {
		List<Document> documentsForAgent = vectorStoreService.getDocumentsForAgent(agentId, tableName, Constant.TABLE);
		if (documentsForAgent != null && !documentsForAgent.isEmpty())
			tableDocuments.addAll(documentsForAgent);
	}

	protected void addColumnsDocument(String agentId, Map<String, Document> weightedColumns, String columnName) {
		List<Document> documentsForAgent = vectorStoreService.getDocumentsForAgent(agentId, columnName,
				Constant.COLUMN);
		if (documentsForAgent != null && !documentsForAgent.isEmpty()) {
			for (Document document : documentsForAgent)
				weightedColumns.putIfAbsent(document.getId(), document);
		}
	}

	/**
	 * Select up to maxCount columns by weight using a round-robin approach to ensure
	 * balanced selection across different tables
	 */
	protected Map<String, Document> selectColumnsByRoundRobin(List<List<Document>> columnDocumentList, int maxCount) {
		Map<String, Document> selectedColumns = new HashMap<>();
		int currentRound = 0;

		// Continue selecting columns until we reach maxCount or exhaust all columns
		while (selectedColumns.size() < maxCount) {
			boolean hasMoreColumnsInAnyList = false;

			// Process each table's column list in the current round
			for (List<Document> tableColumns : columnDocumentList) {
				if (currentRound < tableColumns.size()) {
					// Get the column at current position (already sorted by weight)
					Document column = tableColumns.get(currentRound);
					String columnId = column.getId();

					// Add to selection if not already selected
					if (!selectedColumns.containsKey(columnId)) {
						selectedColumns.put(columnId, column);

						// Stop if we've reached the maximum count
						if (selectedColumns.size() >= maxCount) {
							break;
						}
					}

					hasMoreColumnsInAnyList = true;
				}
			}

			// If no more columns in any list, exit the loop
			if (!hasMoreColumnsInAnyList) {
				break;
			}

			currentRound++;
		}

		return selectedColumns;
	}

	/**
	 * Build table list from documents
	 * @param documents document list
	 * @return table list
	 */
	protected List<TableDTO> buildTableListFromDocuments(List<Document> documents) {
		List<TableDTO> tableList = new ArrayList<>();
		for (Document doc : documents) {
			Map<String, Object> meta = doc.getMetadata();
			TableDTO dto = new TableDTO();
			dto.setName((String) meta.get("name"));
			dto.setDescription((String) meta.get("description"));
			if (meta.containsKey("primaryKey")) {
				Object primaryKeyObj = meta.get("primaryKey");
				if (primaryKeyObj instanceof List) {
					@SuppressWarnings("unchecked")
					List<String> primaryKeys = (List<String>) primaryKeyObj;
					dto.setPrimaryKeys(primaryKeys);
				}
				else if (primaryKeyObj instanceof String) {
					String primaryKey = (String) primaryKeyObj;
					if (StringUtils.isNotBlank(primaryKey)) {
						dto.setPrimaryKeys(List.of(primaryKey));
					}
				}
			}
			tableList.add(dto);
		}
		return tableList;
	}

	/**
	 * Score each column (combining with its table's score)
	 */
	public void updateAndSortColumnScoresByTableWeights(List<List<Document>> columnDocuments,
			List<Document> tableDocuments) {
		for (int i = 0; i < columnDocuments.size(); i++) {
			List<Document> processedColumns = processSingleTableColumns(columnDocuments.get(i), tableDocuments);
			columnDocuments.set(i, processedColumns);
		}
	}

	/**
	 * Process columns for a single table, filtering and updating scores
	 */
	private List<Document> processSingleTableColumns(List<Document> columns, List<Document> tableDocuments) {
		// Step 1: Filter columns to only include those that have a matching table
		List<Document> filteredColumns = filterColumnsWithMatchingTables(columns, tableDocuments);

		// Step 2: Update column scores by multiplying with their table scores
		updateColumnScoresWithTableScores(filteredColumns, tableDocuments);

		// Step 3: Sort columns by their new scores in descending order
		return sortColumnsByScoreDescending(filteredColumns);
	}

	/**
	 * Filter columns to only include those that have a matching table
	 */
	private List<Document> filterColumnsWithMatchingTables(List<Document> columns, List<Document> tableDocuments) {
		List<Document> result = new ArrayList<>();

		for (Document column : columns) {
			String columnTableName = (String) column.getMetadata().get("tableName");
			if (hasMatchingTable(tableDocuments, columnTableName)) {
				result.add(column);
			}
		}

		return result;
	}

	/**
	 * Check if there's a table with the given name in the table documents
	 */
	private boolean hasMatchingTable(List<Document> tableDocuments, String tableName) {
		if (StringUtils.isBlank(tableName)) {
			return false;
		}

		for (Document table : tableDocuments) {
			String table_name = (String) table.getMetadata().get("name");
			if (tableName.equals(table_name)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Update column scores by multiplying with their table scores
	 */
	private void updateColumnScoresWithTableScores(List<Document> columns, List<Document> tableDocuments) {
		for (Document column : columns) {
			String columnTableName = (String) column.getMetadata().get("tableName");
			Document matchingTable = findTableByName(tableDocuments, columnTableName);

			if (matchingTable != null) {
				Double tableScore = getTableScore(matchingTable);
				Double columnScore = getColumnScore(column);

				if (tableScore != null && columnScore != null) {
					Double newScore = columnScore * tableScore;
					column.getMetadata().put("score", newScore);
				}
			}
		}
	}

	/**
	 * Find a table document by its name
	 */
	private Document findTableByName(List<Document> tableDocuments, String tableName) {
		if (StringUtils.isBlank(tableName)) {
			return null;
		}

		for (Document table : tableDocuments) {
			String table_name = (String) table.getMetadata().get("name");
			if (tableName.equals(table_name)) {
				return table;
			}
		}

		return null;
	}

	/**
	 * Get the score from a table document
	 */
	private Double getTableScore(Document tableDoc) {
		Double scoreFromMetadata = (Double) tableDoc.getMetadata().get("score");
		return scoreFromMetadata != null ? scoreFromMetadata : tableDoc.getScore();
	}

	/**
	 * Get the score from a column document
	 */
	private Double getColumnScore(Document columnDoc) {
		Double scoreFromMetadata = (Double) columnDoc.getMetadata().get("score");
		return scoreFromMetadata != null ? scoreFromMetadata : columnDoc.getScore();
	}

	/**
	 * Sort columns by their scores in descending order
	 */
	private List<Document> sortColumnsByScoreDescending(List<Document> columns) {
		List<Document> sortedColumns = new ArrayList<>(columns);

		sortedColumns.sort((doc1, doc2) -> {
			Double score1 = (Double) doc1.getMetadata().get("score");
			Double score2 = (Double) doc2.getMetadata().get("score");

			// Handle null scores
			if (score1 == null && score2 == null)
				return 0;
			if (score1 == null)
				return 1;
			if (score2 == null)
				return -1;

			// Sort in descending order
			return score2.compareTo(score1);
		});

		return sortedColumns;
	}

	/**
	 * Extract related table and column names from foreign key relationships
	 * @param tableDocuments table document list
	 * @return set of related names in format "tableName.columnName"
	 */
	protected Set<String> extractRelatedNamesFromForeignKeys(List<Document> tableDocuments) {
		Set<String> result = new HashSet<>();

		for (Document doc : tableDocuments) {
			String foreignKeyStr = (String) doc.getMetadata().getOrDefault("foreignKey", "");
			if (StringUtils.isNotBlank(foreignKeyStr)) {
				Arrays.stream(foreignKeyStr.split("、")).forEach(pair -> {
					String[] parts = pair.split("=");
					if (parts.length == 2) {
						result.add(parts[0].trim());
						result.add(parts[1].trim());
					}
				});
			}
		}

		return result;
	}

	/**
	 * Attach column documents to corresponding tables
	 */
	protected void attachColumnsToTables(Map<String, Document> weightedColumns, List<TableDTO> tableList) {
		if (CollectionUtils.isEmpty(weightedColumns.values())) {
			return;
		}

		for (Document columnDoc : weightedColumns.values()) {
			Map<String, Object> meta = columnDoc.getMetadata();
			ColumnDTO columnDTO = new ColumnDTO();
			columnDTO.setName((String) meta.get("name"));
			columnDTO.setDescription((String) meta.get("description"));
			columnDTO.setType((String) meta.get("type"));

			String samplesStr = (String) meta.get("samples");
			if (StringUtils.isNotBlank(samplesStr)) {
				try {
					List<String> samples = objectMapper.readValue(samplesStr, new TypeReference<List<String>>() {
					});
					columnDTO.setData(samples);
				}
				catch (Exception e) {
					log.error("Failed to parse samples: {}", samplesStr, e);
				}
			}

			String tableName = (String) meta.get("tableName");
			tableList.stream()
				.filter(t -> t.getName().equals(tableName))
				.findFirst()
				.ifPresent(dto -> dto.getColumn().add(columnDTO));
		}
	}

	/**
	 * Extract database name
	 * @param schemaDTO SchemaDTO
	 * @param dbConfig Database configuration
	 */
	@Override
	public void extractDatabaseName(SchemaDTO schemaDTO, DbConfig dbConfig) {
		String pattern = ":\\d+/([^/?&]+)";
		if (BizDataSourceTypeEnum.isMysqlDialect(dbConfig.getDialectType())) {
			Pattern regex = Pattern.compile(pattern);
			Matcher matcher = regex.matcher(dbConfig.getUrl());
			if (matcher.find()) {
				schemaDTO.setName(matcher.group(1));
			}
		}
		else if (BizDataSourceTypeEnum.isPgDialect(dbConfig.getDialectType())) {
			schemaDTO.setName(dbConfig.getSchema());
		}
	}

}
