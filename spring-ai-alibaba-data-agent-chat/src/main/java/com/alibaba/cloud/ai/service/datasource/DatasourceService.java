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

package com.alibaba.cloud.ai.service.datasource;

import com.alibaba.cloud.ai.entity.AgentDatasource;
import com.alibaba.cloud.ai.entity.Datasource;

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

	Datasource getActiveDatasourceByAgentId(Integer agentId);

}
