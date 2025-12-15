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

import com.alibaba.cloud.ai.dataagent.bo.schema.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.common.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.common.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.common.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.config.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.connector.config.DbConfig;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SchemaInitRequest;
import com.alibaba.cloud.ai.dataagent.dto.schema.ColumnDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.common.util.DocumentConverterUtil.convertColumnsToDocuments;
import static com.alibaba.cloud.ai.dataagent.common.util.DocumentConverterUtil.convertTablesToDocuments;

/**
 * Schema service base class, providing common method implementations
 */
@Slf4j
@Service
@AllArgsConstructor
public class SchemaServiceImpl implements SchemaService {

	private final ExecutorService dbOperationExecutor;

	private final AccessorFactory accessorFactory;

	private final TableMetadataService tableMetadataService;

	private final BatchingStrategy batchingStrategy;

	private final DynamicFilterService dynamicFilterService;

	private final DataAgentProperties dataAgentProperties;

	/**
	 * Vector storage service
	 */
	private final AgentVectorStoreService agentVectorStoreService;

	@Override
	public void buildSchemaFromDocuments(String agentId, List<Document> currentColumnDocuments,
			List<Document> tableDocuments, SchemaDTO schemaDTO) {

		// 如果外键关系是"订单表.订单ID=订单详情表.订单ID"，那么 relatedNamesFromForeignKeys
		// 将包含"订单表.订单ID"和"订单详情表.订单ID"
		Set<String> relatedNamesFromForeignKeys = extractRelatedNamesFromForeignKeys(tableDocuments);

		// 通过外键加载缺失的表和列
		List<String> missingTables = getMissingTableNamesWithForeignKeySet(tableDocuments, relatedNamesFromForeignKeys);
		if (!missingTables.isEmpty()) {
			loadMissingTableDocuments(agentId, tableDocuments, missingTables);
			loadMissingColDocForMissingTables(agentId, currentColumnDocuments, missingTables);
		}

		// Build table list
		List<TableDTO> tableList = buildTableListFromDocuments(tableDocuments);
		// Attach columns to corresponding tables
		attachColumnsToTables(currentColumnDocuments, tableList);

		// Finally assemble SchemaDTO
		schemaDTO.setTable(tableList);

		Set<String> foreignKeys = tableDocuments.stream()
			.map(doc -> (String) doc.getMetadata().getOrDefault("foreignKey", ""))
			.flatMap(fk -> Arrays.stream(fk.split("、")))
			.filter(StringUtils::isNotBlank)
			.collect(Collectors.toSet());
		schemaDTO.setForeignKeys(new ArrayList<>(foreignKeys));
	}

	@Override
	public Boolean schema(String agentId, SchemaInitRequest schemaInitRequest) throws Exception {
		log.info("Starting schema initialization for agent: {}", agentId);
		DbConfig config = schemaInitRequest.getDbConfig();
		DbQueryParameter dqp = DbQueryParameter.from(config)
			.setSchema(config.getSchema())
			.setTables(schemaInitRequest.getTables());

		try {
			// 根据当前DbConfig获取Accessor
			Accessor dbAccessor = accessorFactory.getAccessorByDbConfig(config);

			// 清理旧数据
			log.info("Clearing existing schema data for agent: {}", agentId);
			clearSchemaDataForAgent(agentId);
			log.debug("Successfully cleared existing schema data for agent: {}", agentId);

			// 处理外键
			log.debug("Fetching foreign keys for agent: {}", agentId);
			List<ForeignKeyInfoBO> foreignKeys = dbAccessor.showForeignKeys(config, dqp);
			log.info("Found {} foreign keys for agent: {}", foreignKeys.size(), agentId);

			Map<String, List<String>> foreignKeyMap = buildForeignKeyMap(foreignKeys);
			log.debug("Built foreign key map with {} entries for agent: {}", foreignKeyMap.size(), agentId);

			// 处理表和列
			log.debug("Fetching tables for agent: {}", agentId);
			List<TableInfoBO> tables = dbAccessor.fetchTables(config, dqp);
			log.info("Found {} tables for agent: {}", tables.size(), agentId);

			if (tables.size() > 5) {
				// 对于大量表，使用并行处理
				log.info("Processing {} tables in parallel mode for agent: {}", tables.size(), agentId);
				processTablesInParallel(tables, config, foreignKeyMap);
			}
			else {
				// 对于少量表，使用批量处理
				log.info("Processing {} tables in batch mode for agent: {}", tables.size(), agentId);
				tableMetadataService.batchEnrichTableMetadata(tables, config, foreignKeyMap);
			}

			log.info("Successfully processed all tables for agent: {}", agentId);

			// 转换为文档
			List<Document> columnDocs = convertColumnsToDocuments(agentId, tables);
			List<Document> tableDocs = convertTablesToDocuments(agentId, tables);

			// 存储文档
			log.info("Storing {} columns and {} tables for agent: {}", columnDocs.size(), tableDocs.size(), agentId);
			storeSchemaDocuments(agentId, columnDocs, tableDocs);
			log.info("Successfully stored all documents for agent: {}", agentId);
			return true;
		}
		catch (Exception e) {
			log.error("Failed to process schema for agent: {}", agentId, e);
			return false;
		}
	}

