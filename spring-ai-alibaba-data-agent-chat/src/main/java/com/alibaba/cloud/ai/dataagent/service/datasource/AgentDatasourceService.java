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

package com.alibaba.cloud.ai.dataagent.service.datasource;

import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;

import java.util.List;

public interface AgentDatasourceService {

	/**
	 * Initialize agent's database schema using datasource
	 */
	Boolean initializeSchemaForAgentWithDatasource(Long agentId, Integer datasourceId, List<String> tables);

	List<AgentDatasource> getAgentDatasource(Integer agentId);

	default AgentDatasource getCurrentAgentDatasource(Integer agentId) {
		return getAgentDatasource(agentId).stream()
			.filter(a -> a.getIsActive() != 0)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Agent " + agentId + " has no active datasource"));
	}

	AgentDatasource addDatasourceToAgent(Integer agentId, Integer datasourceId);

	void removeDatasourceFromAgent(Integer agentId, Integer datasourceId);

	AgentDatasource toggleDatasourceForAgent(Integer agentId, Integer datasourceId, Boolean isActive);

	void updateDatasourceTables(Integer agentId, Integer datasourceId, List<String> tables);

}
