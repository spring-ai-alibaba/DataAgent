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
package com.alibaba.cloud.ai.dataagent.service.datasource;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;

import java.util.List;

public interface DatasourceService {

	/**
	 * Get all data source list
	 */
	List<Datasource> getAllDatasource();

	/**
	 * Get data source list by status
	 */
	List<Datasource> getDatasourceByStatus(String status);

	/**
	 * Get data source list by type
	 */
	List<Datasource> getDatasourceByType(String type);

	/**
	 * Get data source details by ID
	 */
	Datasource getDatasourceById(Integer id);

	/**
	 * Create data source
	 */
	Datasource createDatasource(Datasource datasource);

	/**
	 * Update data source
	 */
	Datasource updateDatasource(Integer id, Datasource datasource);

	/**
	 * Delete data source
	 */
	void deleteDatasource(Integer id);

	/**
	 * Update data source test status
	 */
	void updateTestStatus(Integer id, String testStatus);

	/**
	 * Test data source connection
	 */
	boolean testConnection(Integer id);

	/**
	 * Get data source list associated with agent
	 */
	// 应该使用 AgentDatasourceService 中的方法
	@Deprecated
	List<AgentDatasource> getAgentDatasource(Integer agentId);

	List<String> getDatasourceTables(Integer datasourceId) throws Exception;

	/**
	 * 获取数据源表的字段列表
	 */
	List<String> getTableColumns(Integer datasourceId, String tableName) throws Exception;

	DbConfigBO getDbConfig(Datasource datasource);

	/**
	 * 获取数据源的逻辑外键列表
	 */
	List<LogicalRelation> getLogicalRelations(Integer datasourceId);

	/**
	 * 添加逻辑外键
	 */
	LogicalRelation addLogicalRelation(Integer datasourceId, LogicalRelation logicalRelation);

	/**
	 * 更新逻辑外键
	 */
	LogicalRelation updateLogicalRelation(Integer datasourceId, Integer relationId, LogicalRelation logicalRelation);

	/**
	 * 删除逻辑外键
	 */
	void deleteLogicalRelation(Integer datasourceId, Integer logicalRelationId);

	/**
	 * 批量保存逻辑外键（替换现有的所有外键）
	 */
	List<LogicalRelation> saveLogicalRelations(Integer datasourceId, List<LogicalRelation> logicalRelations);

}
