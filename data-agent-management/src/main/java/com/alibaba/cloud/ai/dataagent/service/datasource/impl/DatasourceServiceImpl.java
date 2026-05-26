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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPool;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPoolFactory;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.DatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticColumnMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticRelationMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticTableMapper;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.registry.DatasourceTypeHandlerRegistry;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@AllArgsConstructor
public class DatasourceServiceImpl implements DatasourceService {

	private final DatasourceMapper datasourceMapper;

	private final AgentDatasourceMapper agentDatasourceMapper;

	private final SemanticTableMapper semanticTableMapper;

	private final SemanticColumnMapper semanticColumnMapper;

	private final SemanticRelationMapper semanticRelationMapper;

	private final DBConnectionPoolFactory poolFactory;

	private final AccessorFactory accessorFactory;

	private final DatasourceTypeHandlerRegistry datasourceTypeHandlerRegistry;

	private final AgentVectorStoreService agentVectorStoreService;

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
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		String connectionUrl = handler.resolveConnectionUrl(datasource);
		if (StringUtils.isNotBlank(connectionUrl)) {
			datasource.setConnectionUrl(connectionUrl);
		}

		if (datasource.getStatus() == null) {
			datasource.setStatus("active");
		}
		if (datasource.getTestStatus() == null) {
			datasource.setTestStatus("unknown");
		}
		if (datasource.getPassword() == null) {
			datasource.setPassword("");
		}
		if (datasource.getUsername() == null) {
			datasource.setUsername("");
		}

		datasourceMapper.insert(datasource);
		return datasource;
	}

	@Override
	public Datasource updateDatasource(Integer id, Datasource datasource) {
		Datasource existingDatasource = datasourceMapper.selectById(id);
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		String connectionUrl = handler.resolveConnectionUrl(datasource);
		if (StringUtils.isNotBlank(connectionUrl)) {
			datasource.setConnectionUrl(connectionUrl);
		}
		datasource.setId(id);

		if (datasource.getPassword() == null) {
			datasource.setPassword("");
		}
		if (datasource.getUsername() == null) {
			datasource.setUsername("");
		}

		evictDatasourcePool(existingDatasource);
		datasourceMapper.updateById(datasource);
		return datasource;
	}

	@Override
	@Transactional
	public void deleteDatasource(Integer id) {
		Datasource datasource = datasourceMapper.selectById(id);
		List<AgentDatasource> agentDatasources = agentDatasourceMapper.selectByDatasourceId(id);
		for (AgentDatasource agentDatasource : agentDatasources) {
			if (agentDatasource == null || agentDatasource.getAgentId() == null) {
				continue;
			}
			if (agentDatasource.getIsActive() != null && agentDatasource.getIsActive() == 1) {
				int activeCount = agentDatasourceMapper.countActiveByAgentId(agentDatasource.getAgentId());
				if (activeCount <= 1) {
					throw new RuntimeException("当前智能体必须至少保留一个启用中的数据源");
				}
			}
			agentVectorStoreService.deleteSchemaDocuments(String.valueOf(agentDatasource.getAgentId()),
					String.valueOf(id));
		}

		evictDatasourcePool(datasource);
		agentDatasourceMapper.deleteAllByDatasourceId(id);
		semanticTableMapper.deleteByDatasourceId(id);
		semanticColumnMapper.deleteByDatasourceId(id);
		semanticRelationMapper.deleteByDatasourceId(id);
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
			boolean connectionSuccess = realConnectionTest(datasource);
			log.info("{} test connection result: {}", datasource.getName(), connectionSuccess);
			updateTestStatus(id, connectionSuccess ? "success" : "failed");
			return connectionSuccess;
		}
		catch (Exception e) {
			updateTestStatus(id, "failed");
			log.error("Error testing connection for datasource ID {}: {}", id, e.getMessage(), e);
			return false;
		}
	}

	private boolean realConnectionTest(Datasource datasource) {
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

	private void evictDatasourcePool(Datasource datasource) {
		if (datasource == null || datasource.getType() == null) {
			return;
		}
		DBConnectionPool pool = poolFactory.getPoolByType(datasource.getType());
		if (pool == null) {
			return;
		}
		try {
			pool.evict(getDbConfig(datasource));
		}
		catch (Exception e) {
			log.warn("Failed to evict datasource pool for datasourceId={}: {}", datasource.getId(), e.getMessage());
		}
	}

	@Override
	public List<String> getDatasourceTables(Integer datasourceId) throws Exception {
		log.info("Getting tables for datasource: {}", datasourceId);
		Datasource datasource = this.getDatasourceById(datasourceId);
		if (datasource == null) {
			throw new RuntimeException("Datasource not found with id: " + datasourceId);
		}

		DbConfigBO dbConfig = getDbConfig(datasource);
		DbQueryParameter queryParam = DbQueryParameter.from(dbConfig);
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		String schemaName = handler.extractSchemaName(datasource);
		queryParam.setSchema(schemaName);

		Accessor dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		List<TableInfoBO> tableInfoList = dbAccessor.showTables(dbConfig, queryParam);
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
		Datasource datasource = this.getDatasourceById(datasourceId);
		if (datasource == null) {
			throw new RuntimeException("Datasource not found with id: " + datasourceId);
		}

		DbConfigBO dbConfig = getDbConfig(datasource);
		DbQueryParameter queryParam = DbQueryParameter.from(dbConfig);
		DatasourceTypeHandler handler = datasourceTypeHandlerRegistry.getRequired(datasource.getType());
		String schemaName = handler.extractSchemaName(datasource);
		queryParam.setSchema(schemaName);
		queryParam.setTable(tableName);

		Accessor dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		List<ColumnInfoBO> columnInfoList = dbAccessor.showColumns(dbConfig, queryParam);
		List<String> columnNames = columnInfoList.stream()
			.map(ColumnInfoBO::getName)
			.filter(name -> name != null && !name.trim().isEmpty())
			.sorted()
			.toList();

		log.info("Found {} columns for table {} in datasource: {}", columnNames.size(), tableName, datasourceId);
		return columnNames;
	}

}
