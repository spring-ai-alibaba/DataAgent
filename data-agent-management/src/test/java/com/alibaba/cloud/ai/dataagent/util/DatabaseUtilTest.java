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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatabaseUtilTest {

	@Mock
	private AccessorFactory accessorFactory;

	@Mock
	private AgentDatasourceService agentDatasourceService;

	@Mock
	private DatasourceService datasourceService;

	@InjectMocks
	private DatabaseUtil databaseUtil;

	@Test
	void getAgentDbConfig_success() {
		Datasource datasource = new Datasource();
		AgentDatasource agentDatasource = new AgentDatasource();
		agentDatasource.setDatasource(datasource);

		DbConfigBO expectedConfig = new DbConfigBO();
		expectedConfig.setUrl("jdbc:mysql://localhost:3306/test");
		expectedConfig.setSchema("test");
		expectedConfig.setDialectType("mysql");

		when(agentDatasourceService.getCurrentAgentDatasource(1L)).thenReturn(agentDatasource);
		when(datasourceService.getDbConfig(datasource)).thenReturn(expectedConfig);

		DbConfigBO result = databaseUtil.getAgentDbConfig(1L);

		assertNotNull(result);
		assertEquals("jdbc:mysql://localhost:3306/test", result.getUrl());
		verify(agentDatasourceService).getCurrentAgentDatasource(1L);
		verify(datasourceService).getDbConfig(datasource);
	}

	@Test
	void getAgentAccessor_success() {
		Datasource datasource = new Datasource();
		AgentDatasource agentDatasource = new AgentDatasource();
		agentDatasource.setDatasource(datasource);

		DbConfigBO config = new DbConfigBO();
		config.setUrl("jdbc:mysql://localhost:3306/test");

		Accessor mockAccessor = mock(Accessor.class);

		when(agentDatasourceService.getCurrentAgentDatasource(1L)).thenReturn(agentDatasource);
		when(datasourceService.getDbConfig(datasource)).thenReturn(config);
		when(accessorFactory.getAccessorByDbConfig(config)).thenReturn(mockAccessor);

		Accessor result = databaseUtil.getAgentAccessor(1L);

		assertNotNull(result);
		assertSame(mockAccessor, result);
	}

}