	/**
	 * 并行处理表元数据，提高大量表时的处理性能
	 * @param tables 表列表
	 * @param config 数据库配置
	 * @param foreignKeyMap 外键映射
	 * @throws Exception 处理失败时抛出异常
	 */
	private void processTablesInParallel(List<TableInfoBO> tables, DbConfig config,
			Map<String, List<String>> foreignKeyMap) throws Exception {

		// 根据CPU核心数确定并行度，但不超过表的数量
		int parallelism = Math.min(Runtime.getRuntime().availableProcessors() * 2, tables.size());
		int batchSize = (int) Math.ceil((double) tables.size() / parallelism);

		log.info("Processing {} tables in parallel with parallelism: {}, batch size: {}", tables.size(), parallelism,
				batchSize);
		// 将表分成多个批次
		List<List<TableInfoBO>> tableBatches = partitionList(tables, batchSize);

		// 使用CompletableFuture进行更精细的并行控制，使用专用线程池
		List<CompletableFuture<Void>> futures = tableBatches.stream().map(batch -> CompletableFuture.runAsync(() -> {
			try {
				log.debug("Processing batch of {} tables", batch.size());

				// 批量处理当前批次的表
				tableMetadataService.batchEnrichTableMetadata(batch, config, foreignKeyMap);
				log.debug("Successfully processed batch of {} tables", batch.size());
			}
			catch (Exception e) {
				log.error("Failed to process batch of tables", e);
				throw new CompletionException(e);
			}
		}, dbOperationExecutor)).toList();

		// 等待所有任务完成，并处理异常
		try {
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
			log.info("All parallel batches completed successfully");
		}
		catch (CompletionException e) {
			log.error("Parallel processing failed", e);
			throw new Exception(e.getCause());
		}
	}

