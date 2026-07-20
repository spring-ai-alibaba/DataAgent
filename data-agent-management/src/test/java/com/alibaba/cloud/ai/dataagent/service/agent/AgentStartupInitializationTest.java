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
package com.alibaba.cloud.ai.dataagent.service.agent;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentStartupInitializationTest {

	@Mock
	private AgentService agentService;

	@Mock
	private AgentVectorStoreService agentVectorStoreService;

	@Mock
	private AgentDatasourceService agentDatasourceService;

	@Mock
	private ExecutorService executorService;

	private AgentStartupInitialization initialization;

	@BeforeEach
	void setUp() {
		initialization = new AgentStartupInitialization(agentService, agentVectorStoreService, agentDatasourceService,
				executorService);
	}

	@Test
	void initializeAgentDataSource_completeDatasourceIndex_skipsRebuild() {
		Agent agent = new Agent();
		agent.setId(9L);
		AgentDatasource datasource = new AgentDatasource();
		datasource.setDatasourceId(6);
		datasource.setSelectTables(List.of("delivery_actual_daily", "part_mappings"));
		when(agentDatasourceService.getCurrentAgentDatasource(9L)).thenReturn(datasource);
		when(agentVectorStoreService.getDocumentsOnlyByFilter(any(), eq(2)))
			.thenReturn(List.of(tableDocument("delivery_actual_daily"), tableDocument("part_mappings")));

		assertTrue(initialization.initializeAgentDataSource(agent));

		verify(agentDatasourceService, never()).initializeSchemaForAgentWithDatasource(anyLong(), anyInt(), anyList());
	}

	private Document tableDocument(String name) {
		return new Document(name, Map.of("name", name));
	}

}
