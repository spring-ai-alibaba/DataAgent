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
package com.alibaba.cloud.ai.service.nl2sql.impls;

import com.alibaba.cloud.ai.connector.accessor.Accessor;
import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.connector.bo.DbQueryParameter;
import com.alibaba.cloud.ai.connector.bo.ResultSetBO;
import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.dto.schema.ColumnDTO;
import com.alibaba.cloud.ai.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dto.schema.TableDTO;
import com.alibaba.cloud.ai.prompt.PromptHelper;
import com.alibaba.cloud.ai.service.llm.LlmService;

import com.alibaba.cloud.ai.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.util.MarkdownParserUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.alibaba.cloud.ai.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.prompt.PromptHelper.buildMixMacSqlDbPrompt;
import static com.alibaba.cloud.ai.prompt.PromptHelper.buildMixSelectorPrompt;

@Service
public class Nl2SqlServiceImpl implements Nl2SqlService {

	private static final Logger logger = LoggerFactory.getLogger(Nl2SqlServiceImpl.class);

	public final LlmService aiService;

	// todo: Accessor应根据数据源动态获取
	protected final Accessor dbAccessor;

	protected final DbConfig dbConfig;

	public Nl2SqlServiceImpl(LlmService aiService, AccessorFactory accessorFactory, DbConfig dbConfig) {
		this.aiService = aiService;
		this.dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		this.dbConfig = dbConfig;
	}

	@Override
	public Flux<ChatResponse> semanticConsistencyStream(String sql, String queryPrompt) {
		String semanticConsistencyPrompt = PromptHelper.buildSemanticConsistenPrompt(queryPrompt, sql);
		logger.info("semanticConsistencyPrompt = {}", semanticConsistencyPrompt);
		return aiService.streamCall(semanticConsistencyPrompt);
	}

	@Override
	public String generateSql(List<String> evidenceList, String query, SchemaDTO schemaDTO, String sql,
			String exceptionMessage) {
		logger.info("Generating SQL for query: {}, hasExistingSql: {}", query, sql != null && !sql.isEmpty());

		// 时间处理已经在查询重写阶段完成，这里不再需要处理
		logger.debug("Time expressions already processed in rewrite phase");

		String newSql = "";
		if (sql != null && !sql.isEmpty()) {
			// Use professional SQL error repair prompt
			logger.debug("Using SQL error fixer for existing SQL: {}", sql);
			String errorFixerPrompt = PromptHelper.buildSqlErrorFixerPrompt(query, dbConfig, schemaDTO, evidenceList,
					sql, exceptionMessage);
			newSql = aiService.call(errorFixerPrompt);
			logger.info("SQL error fixing completed");
		}
		else {
			// Normal SQL generation process
			logger.debug("Generating new SQL from scratch");
			List<String> prompts = PromptHelper.buildMixSqlGeneratorPrompt(query, dbConfig, schemaDTO, evidenceList);
			newSql = aiService.callWithSystemPrompt(prompts.get(0), prompts.get(1));
			logger.info("New SQL generation completed");
		}

		String result = MarkdownParserUtil.extractRawText(newSql).trim();
		logger.info("Final generated SQL: {}", result);
		return result;
	}

