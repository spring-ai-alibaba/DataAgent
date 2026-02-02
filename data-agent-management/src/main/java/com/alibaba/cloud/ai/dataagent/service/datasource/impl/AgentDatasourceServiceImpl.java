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
import com.alibaba.cloud.ai.dataagent.dto.datasource.SchemaInitRequest;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceTablesMapper;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Slf4j
@Service
@AllArgsConstructor
public class AgentDatasourceServiceImpl implements AgentDatasourceService {

	private final DatasourceService datasourceService;

	private final SchemaService schemaService;

	private final AgentDatasourceMapper agentDatasourceMapper;

	private final AgentDatasourceTablesMapper tablesMapper;

	@Override
	public Boolean initializeSchemaForAgentWithDatasource(Long agentId, Integer datasourceId, List<String> tables) {
		Assert.notNull(agentId, "Agent ID cannot be null");
		Assert.notNull(datasourceId, "Datasource ID cannot be null");
		Assert.notEmpty(tables, "Tables cannot be empty");
		try {
			String agentIdStr = String.valueOf(agentId);
			log.info("Initializing schema for agent: {} with datasource: {}, tables: {}", agentIdStr, datasourceId,
					tables);

			// Get data source information
			Datasource datasource = datasourceService.getDatasourceById(datasourceId);
			if (datasource == null) {
				throw new RuntimeException("Datasource not found with id: " + datasourceId);
			}

			// Create database configuration
			DbConfigBO dbConfig = datasourceService.getDbConfig(datasource);

			// Create SchemaInitRequest
			SchemaInitRequest schemaInitRequest = new SchemaInitRequest();
			schemaInitRequest.setDbConfig(dbConfig);
			schemaInitRequest.setTables(tables);

			log.info("Created SchemaInitRequest for agent: {}, dbConfig: {}, tables: {}", agentIdStr, dbConfig, tables);

			// Call the original initialization method
			return schemaService.schema(datasourceId, schemaInitRequest);

		}
		catch (Exception e) {
			log.error("Failed to initialize schema for agent: {} with datasource: {}", agentId, datasourceId, e);
			throw new RuntimeException("Failed to initialize schema for agent " + agentId + ": " + e.getMessage(), e);
		}
	}

	@Override
	public List<AgentDatasource> getAgentDatasource(Long agentId) {
		Assert.notNull(agentId, "Agent ID cannot be null");
		List<AgentDatasource> adentDatasources = agentDatasourceMapper.selectByAgentIdWithDatasource(agentId);

		// Manually fill in the data source information (since MyBatis Plus does not
		// directly support complex join query result mapping)
		for (AgentDatasource agentDatasource : adentDatasources) {
			if (agentDatasource.getDatasourceId() != null) {
				Datasource datasource = datasourceService.getDatasourceById(agentDatasource.getDatasourceId());
				agentDatasource.setDatasource(datasource);
			}
			// 获取选中的数据表
			int id = agentDatasource.getId();
			List<String> tables = tablesMapper.getAgentDatasourceTables(id);
			agentDatasource.setSelectTables(Optional.ofNullable(tables).orElse(List.of()));
		}

		return adentDatasources;
	}

	@Override
	@Transactional
	public AgentDatasource addDatasourceToAgent(Long agentId, Integer datasourceId) {
		// First, disable other data sources for this agent (an agent can only have one
		// enabled data source)
		agentDatasourceMapper.disableAllByAgentId(agentId);

		// Check if an association already exists
		AgentDatasource existing = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);

		AgentDatasource result;
		if (existing != null) {
			// If it exists, activate the association
			agentDatasourceMapper.enableRelation(agentId, datasourceId);

			// 删除已有的表
			tablesMapper.removeAllTables(existing.getId());

			// Query and return the updated association
			result = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		}
		else {
			// If it does not exist, create a new association
			AgentDatasource agentDatasource = new AgentDatasource(agentId, datasourceId);
			agentDatasource.setIsActive(1);
			agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId);
			result = agentDatasource;
		}
		result.setSelectTables(List.of());
		return result;
	}

	@Override
	public void removeDatasourceFromAgent(Long agentId, Integer datasourceId) {
		agentDatasourceMapper.removeRelation(agentId, datasourceId);
	}

	@Override
	public AgentDatasource toggleDatasourceForAgent(Long agentId, Integer datasourceId, Boolean isActive) {
		// If enabling data source, first check if there are other enabled data sources
		if (isActive) {
			int activeCount = agentDatasourceMapper.countActiveByAgentIdExcluding(agentId, datasourceId);
			if (activeCount > 0) {
				throw new RuntimeException("同一智能体下只能启用一个数据源，请先禁用其他数据源后再启用此数据源");
			}
		}

		// Update data source status
		int updated = agentDatasourceMapper.updateRelation(agentId, datasourceId, isActive ? 1 : 0);

		if (updated == 0) {
			throw new RuntimeException("未找到相关的数据源关联记录");
		}

		// Return the updated association record
		return agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
	}

	@Override
	@Transactional
	public void updateDatasourceTables(Long agentId, Integer datasourceId, List<String> tables) {
		if (agentId == null || datasourceId == null || tables == null) {
			throw new IllegalArgumentException("参数不能为空");
		}
		AgentDatasource datasource = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		if (datasource == null) {
			throw new IllegalArgumentException("未找到对应的数据源关联记录");
		}
		if (tables.isEmpty()) {
			tablesMapper.removeAllTables(datasource.getId());
		}
		else {
			tablesMapper.updateAgentDatasourceTables(datasource.getId(), tables);
		}
	}

}