	/**
	 * 将列表分成指定大小的子列表
	 * @param list 原始列表
	 * @param batchSize 批次大小
	 * @param <T> 列表元素类型
	 * @return 分批后的列表
	 */
	private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
		List<List<T>> partitions = new ArrayList<>();
		for (int i = 0; i < list.size(); i += batchSize) {
			partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
		}
		return partitions;
	}

	protected void storeSchemaDocuments(String agentId, List<Document> columns, List<Document> tables) {
		// 串行去批写入，并行流的时候有API限速了
		List<List<Document>> columnBatches = batchingStrategy.batch(columns);
		for (List<Document> batch : columnBatches) {
			agentVectorStoreService.addDocuments(agentId, batch);
		}
		List<List<Document>> tableBatches = batchingStrategy.batch(tables);
		for (List<Document> batch : tableBatches) {
			agentVectorStoreService.addDocuments(agentId, batch);
		}

	}

	protected Map<String, List<String>> buildForeignKeyMap(List<ForeignKeyInfoBO> foreignKeys) {
		Map<String, List<String>> map = new HashMap<>();
		for (ForeignKeyInfoBO fk : foreignKeys) {
			String key = fk.getTable() + "." + fk.getColumn() + "=" + fk.getReferencedTable() + "."
					+ fk.getReferencedColumn();

			map.computeIfAbsent(fk.getTable(), k -> new ArrayList<>()).add(key);
			map.computeIfAbsent(fk.getReferencedTable(), k -> new ArrayList<>()).add(key);
		}
		return map;
	}

	protected void clearSchemaDataForAgent(String agentId) throws Exception {
		agentVectorStoreService.deleteDocumentsByVectorType(agentId, DocumentMetadataConstant.COLUMN);
		agentVectorStoreService.deleteDocumentsByVectorType(agentId, DocumentMetadataConstant.TABLE);
	}

	/**
	 * Get all table documents by keywords for specified agent
	 */
	@Override
	public List<Document> getTableDocumentsForAgent(String agentId, String query) {
		Assert.notNull(agentId, "agentId cannot be null");
		return agentVectorStoreService.getDocumentsForAgent(agentId, query, DocumentMetadataConstant.TABLE);
	}

	private List<String> getMissingTableNamesWithForeignKeySet(List<Document> tableDocuments,
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
		return new ArrayList<>(missingTables);
	}

	private void loadMissingTableDocuments(String agentId, List<Document> tableDocuments,
			List<String> missingTableNames) {
		// 加载缺失的表文档
		List<Document> foundTableDocs = this.getTableDocuments(agentId, missingTableNames);
		if (foundTableDocs.size() > missingTableNames.size())
			log.error("When we search missing tables:{},  more than expected tables for agent: {}", missingTableNames,
					agentId);

		if (!foundTableDocs.isEmpty()) {
			// 使用公共方法添加去重后的文档
			addUniqueDocuments(tableDocuments, foundTableDocs, DocumentMetadataConstant.TABLE, missingTableNames);
		}
	}

	private void loadMissingColDocForMissingTables(String agentId, List<Document> curColDocs,
			List<String> missingTableNames) {
		// 加载缺失的列文档
		List<Document> foundColumnDocs = this.getColumnDocumentsByTableName(agentId, missingTableNames);
		if (!foundColumnDocs.isEmpty()) {
			// 使用公共方法添加去重后的文档
			addUniqueDocuments(curColDocs, foundColumnDocs, DocumentMetadataConstant.COLUMN, missingTableNames);
		}
	}

	/**
	 * 添加去重后的文档到现有文档列表中
	 * @param existingDocs 现有文档列表
	 * @param newDocs 待添加的新文档列表
	 * @param docType 文档类型（用于日志输出）
	 * @param context 上下文信息（用于日志输出）
	 */
	private void addUniqueDocuments(List<Document> existingDocs, List<Document> newDocs, String docType,
			Object context) {
		// 通过Document的id来去重
		Set<String> existingDocIds = existingDocs.stream().map(Document::getId).collect(Collectors.toSet());

		// 只添加ID不存在的文档
		List<Document> uniqueNewDocs = newDocs.stream().filter(doc -> !existingDocIds.contains(doc.getId())).toList();

		if (!uniqueNewDocs.isEmpty()) {
			existingDocs.addAll(uniqueNewDocs);
			log.debug("Added {} {} documents for context: {}", uniqueNewDocs.size(), docType, context);
		}
		else {
			log.debug("No new {} documents to add for context: {}", docType, context);
		}
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
					List<String> samples = JsonUtil.getObjectMapper()
						.readValue(samplesStr, new TypeReference<List<String>>() {
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

	@Override
	public List<Document> getTableDocuments(String agentId, List<String> tableNames) {
		Assert.hasText(agentId, "AgentId cannot be empty.");
		if (tableNames.isEmpty())
			return Collections.emptyList();
		// 通过元数据过滤查找目标表
		Filter.Expression filterExpression = DynamicFilterService.buildFilterExpressionForSearchTables(agentId,
				tableNames);
		if (filterExpression == null) {
			log.error("FilterExpression is null.This should not happen when tableNames is not Empty, ");
			return Collections.emptyList();
		}
		return agentVectorStoreService.getDocumentsOnlyByFilter(filterExpression, tableNames.size() + 5);
	}

	@Override
	public List<Document> getColumnDocumentsByTableName(String agentId, List<String> tableNames) {
		Assert.hasText(agentId, "AgentId cannot be empty.");
		if (tableNames.isEmpty()) {
			log.warn("TableNames is empty.We need talbeNames to search their columns");
			return Collections.emptyList();
		}
		Filter.Expression filterExpression = dynamicFilterService.buildFilterExpressionForSearchColumns(agentId,
				tableNames);
		if (filterExpression == null) {
			log.error("FilterExpression is null.This should not happen when tableNames is not Empty, ");
			return Collections.emptyList();
		}
		// 通过元数据过滤查找目标表下的所有列
		// TopK=表数量×最大预估列数
		return agentVectorStoreService.getDocumentsOnlyByFilter(filterExpression,
				tableNames.size() * dataAgentProperties.getMaxColumnsPerTable());
	}

}
