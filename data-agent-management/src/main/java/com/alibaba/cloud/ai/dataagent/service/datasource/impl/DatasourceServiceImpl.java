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
package com.alibaba.cloud.ai.dataagent.service.datasource.impl;

import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPool;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPoolFactory;
import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.registry.DatasourceTypeHandlerRegistry;
import com.alibaba.cloud.ai.dataagent.mapper.DatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.LogicalRelationMapper;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// todo: 检查Mapper的返回值，判断是否执行成功（或者对Mapper进行AOP）
@Slf4j
@Service
@AllArgsConstructor
public class DatasourceServiceImpl implements DatasourceService {

	private final DatasourceMapper datasourceMapper;

	private final AgentDatasourceMapper agentDatasourceMapper;

	private final LogicalRelationMapper logicalRelationMapper;

	private final DBConnectionPoolFactory poolFactory;

	private final AccessorFactory accessorFactory;

	private final DatasourceTypeHandlerRegistry datasourceTypeHandlerRegistry;

	@Override
	public List<Datasource> getAllDatasource() {
		return datasourceMapper.selectAll();
	}

	@Override
	public List<Datasource> getDatasourceByStatus(String status) {
		return datasourceMapper.selectByStatus(status);
	}

	@Override
	public List<Datasource> getDatasourceByType(String type) {
		return datasourceMapper.selectByType(type);
	}

	@Override
	public Datasource getDatasourceById(Integer id) {
		return datasourceMapper.selectById(id);
	}

	@Override
	public Datasource createDatasource(Datasource datasource) {
		// Generate connection URL
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		String connectionUrl = handler.resolveConnectionUrl(datasource);
		if (StringUtils.isNotBlank(connectionUrl)) {
			datasource.setConnectionUrl(connectionUrl);
		}

		// Set default values
		if (datasource.getStatus() == null) {
			datasource.setStatus("active");
		}
		if (datasource.getTestStatus() == null) {
			datasource.setTestStatus("unknown");
		}

		datasourceMapper.insert(datasource);
		return datasource;
	}

	@Override
	public Datasource updateDatasource(Integer id, Datasource datasource) {
		// Regenerate connection URL
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		String connectionUrl = handler.resolveConnectionUrl(datasource);
		if (StringUtils.isNotBlank(connectionUrl)) {
			datasource.setConnectionUrl(connectionUrl);
		}
		datasource.setId(id);

		datasourceMapper.updateById(datasource);
		return datasource;
	}

	@Override
	@Transactional
	public void deleteDatasource(Integer id) {
		// First, delete the associations
		agentDatasourceMapper.deleteAllByDatasourceId(id);

		// Then, delete the data source
		datasourceMapper.deleteById(id);
	}

	@Override
	public void updateTestStatus(Integer id, String testStatus) {
		datasourceMapper.updateTestStatusById(id, testStatus);
	}

