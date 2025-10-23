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

import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.entity.AgentDatasource;
import com.alibaba.cloud.ai.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.request.SchemaInitRequest;
import com.alibaba.cloud.ai.service.AgentDatasourceService;
import com.alibaba.cloud.ai.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.util.SchemaProcessorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;

@Slf4j
@Service
public class AgentDatasourceServiceImpl implements AgentDatasourceService {

	private final DatasourceService datasourceService;

	private final AgentVectorStoreService vectorStoreService;

	private final AgentDatasourceMapper agentDatasourceMapper;

	public AgentDatasourceServiceImpl(DatasourceService datasourceService, AgentVectorStoreService vectorStoreService,
			AgentDatasourceMapper agentDatasourceMapper) {
		this.datasourceService = datasourceService;
		this.vectorStoreService = vectorStoreService;
		this.agentDatasourceMapper = agentDatasourceMapper;
	}

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

	@Override
	public List<String> getDatasourceTables(Integer datasourceId) throws Exception {
		Assert.notNull(datasourceId, "Datasource ID cannot be null");
		return datasourceService.getDatasourceTables(datasourceId);
	}

	@Override
	public List<AgentDatasource> getAgentDatasource(Integer agentId) {
		Assert.notNull(agentId, "Agent ID cannot be null");
		return datasourceService.getAgentDatasource(agentId);
	}

	@Override
	@Transactional
	public AgentDatasource addDatasourceToAgent(Integer agentId, Integer datasourceId) {
		// First, disable other data sources for this agent (an agent can only have one
		// enabled data source)
		agentDatasourceMapper.disableAllByAgentId(agentId);

		// Check if an association already exists
		AgentDatasource existing = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);

		if (existing != null) {
			// If it exists, activate the association
			agentDatasourceMapper.enableRelation(agentId, datasourceId);

			// Query and return the updated association
			return agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		}
		else {
			// If it does not exist, create a new association
			AgentDatasource agentDatasource = new AgentDatasource(agentId, datasourceId);
			agentDatasource.setIsActive(1);
			agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId);
			return agentDatasource;
		}
	}

	@Override
	public void removeDatasourceFromAgent(Integer agentId, Integer datasourceId) {
		agentDatasourceMapper.removeRelation(agentId, datasourceId);
	}

	@Override
	public AgentDatasource toggleDatasourceForAgent(Integer agentId, Integer datasourceId, Boolean isActive) {
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

}
