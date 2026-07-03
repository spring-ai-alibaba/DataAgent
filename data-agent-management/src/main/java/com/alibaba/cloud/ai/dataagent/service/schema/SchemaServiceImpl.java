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
package com.alibaba.cloud.ai.dataagent.service.schema;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
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
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.util.DocumentConverterUtil.convertColumnsToDocuments;
import static com.alibaba.cloud.ai.dataagent.util.DocumentConverterUtil.convertTablesToDocuments;

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
			List<Document> tableDocuments, SchemaDTO schemaDTO, List<String> extraForeignKeys) {

		// 创建可变列表副本，避免不可变集合异常
		List<Document> mutableColumnDocuments = new ArrayList<>(currentColumnDocuments);
		List<Document> mutableTableDocuments = new ArrayList<>(tableDocuments);

		// 从 tableDocuments 中提取 datasourceId
		Integer datasourceId = mutableTableDocuments.stream()
			.map(doc -> doc.getMetadata().get(Constant.DATASOURCE_ID))
			.filter(Objects::nonNull)
			.map(Object::toString)
			.map(Integer::valueOf)
			.findFirst()
			.orElse(null);

		// 如果外键关系是"订单表.订单ID=订单详情表.订单ID"，那么 relatedNamesFromForeignKeys
		// 将包含"订单表.订单ID"和"订单详情表.订单ID"
		Set<String> relatedNamesFromForeignKeys = extractRelatedNamesFromForeignKeys(mutableTableDocuments);

		// 将额外的外键信息（例如逻辑虚拟外键）一并纳入提取
		if (extraForeignKeys != null) {
			for (String fk : extraForeignKeys) {
				if (StringUtils.isNotBlank(fk)) {
					Arrays.stream(fk.split("、")).forEach(pair -> {
						String[] parts = pair.split("=");
						if (parts.length == 2) {
							relatedNamesFromForeignKeys.add(parts[0].trim());
							relatedNamesFromForeignKeys.add(parts[1].trim());
						}
					});
				}
			}
		}

		// 通过外键加载缺失的表和列
		List<String> missingTables = getMissingTableNamesWithForeignKeySet(mutableTableDocuments,
				relatedNamesFromForeignKeys);
		if (!missingTables.isEmpty() && datasourceId != null) {
			loadMissingTableDocuments(datasourceId, mutableTableDocuments, missingTables);
			loadMissingColDocForMissingTables(datasourceId, mutableColumnDocuments, missingTables);
		}

		// Build table list
		List<TableDTO> tableList = buildTableListFromDocuments(mutableTableDocuments);
		// Attach columns to corresponding tables
		attachColumnsToTables(mutableColumnDocuments, tableList);

		// Finally assemble SchemaDTO
		schemaDTO.setTable(tableList);

		Set<String> foreignKeys = tableDocuments.stream()
			.map(doc -> {
				Object fk = doc.getMetadata().getOrDefault("foreignKey", "");
				return fk != null ? fk.toString() : "";
			})
			.flatMap(fk -> Arrays.stream(fk.split("、")))
			.filter(StringUtils::isNotBlank)
			.collect(Collectors.toSet());
		schemaDTO.setForeignKeys(new ArrayList<>(foreignKeys));
	}

	@Override
	public Boolean schema(Integer datasourceId, SchemaInitRequest schemaInitRequest) throws Exception {
		log.info("Starting schema initialization for datasource: {}", datasourceId);
		DbConfigBO config = schemaInitRequest.getDbConfig();
		// 将前端传入的表按 schema 归类
		Map<String, List<String>> schemaToTables = new HashMap<>();
		// 解析 "schema.table" 格式，按 schema 分组
		for (String rawTable : schemaInitRequest.getTables()) {
			String schemaName = config.getSchema();
			String tableName = rawTable;
			if (rawTable.contains(".")) {
				String[] split = rawTable.split("\\.", 2);
				schemaName = split[0];
				tableName = split[1];
			}
			schemaToTables.computeIfAbsent(schemaName, k -> new ArrayList<>()).add(tableName);
		}

		try {
			// 根据当前DbConfig获取Accessor
			Accessor dbAccessor = accessorFactory.getAccessorByDbConfig(config);
			// 清理旧数据
			log.info("Clearing existing schema data for datasource: {}", datasourceId);
			clearSchemaDataForDatasource(datasourceId);
			log.debug("Successfully cleared existing schema data for datasource: {}", datasourceId);
			List<TableInfoBO> allTables = new ArrayList<>();
			for (Map.Entry<String, List<String>> entry : schemaToTables.entrySet()) {
				String schemaName = entry.getKey();
				List<String> tableNamesForSchema = entry.getValue();

				// 复制一个带有特定 schema 的配置对象
				DbConfigBO currentSchemaConfig = new DbConfigBO();
				BeanUtils.copyProperties(config, currentSchemaConfig);
				currentSchemaConfig.setSchema(schemaName);
				DbQueryParameter dqp = DbQueryParameter.from(currentSchemaConfig)
						.setSchema(schemaName)
						.setTables(tableNamesForSchema);
				// 处理外键
				log.debug("Fetching foreign keys for datasource: {}", datasourceId);
				List<ForeignKeyInfoBO> foreignKeys = dbAccessor.showForeignKeys(currentSchemaConfig, dqp);
				log.info("Found {} foreign keys for datasource: {}", foreignKeys.size(), datasourceId);
				// 外键 map 的 key 需要使用 schema.table 格式，与后续表名前缀保持一致
				Map<String, List<String>> foreignKeyMap = buildForeignKeyMap(foreignKeys, schemaName);
				log.debug("Built foreign key map with {} entries for datasource: {}", foreignKeyMap.size(), datasourceId);

				// 处理表和列
				log.debug("Fetching tables for datasource: {}", datasourceId);
				List<TableInfoBO> tables = dbAccessor.fetchTables(currentSchemaConfig, dqp);
				log.info("Found {} tables for datasource: {}", tables.size(), datasourceId);

				if (tables.size() > 5) {
					// 对于大量表，使用并行处理
					log.info("Processing {} tables in parallel mode for datasource: {}", tables.size(), datasourceId);
					processTablesInParallel(tables, currentSchemaConfig, foreignKeyMap);
				}
				else {
					// 对于少量表，使用批量处理
					log.info("Processing {} tables in batch mode for datasource: {}", tables.size(), datasourceId);
					tableMetadataService.batchEnrichTableMetadata(tables, currentSchemaConfig, foreignKeyMap);
				}
				// 将带有 schema 的表名和相关列的归属表名更新，方便存储到向量库中
				if (StringUtils.isNotBlank(schemaName)) {
					for (TableInfoBO table : tables) {
						String fullName = schemaName + "." + table.getName();
						table.setName(fullName);
						if (table.getColumns() != null) {
							for (ColumnInfoBO column : table.getColumns()) {
								column.setTableName(fullName);
							}
						}
					}
				}
				allTables.addAll(tables);
			}

			log.info("Successfully processed all tables for datasource: {}", datasourceId);

			// 转换为文档
			List<Document> columnDocs = convertColumnsToDocuments(datasourceId,allTables);
			List<Document> tableDocs = convertTablesToDocuments(datasourceId, allTables);

			// 存储文档
			log.info("Storing  columns and {} tables for datasource: {}", columnDocs.size(), tableDocs.size(),
					datasourceId);
			storeSchemaDocuments(datasourceId, columnDocs, tableDocs);
			log.info("Successfully stored all documents for datasource: {}", datasourceId);
			return true;
		}
		catch (Exception e) {
			log.error("Failed to process schema for datasource: {}", datasourceId, e);
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
	private void processTablesInParallel(List<TableInfoBO> tables, DbConfigBO config,
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

	protected void storeSchemaDocuments(Integer datasourceId, List<Document> columns, List<Document> tables) {
		// 串行去批写入，并行流的时候有API限速了
		List<List<Document>> columnBatches = batchingStrategy.batch(columns);
		for (List<Document> batch : columnBatches) {
			agentVectorStoreService.addDocuments(datasourceId.toString(), batch);
		}
		List<List<Document>> tableBatches = batchingStrategy.batch(tables);
		for (List<Document> batch : tableBatches) {
			agentVectorStoreService.addDocuments(datasourceId.toString(), batch);
		}

	}

	protected Map<String, List<String>> buildForeignKeyMap(List<ForeignKeyInfoBO> foreignKeys) {
		return buildForeignKeyMap(foreignKeys, null);
	}

	/**
	 * 构建外键映射，key 使用 schema.table 格式（与向量库表名保持一致）
	 * @param foreignKeys 外键列表
	 * @param schemaName schema 名称（为非空时，table 名前加 schema. 前缀）
	 */
	protected Map<String, List<String>> buildForeignKeyMap(List<ForeignKeyInfoBO> foreignKeys, String schemaName) {
		Map<String, List<String>> map = new HashMap<>();
		boolean hasSchema = StringUtils.isNotBlank(schemaName);
		for (ForeignKeyInfoBO fk : foreignKeys) {
			String table = hasSchema ? schemaName + "." + fk.getTable() : fk.getTable();
			String refTable = hasSchema ? schemaName + "." + fk.getReferencedTable() : fk.getReferencedTable();
			String key = table + "." + fk.getColumn() + "=" + refTable + "." + fk.getReferencedColumn();

			map.computeIfAbsent(table, k -> new ArrayList<>()).add(key);
			map.computeIfAbsent(refTable, k -> new ArrayList<>()).add(key);
		}
		return map;
	}

	protected void clearSchemaDataForDatasource(Integer datasourceId) throws Exception {
		// 检查是否有文档需要删除
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(Constant.DATASOURCE_ID, datasourceId.toString());
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.COLUMN);

		agentVectorStoreService.deleteDocumentsByMetadata(metadata);

		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.TABLE);
		agentVectorStoreService.deleteDocumentsByMetadata(metadata);
	}

	@Override
	public List<Document> getTableDocumentsByDatasource(Integer datasourceId, String query) {
		Assert.notNull(datasourceId, "datasourceId cannot be null");
		int tableTopK = dataAgentProperties.getVectorStore().getTableTopkLimit();
		double tableThreshold = dataAgentProperties.getVectorStore().getTableSimilarityThreshold();

		// 构建过滤表达式
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		List<Filter.Expression> conditions = new ArrayList<>();

		conditions.add(b.eq(Constant.DATASOURCE_ID, datasourceId.toString()).build());
		conditions.add(b.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.TABLE).build());

		Filter.Expression filterExpression = DynamicFilterService.combineWithAnd(conditions);

		// 执行向量检索
		SearchRequest searchRequest = SearchRequest.builder()
			.query(query)
			.topK(tableTopK)
			.similarityThreshold(tableThreshold)
			.filterExpression(filterExpression)
			.build();

		return agentVectorStoreService.getDocumentsOnlyByFilter(filterExpression, tableTopK);
	}

	private List<String> getMissingTableNamesWithForeignKeySet(List<Document> tableDocuments,
			Set<String> foreignKeySet) {
		Set<String> uniqueTableNames = tableDocuments.stream()
			.map(doc -> (String) doc.getMetadata().get("name"))
			.collect(Collectors.toSet());

		Set<String> missingTables = new HashSet<>();
		for (String key : foreignKeySet) {
			// key 格式： schema.table.column 或 table.column
			// 从最后一个点拆分，取左边得到表名（可能是 schema.table 或 table）
			int lastDot = key.lastIndexOf('.');
			if (lastDot > 0) {
				String tableName = key.substring(0, lastDot);
				if (!uniqueTableNames.contains(tableName)) {
					missingTables.add(tableName);
				}
			}
		}
		return new ArrayList<>(missingTables);
	}

	private void loadMissingTableDocuments(Integer datasourceId, List<Document> tableDocuments,
			List<String> missingTableNames) {
		// 加载缺失的表文档
		List<Document> foundTableDocs = this.getTableDocuments(datasourceId, missingTableNames);
		if (foundTableDocs.size() > missingTableNames.size())
			log.error("When we search missing tables:{},  more than expected tables for datasource: {}",
					missingTableNames, datasourceId);

		if (!foundTableDocs.isEmpty()) {
			// 使用公共方法添加去重后的文档
			addUniqueDocuments(tableDocuments, foundTableDocs, DocumentMetadataConstant.TABLE, missingTableNames);
		}
	}

	private void loadMissingColDocForMissingTables(Integer datasourceId, List<Document> curColDocs,
			List<String> missingTableNames) {
		// 加载缺失的列文档
		List<Document> foundColumnDocs = this.getColumnDocumentsByTableName(datasourceId, missingTableNames);
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
	public void extractDatabaseName(SchemaDTO schemaDTO, DbConfigBO dbConfig) {
		String pattern = ":\\d+/([^/?&]+)";
		if (BizDataSourceTypeEnum.isMysqlDialect(dbConfig.getDialectType())) {
			Pattern regex = Pattern.compile(pattern);
			Matcher matcher = regex.matcher(dbConfig.getUrl());
			if (matcher.find()) {
				schemaDTO.setName(matcher.group(1));
			}
		}
		else if (BizDataSourceTypeEnum.isPgDialect(dbConfig.getDialectType())) {
			if (dbConfig.getSchemas() != null && !dbConfig.getSchemas().isEmpty()) {
				schemaDTO.setName(String.join(",", dbConfig.getSchemas()));
			}else {
				schemaDTO.setName(dbConfig.getSchema());
			}
		}
	}

	@Override
	public List<Document> getTableDocuments(Integer datasourceId, List<String> tableNames) {
		Assert.notNull(datasourceId, "DatasourceId cannot be null.");
		if (tableNames.isEmpty())
			return Collections.emptyList();
		// 通过元数据过滤查找目标表
		Filter.Expression filterExpression = DynamicFilterService.buildFilterExpressionForSearchTables(datasourceId,
				tableNames);
		if (filterExpression == null) {
			log.error("FilterExpression is null.This should not happen when tableNames is not Empty, ");
			return Collections.emptyList();
		}
		return agentVectorStoreService.getDocumentsOnlyByFilter(filterExpression, tableNames.size() + 5);
	}

	@Override
	public List<Document> getColumnDocumentsByTableName(Integer datasourceId, List<String> tableNames) {
		Assert.notNull(datasourceId, "DatasourceId cannot be null.");
		if (tableNames.isEmpty()) {
			log.warn("TableNames is empty.We need talbeNames to search their columns");
			return Collections.emptyList();
		}
		Filter.Expression filterExpression = dynamicFilterService.buildFilterExpressionForSearchColumns(datasourceId,
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
