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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Utility class for processing database.
 */
@Slf4j
@Component
@AllArgsConstructor
public class DatabaseUtil {

	private final AccessorFactory accessorFactory;

	private final AgentDatasourceService agentDatasourceService;

	private final DatasourceService datasourceService;

	public DbConfigBO getAgentDbConfig(Integer agentId) {
		log.info("Getting datasource config for agent: {}", agentId);

		// Get the enabled data source for the agent
		AgentDatasource activeDatasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
		// Convert to DbConfig
		DbConfigBO dbConfig = datasourceService.getDbConfig(activeDatasource.getDatasource());
		log.info("Successfully created DbConfig for agent {}: url={}, schema={}, type={}", agentId, dbConfig.getUrl(),
				dbConfig.getSchema(), dbConfig.getDialectType());

		return dbConfig;
	}

	public Accessor getAgentAccessor(Integer agentId) {
		DbConfigBO dbConfig = getAgentDbConfig(agentId);
		return accessorFactory.getAccessorByDbConfig(dbConfig);
	}

}