	@Override
	public boolean testConnection(Integer id) {
		Datasource datasource = getDatasourceById(id);
		if (datasource == null) {
			return false;
		}
		try {
			// ping测试
			boolean connectionSuccess = realConnectionTest(datasource);
			log.info(datasource.getName() + " test connection result: " + connectionSuccess);
			// Update test status
			updateTestStatus(id, connectionSuccess ? "success" : "failed");

			return connectionSuccess;
		}
		catch (Exception e) {
			updateTestStatus(id, "failed");
			log.error("Error testing connection for datasource ID " + id + ": " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Actual connection test method
	 */
	private boolean realConnectionTest(Datasource datasource) {
		// Convert Datasource to DbConfig
		DbConfigBO config = new DbConfigBO();
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		String originalUrl = handler.resolveConnectionUrl(datasource);

		if (StringUtils.isNotBlank(originalUrl)) {
			originalUrl = handler.normalizeTestUrl(datasource, originalUrl);
		}
		config.setUrl(originalUrl);
		config.setUsername(datasource.getUsername());
		config.setPassword(datasource.getPassword());

		DBConnectionPool pool = poolFactory.getPoolByType(datasource.getType());
		if (pool == null) {
			return false;
		}

		ErrorCodeEnum result = pool.ping(config);
		return result == ErrorCodeEnum.SUCCESS;

	}

	@Override
	@Deprecated
	public List<AgentDatasource> getAgentDatasource(Integer agentId) {
		List<AgentDatasource> adentDatasources = agentDatasourceMapper.selectByAgentIdWithDatasource(agentId);

		// Manually fill in the data source information (since MyBatis Plus does not
		// directly support complex join query result mapping)
		for (AgentDatasource agentDatasource : adentDatasources) {
			if (agentDatasource.getDatasourceId() != null) {
				Datasource datasource = datasourceMapper.selectById(agentDatasource.getDatasourceId());
				agentDatasource.setDatasource(datasource);
			}
		}

		return adentDatasources;
	}

	@Override
	public List<String> getDatasourceTables(Integer datasourceId) throws Exception {
		log.info("Getting tables for datasource: {}", datasourceId);

		// Get data source information
		Datasource datasource = this.getDatasourceById(datasourceId);
		if (datasource == null) {
			throw new RuntimeException("Datasource not found with id: " + datasourceId);
		}

		// Create database configuration
		DbConfigBO dbConfig = getDbConfig(datasource);

		// Create query parameters
		DbQueryParameter queryParam = DbQueryParameter.from(dbConfig);
		queryParam.setSchema(datasource.getDatabaseName());

		// Query table list
		Accessor dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
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

	@Override
	public DbConfigBO getDbConfig(Datasource datasource) {
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		return handler.toDbConfig(datasource);
	}

	@Override
	public List<String> getTableColumns(Integer datasourceId, String tableName) throws Exception {
		log.info("Getting columns for table: {} in datasource: {}", tableName, datasourceId);

		// 获取数据源信息
		Datasource datasource = this.getDatasourceById(datasourceId);
		if (datasource == null) {
			throw new RuntimeException("Datasource not found with id: " + datasourceId);
		}

		// 创建数据库配置
		DbConfigBO dbConfig = getDbConfig(datasource);

		// 创建查询参数
		DbQueryParameter queryParam = DbQueryParameter.from(dbConfig);
		queryParam.setSchema(datasource.getDatabaseName());
		queryParam.setTable(tableName);

		// 查询字段列表
		Accessor dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		List<ColumnInfoBO> columnInfoList = dbAccessor.showColumns(dbConfig, queryParam); // 提取字段名称
		List<String> columnNames = columnInfoList.stream()
			.map(ColumnInfoBO::getName)
			.filter(name -> name != null && !name.trim().isEmpty())
			.sorted()
			.toList();

		log.info("Found {} columns for table {} in datasource: {}", columnNames.size(), tableName, datasourceId);
		return columnNames;
	}

	@Override
	public List<LogicalRelation> getLogicalRelations(Integer datasourceId) {
		log.info("Getting logical relations for datasource: {}", datasourceId);
		return logicalRelationMapper.selectByDatasourceId(datasourceId);
	}

	@Override
	public LogicalRelation addLogicalRelation(Integer datasourceId, LogicalRelation logicalRelation) {
		log.info("Adding logical relation for datasource: {}", datasourceId);

		// 设置数据源ID
		logicalRelation.setDatasourceId(datasourceId);

		// 检查是否已存在相同的外键关系
		int exists = logicalRelationMapper.checkExists(datasourceId, logicalRelation.getSourceTableName(),
				logicalRelation.getSourceColumnName(), logicalRelation.getTargetTableName(),
				logicalRelation.getTargetColumnName());

		if (exists > 0) {
			throw new RuntimeException("该逻辑外键关系已存在");
		}

		// 插入外键
		logicalRelationMapper.insert(logicalRelation);
		log.info("Logical relation added successfully with id: {}", logicalRelation.getId());

		return logicalRelation;
	}

	@Override
	public LogicalRelation updateLogicalRelation(Integer datasourceId, Integer logicalRelationId,
			LogicalRelation logicalRelation) {
		log.info("Updating logical relation: {} for datasource: {}", logicalRelationId, datasourceId);

		// 验证外键是否存在且属于该数据源
		LogicalRelation existingRelation = logicalRelationMapper.selectById(logicalRelationId);
		if (existingRelation == null) {
			throw new RuntimeException("逻辑外键不存在，ID: " + logicalRelationId);
		}

		if (!existingRelation.getDatasourceId().equals(datasourceId)) {
			throw new RuntimeException("逻辑外键不属于指定的数据源");
		}

		// 设置ID和数据源ID
		logicalRelation.setId(logicalRelationId);
		logicalRelation.setDatasourceId(datasourceId);

		// 更新外键
		int updated = logicalRelationMapper.updateById(logicalRelation);
		if (updated == 0) {
			throw new RuntimeException("更新逻辑外键失败");
		}

		log.info("Logical relation updated successfully: {}", logicalRelationId);

		// 返回更新后的数据
		return logicalRelationMapper.selectById(logicalRelationId);
	}

	@Override
	public void deleteLogicalRelation(Integer datasourceId, Integer logicalRelationId) {
		log.info("Deleting logical relation: {} for datasource: {}", logicalRelationId, datasourceId);

		// 验证外键是否属于该数据源
		LogicalRelation logicalRelation = logicalRelationMapper.selectById(logicalRelationId);
		if (logicalRelation == null) {
			throw new RuntimeException("逻辑外键不存在，ID: " + logicalRelationId);
		}

		if (!logicalRelation.getDatasourceId().equals(datasourceId)) {
			throw new RuntimeException("逻辑外键不属于指定的数据源");
		}

		// 删除外键（逻辑删除）
		int deleted = logicalRelationMapper.deleteById(logicalRelationId);
		if (deleted == 0) {
			throw new RuntimeException("删除逻辑外键失败");
		}

		log.info("Logical relation deleted successfully: {}", logicalRelationId);
	}

	@Override
	@Transactional
	public List<LogicalRelation> saveLogicalRelations(Integer datasourceId, List<LogicalRelation> logicalRelations) {
		log.info("Saving {} logical relations for datasource: {}", logicalRelations.size(), datasourceId);

		// 获取现有的所有外键关系
		List<LogicalRelation> existingRelations = logicalRelationMapper.selectByDatasourceId(datasourceId);
		Map<Integer, LogicalRelation> existingMap = existingRelations.stream()
			.collect(Collectors.toMap(LogicalRelation::getId, relation -> relation));

		// 收集传入列表中已存在的ID
		Set<Integer> incomingIds = logicalRelations.stream()
			.map(LogicalRelation::getId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());

		// 删除那些不在传入列表中的外键
		int deletedCount = 0;
		for (LogicalRelation existing : existingRelations) {
			if (!incomingIds.contains(existing.getId())) {
				logicalRelationMapper.deleteById(existing.getId());
				deletedCount++;
				log.info("Deleted logical relation: {} -> {}", existing.getSourceTableName(),
						existing.getTargetTableName());
			}
		}
		log.info("Deleted {} logical relations for datasource: {}", deletedCount, datasourceId);

		// 去重检查
		List<LogicalRelation> uniqueRelations = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		for (LogicalRelation logicalRelation : logicalRelations) {
			String key = logicalRelation.getSourceTableName() + "|" + logicalRelation.getSourceColumnName() + "|"
					+ logicalRelation.getTargetTableName() + "|" + logicalRelation.getTargetColumnName();

			if (!seen.contains(key)) {
				seen.add(key);
				uniqueRelations.add(logicalRelation);
			}
			else {
				log.warn("跳过重复的逻辑外键: {} -> {}", logicalRelation.getSourceTableName(),
						logicalRelation.getTargetTableName());
			}
		}

		int duplicateCount = logicalRelations.size() - uniqueRelations.size();
		if (duplicateCount > 0) {
			log.warn("检测到并去重了 {} 条重复的逻辑外键", duplicateCount);
		}

		// 插入或更新去重后的外键列表
		int insertedCount = 0;
		int updatedCount = 0;
		for (LogicalRelation logicalRelation : uniqueRelations) {
			logicalRelation.setDatasourceId(datasourceId);

			if (logicalRelation.getId() != null && existingMap.containsKey(logicalRelation.getId())) {
				// 更新现有记录
				logicalRelationMapper.updateById(logicalRelation);
				updatedCount++;
				log.debug("Updated logical relation: {} -> {}", logicalRelation.getSourceTableName(),
						logicalRelation.getTargetTableName());
			}
			else {
				// 插入新记录
				logicalRelation.setId(null);
				logicalRelationMapper.insert(logicalRelation);
				insertedCount++;
				log.debug("Inserted logical relation: {} -> {}", logicalRelation.getSourceTableName(),
						logicalRelation.getTargetTableName());
			}
		}

		log.info("Saved logical relations for datasource {}: {} inserted, {} updated, {} deleted", datasourceId,
				insertedCount, updatedCount, deletedCount);

		return logicalRelationMapper.selectByDatasourceId(datasourceId);
	}

}
