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

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.common.util.SqlUtil;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.connector.config.DbConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for processing table metadata. This service handles fetching column details,
 * sample data, and enriching table information with metadata.
 */
@Slf4j
@Service
@AllArgsConstructor
public class TableMetadataService {

	private final AccessorFactory accessorFactory;

	private final ObjectMapper objectMapper;

	/**
	 * 批量处理多个表的元数据，提高性能
	 * @param tables 表列表
	 * @param dbConfig 数据库配置
	 * @param foreignKeyMap 外键映射
	 * @throws Exception 处理失败时抛出异常
	 */
	public void batchEnrichTableMetadata(List<TableInfoBO> tables, DbConfig dbConfig,
			Map<String, List<String>> foreignKeyMap) throws Exception {

		// 1. 批量获取所有表的列信息
		Map<String, List<ColumnInfoBO>> tableColumnsMap = fetchTableColumns(tables, dbConfig);

		// 2. 批量获取所有表的列样本数据
		Map<String, Map<String, List<String>>> allTablesSampleData = batchGetSampleDataForTables(dbConfig,
				tableColumnsMap);

		// 3. 处理每个表的元数据
		enrichTablesWithMetadata(tables, tableColumnsMap, allTablesSampleData, foreignKeyMap);
	}

	/**
	 * 批量获取所有表的列信息
	 * @param tables 表列表
	 * @param dbConfig 数据库配置
	 * @return 表名到列信息的映射
	 * @throws Exception 获取列信息失败时抛出异常
	 */
	private Map<String, List<ColumnInfoBO>> fetchTableColumns(List<TableInfoBO> tables, DbConfig dbConfig)
			throws Exception {
		Map<String, List<ColumnInfoBO>> tableColumnsMap = new HashMap<>();
		Accessor accessor = accessorFactory.getAccessorByDbConfig(dbConfig);

		for (TableInfoBO table : tables) {
			DbQueryParameter tableDqp = DbQueryParameter.from(dbConfig).setTable(table.getName());
			List<ColumnInfoBO> columnInfoBOS = accessor.showColumns(dbConfig, tableDqp);
			tableColumnsMap.put(table.getName(), columnInfoBOS);
		}

		return tableColumnsMap;
	}

	/**
	 * 为表添加元数据信息
	 * @param tables 表列表
	 * @param tableColumnsMap 表名到列信息的映射
	 * @param allTablesSampleData 表名到列样本数据的映射
	 * @param foreignKeyMap 外键映射
	 */
	private void enrichTablesWithMetadata(List<TableInfoBO> tables, Map<String, List<ColumnInfoBO>> tableColumnsMap,
			Map<String, Map<String, List<String>>> allTablesSampleData, Map<String, List<String>> foreignKeyMap) {

		for (TableInfoBO table : tables) {
			List<ColumnInfoBO> columnInfoBOS = tableColumnsMap.get(table.getName());
			Map<String, List<String>> tableSampleData = allTablesSampleData.getOrDefault(table.getName(),
					new HashMap<>());

			// 处理列信息
			processColumnInfo(columnInfoBOS, table, tableSampleData);

			// 设置表的主键信息
			setTablePrimaryKeys(table, columnInfoBOS);

			// 设置表的外键信息
			setTableForeignKeys(table, foreignKeyMap);
		}
	}

	/**
	 * 处理列信息，包括设置表名和样本数据
	 * @param columnInfoBOS 列信息列表
	 * @param table 表信息
	 * @param tableSampleData 表样本数据
	 */
	private void processColumnInfo(List<ColumnInfoBO> columnInfoBOS, TableInfoBO table,
			Map<String, List<String>> tableSampleData) {

		for (ColumnInfoBO columnInfoBO : columnInfoBOS) {
			// 设置列所属的表名
			columnInfoBO.setTableName(table.getName());

			// 获取并设置列的样本数据
			List<String> sampleColumnValue = tableSampleData.getOrDefault(columnInfoBO.getName(), new ArrayList<>());
			setColumnSamples(columnInfoBO, sampleColumnValue);
		}

		// 保存处理过的列数据到TableInfoBO，供后续使用
		table.setColumns(columnInfoBOS);
	}

	/**
	 * 设置列的样本数据
	 * @param columnInfoBO 列信息
	 * @param sampleColumnValue 样本数据列表
	 */
	private void setColumnSamples(ColumnInfoBO columnInfoBO, List<String> sampleColumnValue) {
		try {
			columnInfoBO.setSamples(objectMapper.writeValueAsString(sampleColumnValue));
		}
		catch (JsonProcessingException e) {
			log.error("Failed to convert sample data {} to JSON: {}, set default empty", sampleColumnValue,
					e.getMessage());
			columnInfoBO.setSamples("[]");
		}
	}

	/**
	 * 设置表的主键信息
	 * @param table 表信息
	 * @param columnInfoBOS 列信息列表
	 */
	private void setTablePrimaryKeys(TableInfoBO table, List<ColumnInfoBO> columnInfoBOS) {
		List<ColumnInfoBO> primaryKeyColumns = columnInfoBOS.stream().filter(ColumnInfoBO::isPrimary).toList();

		if (!primaryKeyColumns.isEmpty()) {
			List<String> columnNames = primaryKeyColumns.stream()
				.map(ColumnInfoBO::getName)
				.collect(Collectors.toList());
			table.setPrimaryKeys(columnNames);
		}
		else {
			table.setPrimaryKeys(new ArrayList<>());
		}
	}