	private Set<String> fineSelect(SchemaDTO schemaDTO, String sqlGenerateSchemaMissingAdvice) {
		logger.debug("Fine selecting tables based on advice: {}", sqlGenerateSchemaMissingAdvice);
		String schemaInfo = buildMixMacSqlDbPrompt(schemaDTO, true);
		String prompt = " 建议：" + sqlGenerateSchemaMissingAdvice
				+ " \n 请按照建议进行返回相关表的名称，只返回建议中提到的表名，返回格式为：[\"a\",\"b\",\"c\"] \n " + schemaInfo;
		logger.debug("Calling LLM for table selection with advice");
		String content = aiService.call(prompt);
		if (content != null && !content.trim().isEmpty()) {
			String jsonContent = MarkdownParserUtil.extractText(content);
			List<String> tableList;
			try {
				tableList = JsonUtil.getObjectMapper().readValue(jsonContent, new TypeReference<List<String>>() {
				});
			}
			catch (Exception e) {
				logger.error("Failed to parse table selection response: {}", jsonContent, e);
				throw new IllegalStateException(jsonContent);
			}
			if (tableList != null && !tableList.isEmpty()) {
				Set<String> selectedTables = tableList.stream().map(String::toLowerCase).collect(Collectors.toSet());
				logger.debug("Selected {} tables based on advice: {}", selectedTables.size(), selectedTables);
				return selectedTables;
			}
		}
		logger.debug("No tables selected based on advice");
		return new HashSet<>();
	}

	@Override
	public SchemaDTO fineSelect(SchemaDTO schemaDTO, String query, List<String> evidenceList,
			String sqlGenerateSchemaMissingAdvice, DbConfig specificDbConfig) {
		logger.debug("Fine selecting schema for query: {} with {} evidences and specificDbConfig: {}", query,
				evidenceList.size(), specificDbConfig != null ? specificDbConfig.getUrl() : "default");

		// 增加具体的样例数据，让模型根据样例数据进行选择
		SchemaDTO enrichedSchema = enrichSchemaWithSampleData(schemaDTO, specificDbConfig);
		logger.debug("Schema enriched with sample data for {} tables",
				enrichedSchema.getTable() != null ? enrichedSchema.getTable().size() : 0);

		String prompt = buildMixSelectorPrompt(evidenceList, query, enrichedSchema);
		logger.debug("Calling LLM for schema fine selection");
		String content = aiService.call(prompt);
		Set<String> selectedTables = new HashSet<>();

		if (sqlGenerateSchemaMissingAdvice != null) {
			logger.debug("Adding tables from schema missing advice");
			selectedTables.addAll(this.fineSelect(schemaDTO, sqlGenerateSchemaMissingAdvice));
		}

		if (content != null && !content.trim().isEmpty()) {
			String jsonContent = MarkdownParserUtil.extractText(content);
			List<String> tableList;
			try {
				tableList = JsonUtil.getObjectMapper().readValue(jsonContent, new TypeReference<List<String>>() {
				});
			}
			catch (Exception e) {
				// Some scenarios may prompt exceptions, such as:
				// java.lang.IllegalStateException:
				// Please provide database schema information so I can filter relevant
				// tables based on your question.
				// TODO 目前异常接口直接返回500，未返回异常信息，后续优化将异常返回给用户
				logger.error("Failed to parse fine selection response: {}", jsonContent, e);
				throw new IllegalStateException(jsonContent);
			}
			if (tableList != null && !tableList.isEmpty()) {
				selectedTables.addAll(tableList.stream().map(String::toLowerCase).collect(Collectors.toSet()));
				int originalTableCount = schemaDTO.getTable() != null ? schemaDTO.getTable().size() : 0;
				schemaDTO.getTable().removeIf(table -> !selectedTables.contains(table.getName().toLowerCase()));
				int finalTableCount = schemaDTO.getTable() != null ? schemaDTO.getTable().size() : 0;
				logger.debug("Fine selection completed: {} -> {} tables, selected tables: {}", originalTableCount,
						finalTableCount, selectedTables);
			}
		}
		return schemaDTO;
	}

