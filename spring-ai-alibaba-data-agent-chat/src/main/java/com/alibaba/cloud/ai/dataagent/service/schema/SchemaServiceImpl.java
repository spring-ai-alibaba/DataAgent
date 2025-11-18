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
package com.alibaba.cloud.ai.dataagent.service.schema;

import com.alibaba.cloud.ai.dataagent.common.connector.config.DbConfig;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.dto.schema.ColumnDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.common.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Schema service base class, providing common method implementations
 */
@Slf4j
@Service
@AllArgsConstructor
public class SchemaServiceImpl implements SchemaService {

	private final ObjectMapper objectMapper;

	/**
	 * Vector storage service
	 */
	private final AgentVectorStoreService vectorStoreService;

	@Override
	public void buildSchemaFromDocuments(String agentId, List<Document> currentColumnDocuments,
			List<Document> tableDocuments, SchemaDTO schemaDTO) {

		// 如果外键关系是"订单表.订单ID=订单详情表.订单ID"，那么 relatedNamesFromForeignKeys
		// 将包含"订单表.订单ID"和"订单详情表.订单ID"
		Set<String> relatedNamesFromForeignKeys = extractRelatedNamesFromForeignKeys(tableDocuments);

		// Build table list
		List<TableDTO> tableList = buildTableListFromDocuments(tableDocuments);

		// Supplement missing foreign key corresponding tables
		expandTableDocumentsWithForeignKeys(agentId, tableDocuments, relatedNamesFromForeignKeys);
		expandColumnDocumentsWithForeignKeys(agentId, currentColumnDocuments, relatedNamesFromForeignKeys);

		// Attach columns to corresponding tables
		attachColumnsToTables(currentColumnDocuments, tableList);

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
		return vectorStoreService.getDocumentsForAgent(agentId, query, DocumentMetadataConstant.TABLE);
	}

	/**
	 * Get all column documents by keywords for specified agent
	 */
	@Override
	public List<Document> getColumnDocumentsByKeywordsForAgent(String agentId, List<String> keywords,
			List<Document> tableDocuments) {

		Assert.hasText(agentId, "agentId cannot be empty");

		List<Document> allResults = new ArrayList<>();
		Set<String> seenDocumentIds = new HashSet<>();
		for (String kw : keywords) {
			List<Document> docs = vectorStoreService.getDocumentsForAgent(agentId, kw, DocumentMetadataConstant.COLUMN);
			if (CollectionUtils.isEmpty(docs)) {
				continue;
			}

			List<Document> filterDocs = filterColumnsWithMatchingTables(docs, tableDocuments);
			if (CollectionUtils.isEmpty(filterDocs))
				continue;

			for (Document doc : filterDocs) {
				String docId = doc.getId();
				if (seenDocumentIds.add(docId)) {
					allResults.add(doc);
				}
			}

		}

		return allResults;
	}

	/**
	 * Expand column documents (supplement missing columns through foreign keys)
	 */
	private void expandColumnDocumentsWithForeignKeys(String agentId, List<Document> currentColumns,
			Set<String> foreignKeySet) {
		// 提取已存在的列名，格式为 tableName.columnName
		Set<String> existingColumnNames = currentColumns.stream()
			.map(doc -> (String) doc.getMetadata().get("tableName") + "." + (String) doc.getMetadata().get("name"))
			.collect(Collectors.toSet());

		// 找出缺少的列
		Set<String> missingColumnNames = new HashSet<>();
		for (String key : foreignKeySet) {
			if (!existingColumnNames.contains(key)) {
				missingColumnNames.add(key);
			}
		}

		if (missingColumnNames.isEmpty()) {
			return;
		}

		// 按表名分组缺少的列
		Map<String, List<String>> missingColumnsByTable = new HashMap<>();
		for (String columnFullName : missingColumnNames) {
			String[] parts = columnFullName.split("\\.");
			if (parts.length == 2) {
				String tableName = parts[0];
				String columnName = parts[1];
				missingColumnsByTable.computeIfAbsent(tableName, k -> new ArrayList<>()).add(columnName);
			}
		}

		// 为每个表批量获取缺少的列文档
		for (Map.Entry<String, List<String>> entry : missingColumnsByTable.entrySet()) {
			String tableName = entry.getKey();
			List<String> columnNames = entry.getValue();

			List<Document> foundColumnDocs = vectorStoreService.getColumnDocuments(agentId, tableName, columnNames);

			// 添加到当前列列表中
			if (!foundColumnDocs.isEmpty())
				currentColumns.addAll(foundColumnDocs);

			// 记录日志
			if (foundColumnDocs.size() < columnNames.size()) {
				Set<String> foundColumnNames = foundColumnDocs.stream()
					.map(doc -> (String) doc.getMetadata().get("name"))
					.collect(Collectors.toSet());

				Set<String> notFoundColumns = new HashSet<>(columnNames);
				notFoundColumns.removeAll(foundColumnNames);

				log.warn("When we search from vector store,agentId: {} - columns not found in table {}: {}", agentId,
						tableName, notFoundColumns);
			}
		}

		log.debug("Finish expanding column documents with foreign keys: {}", missingColumnNames);
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

		if (!CollectionUtils.isEmpty(missingTables)) {
			log.debug("Expand table documents with foreign keys: {}", missingTables);
			loadMissingTableDocument(agentId, tableDocuments, new ArrayList<>(missingTables));
		}

	}

	private void loadMissingTableDocument(String agentId, List<Document> tableDocuments,
			List<String> missingtableNames) {
		List<Document> foundTableDocs = vectorStoreService.getTableDocuments(agentId, missingtableNames);
		if (foundTableDocs.size() > missingtableNames.size())
			log.error("When we search missing tables:{},  more than expected tables for agent: {}", missingtableNames,
					agentId);
		if (!foundTableDocs.isEmpty())
			tableDocuments.addAll(foundTableDocs);
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
	private void attachColumnsToTables(List<Document> columns, List<TableDTO> tableList) {
		if (CollectionUtils.isEmpty(columns)) {
			return;
		}

		for (Document columnDoc : columns) {
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
