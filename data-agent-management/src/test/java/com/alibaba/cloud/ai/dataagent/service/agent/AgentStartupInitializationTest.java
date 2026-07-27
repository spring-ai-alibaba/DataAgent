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

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.mapper.BusinessKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.ModelConfigDataService;
import com.alibaba.cloud.ai.dataagent.service.business.BusinessKnowledgeService;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
	private BusinessKnowledgeService businessKnowledgeService;

	@Mock
	private AgentKnowledgeService agentKnowledgeService;

	@Mock
	private BusinessKnowledgeMapper businessKnowledgeMapper;

	@Mock
	private AgentKnowledgeMapper agentKnowledgeMapper;

	@Mock
	private ModelConfigDataService modelConfigDataService;

	@Mock
	private ExecutorService executorService;

	@Mock
	private ApplicationArguments applicationArguments;

	private AgentStartupInitialization initialization;

	@BeforeEach
	void setUp() {
		doAnswer(invocation -> {
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(executorService).execute(any(Runnable.class));
		when(agentService.findByStatus("published")).thenReturn(List.of());
		initialization = new AgentStartupInitialization(agentService, agentVectorStoreService, agentDatasourceService,
				businessKnowledgeService, agentKnowledgeService, businessKnowledgeMapper, agentKnowledgeMapper,
				modelConfigDataService, executorService);
	}

	@Test
	void run_withoutActiveEmbeddingModel_keepsPendingKnowledgeUntouched() {
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		initialization.run(applicationArguments);

		verify(businessKnowledgeMapper, never()).selectAll();
		verify(agentKnowledgeMapper, never()).selectPendingAndRecalled();
	}

	@Test
	void run_withActiveEmbeddingModel_checksBothPendingKnowledgeSources() {
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(ModelConfigDTO.builder().build());
		when(businessKnowledgeMapper.selectAll()).thenReturn(List.of());
		when(agentKnowledgeMapper.selectPendingAndRecalled()).thenReturn(List.of());

		initialization.run(applicationArguments);

		verify(businessKnowledgeMapper).selectAll();
		verify(agentKnowledgeMapper).selectPendingAndRecalled();
	}

	@Test
	void run_schemaExistsForActiveDatasource_skipsDuplicateInitialization() {
		Agent agent = Agent.builder().id(1L).name("published").status("published").build();
		AgentDatasource datasource = new AgentDatasource();
		datasource.setDatasourceId(3);
		datasource.setSelectTables(List.of("orders"));
		when(agentService.findByStatus("published")).thenReturn(List.of(agent));
		when(agentDatasourceService.getCurrentAgentDatasource(1L)).thenReturn(datasource);
		when(agentVectorStoreService.hasTableDocuments(3, List.of("orders"))).thenReturn(true);
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		initialization.run(applicationArguments);

		verify(agentDatasourceService, never()).initializeSchemaForAgentWithDatasource(any(), any(), any());
	}

	@Test
	void run_onlyBusinessDocumentsExist_initializesMissingSchemaForActiveDatasource() {
		Agent agent = Agent.builder().id(1L).name("published").status("published").build();
		AgentDatasource datasource = new AgentDatasource();
		datasource.setDatasourceId(3);
		datasource.setSelectTables(List.of("orders"));
		when(agentService.findByStatus("published")).thenReturn(List.of(agent));
		when(agentDatasourceService.getCurrentAgentDatasource(1L)).thenReturn(datasource);
		when(agentVectorStoreService.hasTableDocuments(3, List.of("orders"))).thenReturn(false);
		when(agentDatasourceService.initializeSchemaForAgentWithDatasource(1L, 3, List.of("orders"))).thenReturn(true);
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		initialization.run(applicationArguments);

		verify(agentDatasourceService).initializeSchemaForAgentWithDatasource(1L, 3, List.of("orders"));
	}

}