	/**
	 * 为Schema中的表和列添加样例数据，以帮助模型更好地理解数据内容和结构
	 * @param schemaDTO 原始的数据库模式信息
	 * @param specificDbConfig 特定的数据库配置，如果为null则使用默认配置
	 * @return 包含样例数据的Schema副本
	 */
	private SchemaDTO enrichSchemaWithSampleData(SchemaDTO schemaDTO, DbConfig specificDbConfig) {
		if (schemaDTO == null || schemaDTO.getTable() == null || schemaDTO.getTable().isEmpty()) {
			logger.debug("Schema is null or empty, skipping sample data enrichment");
			return schemaDTO;
		}

		// 使用传入的特定数据库配置，如果为null则使用默认配置
		DbConfig targetDbConfig = specificDbConfig != null ? specificDbConfig : dbConfig;
		logger.debug("Using database config: {}", targetDbConfig != null ? targetDbConfig.getUrl() : "null");

		// 检查数据库配置是否有效
		if (!isDatabaseConfigValid(targetDbConfig)) {
			logger.info("Database configuration is invalid, skipping sample data enrichment for all tables");
			return schemaDTO;
		}

		try {
			// 创建SchemaDTO的深拷贝以避免修改原始对象
			SchemaDTO enrichedSchema = copySchemaDTO(schemaDTO);

			// 为每个表获取样例数据
			for (TableDTO tableDTO : enrichedSchema.getTable()) {
				enrichTableWithSampleData(tableDTO, targetDbConfig);
			}

			logger.info("Successfully enriched schema with sample data for {} tables",
					enrichedSchema.getTable().size());
			return enrichedSchema;

		}
		catch (Exception e) {
			logger.warn("Failed to enrich schema with sample data, using original schema: {}", e.getMessage());
			return schemaDTO;
		}
	}

	/**
	 * 为单个表添加样例数据
	 * @param tableDTO 表信息对象
	 * @param dbConfig 数据库配置
	 */
	private void enrichTableWithSampleData(TableDTO tableDTO, DbConfig dbConfig) {
		if (tableDTO == null || tableDTO.getColumn() == null || tableDTO.getColumn().isEmpty()) {
			return;
		}

		logger.debug("Enriching table '{}' with sample table data for {} columns", tableDTO.getName(),
				tableDTO.getColumn().size());

		try {
			// 获取表的样例数据
			ResultSetBO tableData = getSampleDataForTable(tableDTO.getName(), dbConfig);
			if (tableData != null && tableData.getData() != null && !tableData.getData().isEmpty()) {
				// 将整行数据分配给对应的列
				distributeTableDataToColumns(tableDTO, tableData);
				logger.info("Successfully enriched table '{}' with {} sample rows", tableDTO.getName(),
						tableData.getData().size());
			}
			else {
				logger.debug("No sample data found for table '{}'", tableDTO.getName());
			}

		}
		catch (Exception e) {
			logger.warn("Failed to get sample data for table '{}': {}", tableDTO.getName(), e.getMessage());
		}
	}

	/**
	 * 获取表的样例数据
	 * @param tableName 表名
	 * @param dbConfig 数据库配置
	 * @return 表样例数据
	 */
	private ResultSetBO getSampleDataForTable(String tableName, DbConfig dbConfig) throws Exception {
		DbQueryParameter param = DbQueryParameter.from(dbConfig).setTable(tableName);
		return dbAccessor.scanTable(dbConfig, param);
	}

	/**
	 * 将表数据分配给对应的列
	 * @param tableDTO 表信息
	 * @param tableData 表样例数据
	 */
	private void distributeTableDataToColumns(TableDTO tableDTO, ResultSetBO tableData) {
		List<String> columnHeaders = tableData.getColumn();
		List<Map<String, String>> rows = tableData.getData();

		// 为每个列创建样例数据映射
		Map<String, List<String>> columnSamples = new HashMap<>();

		// 遍历每一行数据
		for (Map<String, String> row : rows) {
			for (String columnName : columnHeaders) {
				String value = row.get(columnName);

				if (value != null && !value.trim().isEmpty()) {
					columnSamples.computeIfAbsent(columnName, k -> new ArrayList<>()).add(value);
				}
			}
		}

		// 将样例数据分配给对应的列
		for (ColumnDTO columnDTO : tableDTO.getColumn()) {
			String columnName = columnDTO.getName();
			List<String> samples = columnSamples.get(columnName);

			if (samples != null && !samples.isEmpty()) {
				// 去重并限制样例数量
				List<String> filteredSamples = samples.stream()
					.filter(sample -> sample != null && !sample.trim().isEmpty())
					.distinct()
					.limit(5) // 最多保留5个样例值
					.collect(Collectors.toList());

				if (!filteredSamples.isEmpty()) {
					columnDTO.setSamples(filteredSamples);
					logger.debug("Added {} sample values for column '{}.{}': {}", filteredSamples.size(),
							tableDTO.getName(), columnName, filteredSamples);
				}
			}
		}
	}