	/**
	 * 设置表的外键信息
	 * @param table 表信息
	 * @param foreignKeyMap 外键映射
	 */
	private void setTableForeignKeys(TableInfoBO table, Map<String, List<String>> foreignKeyMap) {
		List<String> foreignKeys = foreignKeyMap.getOrDefault(table.getName(), new ArrayList<>());
		table.setForeignKey(String.join("、", foreignKeys));
	}

	/**
	 * 批量获取多个表的样本数据，减少数据库查询次数
	 * @param dbConfig 数据库配置
	 * @param tableColumnsMap 表名到列信息的映射
	 * @return 表名到列样本数据的映射
	 */
	private Map<String, Map<String, List<String>>> batchGetSampleDataForTables(DbConfig dbConfig,
			Map<String, List<ColumnInfoBO>> tableColumnsMap) {

		// 外层Map 键:表名，值:该表的列样本数据Map
		// 内层Map 键:列名，值:该列的样本数据
		// 示例数据
		// "users": {
		// "id": ["1", "2", "3"],
		// "name": ["张三", "李四", "王五"],
		// "email": ["zhang@example.com", "li@example.com", "wang@example.com"]
		// }
		// }
		Map<String, Map<String, List<String>>> result = new HashMap<>();
		Accessor accessor = accessorFactory.getAccessorByDbConfig(dbConfig);

		// 为每个表的数据列生成样本数据
		for (Map.Entry<String, List<ColumnInfoBO>> entry : tableColumnsMap.entrySet()) {
			String tableName = entry.getKey();
			List<ColumnInfoBO> columns = entry.getValue();

			if (columns.isEmpty()) {
				result.put(tableName, new HashMap<>());
				continue;
			}

			Map<String, List<String>> tableSampleData = fetchTableSampleData(dbConfig, accessor, tableName, columns);
			result.put(tableName, tableSampleData);
		}

		return result;
	}

	/**
	 * 获取单个表的样本数据
	 * @param dbConfig 数据库配置
	 * @param accessor 数据库访问器
	 * @param tableName 表名
	 * @param columns 列信息列表
	 * @return 表的样本数据映射
	 */
	private Map<String, List<String>> fetchTableSampleData(DbConfig dbConfig, Accessor accessor, String tableName,
			List<ColumnInfoBO> columns) {

		try {
			// 构建批量查询SQL，一次查询多个列的样本数据
			String columnNames = columns.stream().map(ColumnInfoBO::getName).collect(Collectors.joining(", "));
			String sql = SqlUtil.buildSelectSql(dbConfig.getDialectType(), tableName, columnNames, 5);

			DbQueryParameter batchParam = new DbQueryParameter();
			batchParam.setSchema(dbConfig.getSchema());
			batchParam.setSql(sql);

			ResultSetBO resultSet = accessor.executeSqlAndReturnObject(dbConfig, batchParam);
			log.info("Embedding for table: {}, result size: {}", tableName, resultSet.getData().size());

			return processResultSet(resultSet, columns);
		}
		catch (Exception e) {
			log.error("Failed to fetch sample data for table: {},use empty map as default value", tableName, e);
			return new HashMap<>();
		}
	}

	/**
	 * 处理查询结果集，提取并格式化样本数据
	 * @param resultSet 查询结果集
	 * @param columns 列信息列表
	 * @return 处理后的样本数据
	 */
	private Map<String, List<String>> processResultSet(ResultSetBO resultSet, List<ColumnInfoBO> columns) {
		Map<String, List<String>> tableSampleData = new HashMap<>();

		if (resultSet == null || resultSet.getData() == null) {
			return tableSampleData;
		}

		// 提取原始样本数据
		for (Map<String, String> row : resultSet.getData()) {
			extractSampleDataFromRow(row, columns, tableSampleData);
		}

		// 过滤和限制样本数据
		return filterAndLimitSampleData(tableSampleData);
	}

	/**
	 * 从单行数据中提取样本数据
	 * @param row 数据行
	 * @param columns 列信息列表
	 * @param tableSampleData 存储样本数据的映射
	 */
	private void extractSampleDataFromRow(Map<String, String> row, List<ColumnInfoBO> columns,
			Map<String, List<String>> tableSampleData) {
		for (ColumnInfoBO column : columns) {
			String columnName = column.getName();
			Object value = row.get(columnName);
			if (value != null) {
				tableSampleData.computeIfAbsent(columnName, k -> new ArrayList<>()).add(String.valueOf(value));
			}
		}
	}

	/**
	 * 过滤和限制样本数据，确保每列最多3个样本，并去重
	 * @param tableSampleData 原始样本数据
	 * @return 过滤后的样本数据
	 */
	private Map<String, List<String>> filterAndLimitSampleData(Map<String, List<String>> tableSampleData) {
		tableSampleData.replaceAll((col, samples) -> samples.stream()
			.filter(Objects::nonNull)
			.distinct()
			.limit(3)
			.filter(s -> s.length() <= 100)
			.collect(Collectors.toList()));

		return tableSampleData;
	}

}