	/**
	 * 检查数据库配置是否有效
	 * @param dbConfig 数据库配置
	 * @return true如果配置有效，false otherwise
	 */
	private boolean isDatabaseConfigValid(DbConfig dbConfig) {
		if (dbConfig == null) {
			logger.debug("dbConfig is null");
			return false;
		}

		if (dbAccessor == null) {
			logger.debug("dbAccessor is null");
			return false;
		}

		// 检查基本的连接信息
		boolean hasBasicInfo = dbConfig.getUrl() != null && !dbConfig.getUrl().trim().isEmpty()
				&& dbConfig.getUsername() != null && !dbConfig.getUsername().trim().isEmpty();

		if (!hasBasicInfo) {
			logger.debug("dbConfig missing basic connection info - url: {}, username: {}",
					dbConfig.getUrl() != null ? "present" : "null",
					dbConfig.getUsername() != null ? "present" : "null");
			return false;
		}

		return true;
	}

	/**
	 * 创建SchemaDTO的深拷贝
	 * @param originalSchema 原始Schema
	 * @return Schema的深拷贝
	 */
	private SchemaDTO copySchemaDTO(SchemaDTO originalSchema) {
		SchemaDTO copy = new SchemaDTO();
		copy.setName(originalSchema.getName());
		copy.setDescription(originalSchema.getDescription());
		copy.setTableCount(originalSchema.getTableCount());
		copy.setForeignKeys(originalSchema.getForeignKeys());

		if (originalSchema.getTable() != null) {
			List<TableDTO> copiedTables = originalSchema.getTable()
				.stream()
				.map(this::copyTableDTO)
				.collect(Collectors.toList());
			copy.setTable(copiedTables);
		}

		return copy;
	}

	/**
	 * 创建TableDTO的深拷贝
	 * @param originalTable 原始表
	 * @return 表的深拷贝
	 */
	private TableDTO copyTableDTO(TableDTO originalTable) {
		TableDTO copy = new TableDTO();
		copy.setName(originalTable.getName());
		copy.setDescription(originalTable.getDescription());
		copy.setPrimaryKeys(originalTable.getPrimaryKeys());

		if (originalTable.getColumn() != null) {
			List<ColumnDTO> copiedColumns = originalTable.getColumn()
				.stream()
				.map(this::copyColumnDTO)
				.collect(Collectors.toList());
			copy.setColumn(copiedColumns);
		}

		return copy;
	}

	/**
	 * 创建ColumnDTO的深拷贝
	 * @param originalColumn 原始列
	 * @return 列的深拷贝
	 */
	private ColumnDTO copyColumnDTO(ColumnDTO originalColumn) {
		ColumnDTO copy = new ColumnDTO();
		copy.setName(originalColumn.getName());
		copy.setDescription(originalColumn.getDescription());
		copy.setEnumeration(originalColumn.getEnumeration());
		copy.setRange(originalColumn.getRange());
		copy.setType(originalColumn.getType());
		copy.setMapping(originalColumn.getMapping());

		// 复制现有的样例数据
		if (originalColumn.getSamples() != null) {
			copy.setSamples(new ArrayList<>(originalColumn.getSamples()));
		}
		if (originalColumn.getData() != null) {
			copy.setData(new ArrayList<>(originalColumn.getData()));
		}

		return copy;
	}

}
