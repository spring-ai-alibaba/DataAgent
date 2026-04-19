/*
 * Copyright 2026 the original author or authors.
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
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentDatasourceServiceImplTest {

	private AgentDatasourceServiceImpl service;

	@Mock
	private DatasourceService datasourceService;

	@Mock
	private SchemaService schemaService;

	@Mock
	private AgentDatasourceMapper agentDatasourceMapper;

	@Mock
	private AgentDatasourceTablesMapper tablesMapper;

	@BeforeEach
	void setUp() {
		service = new AgentDatasourceServiceImpl(datasourceService, schemaService, agentDatasourceMapper, tablesMapper);
	}

	@Test
	void testInitializeSchemaForAgentWithDatasource_success() throws Exception {
		Datasource ds = new Datasource();
		DbConfigBO dbConfig = new DbConfigBO();
		when(datasourceService.getDatasourceById(1)).thenReturn(ds);
		when(datasourceService.getDbConfig(ds)).thenReturn(dbConfig);
		when(schemaService.schema(eq(1), any(SchemaInitRequest.class))).thenReturn(true);

		Boolean result = service.initializeSchemaForAgentWithDatasource(1L, 1, List.of("table1"));
		assertTrue(result);
	}

	@Test
	void testInitializeSchemaForAgentWithDatasource_datasourceNotFound() {
		when(datasourceService.getDatasourceById(1)).thenReturn(null);

		assertThrows(RuntimeException.class,
				() -> service.initializeSchemaForAgentWithDatasource(1L, 1, List.of("table1")));
	}

	@Test
	void testInitializeSchemaForAgentWithDatasource_nullAgentId() {
		assertThrows(IllegalArgumentException.class,
				() -> service.initializeSchemaForAgentWithDatasource(null, 1, List.of("table1")));
	}

	@Test
	void testInitializeSchemaForAgentWithDatasource_emptyTables() {
		assertThrows(IllegalArgumentException.class,
				() -> service.initializeSchemaForAgentWithDatasource(1L, 1, List.of()));
	}

	@Test
	void testGetAgentDatasource() {
		AgentDatasource ad = new AgentDatasource();
		ad.setDatasourceId(1);
		ad.setId(10);
		Datasource ds = new Datasource();
		when(agentDatasourceMapper.selectByAgentIdWithDatasource(1L)).thenReturn(List.of(ad));
		when(datasourceService.getDatasourceById(1)).thenReturn(ds);
		when(tablesMapper.getAgentDatasourceTables(10)).thenReturn(List.of("t1"));

		List<AgentDatasource> result = service.getAgentDatasource(1L);
		assertEquals(1, result.size());
		assertEquals(ds, result.get(0).getDatasource());
		assertEquals(List.of("t1"), result.get(0).getSelectTables());
	}

	@Test
	void testGetAgentDatasource_nullAgentId() {
		assertThrows(IllegalArgumentException.class, () -> service.getAgentDatasource(null));
	}

	@Test
	void testAddDatasourceToAgent_newAssociation() {
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(1L, 1)).thenReturn(null);

		AgentDatasource result = service.addDatasourceToAgent(1L, 1);
		assertNotNull(result);
		verify(agentDatasourceMapper).disableAllByAgentId(1L);
		verify(agentDatasourceMapper).createNewRelationEnabled(1L, 1);
	}

	@Test
	void testAddDatasourceToAgent_existingAssociation() {
		AgentDatasource existing = new AgentDatasource();
		existing.setId(10);
		AgentDatasource updated = new AgentDatasource();
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(1L, 1)).thenReturn(existing, updated);

		AgentDatasource result = service.addDatasourceToAgent(1L, 1);
		assertNotNull(result);
		verify(agentDatasourceMapper).enableRelation(1L, 1);
		verify(tablesMapper).removeAllTables(10);
	}

	@Test
	void testRemoveDatasourceFromAgent() {
		service.removeDatasourceFromAgent(1L, 1);
		verify(agentDatasourceMapper).removeRelation(1L, 1);
	}

	@Test
	void testToggleDatasourceForAgent_enable_noConflict() {
		when(agentDatasourceMapper.countActiveByAgentIdExcluding(1L, 1)).thenReturn(0);
		when(agentDatasourceMapper.updateRelation(1L, 1, 1)).thenReturn(1);
		AgentDatasource expected = new AgentDatasource();
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(1L, 1)).thenReturn(expected);

		AgentDatasource result = service.toggleDatasourceForAgent(1L, 1, true);
		assertEquals(expected, result);
	}

	@Test
	void testToggleDatasourceForAgent_enable_conflict() {
		when(agentDatasourceMapper.countActiveByAgentIdExcluding(1L, 1)).thenReturn(1);

		assertThrows(RuntimeException.class, () -> service.toggleDatasourceForAgent(1L, 1, true));
	}

	@Test
	void testToggleDatasourceForAgent_disable() {
		when(agentDatasourceMapper.updateRelation(1L, 1, 0)).thenReturn(1);
		AgentDatasource expected = new AgentDatasource();
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(1L, 1)).thenReturn(expected);

		AgentDatasource result = service.toggleDatasourceForAgent(1L, 1, false);
		assertEquals(expected, result);
	}

	@Test
	void testToggleDatasourceForAgent_notFound() {
		when(agentDatasourceMapper.updateRelation(1L, 1, 0)).thenReturn(0);

		assertThrows(RuntimeException.class, () -> service.toggleDatasourceForAgent(1L, 1, false));
	}

	@Test
	void testUpdateDatasourceTables_success() {
		AgentDatasource ad = new AgentDatasource();
		ad.setId(10);
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(1L, 1)).thenReturn(ad);

		service.updateDatasourceTables(1L, 1, List.of("t1", "t2"));
		verify(tablesMapper).updateAgentDatasourceTables(10, List.of("t1", "t2"));
	}

	@Test
	void testUpdateDatasourceTables_emptyTables() {
		AgentDatasource ad = new AgentDatasource();
		ad.setId(10);
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(1L, 1)).thenReturn(ad);

		service.updateDatasourceTables(1L, 1, List.of());
		verify(tablesMapper).removeAllTables(10);
	}

	@Test
	void testUpdateDatasourceTables_nullParams() {
		assertThrows(IllegalArgumentException.class, () -> service.updateDatasourceTables(null, 1, List.of()));
	}

	@Test
	void testUpdateDatasourceTables_notFound() {
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(1L, 1)).thenReturn(null);

		assertThrows(IllegalArgumentException.class, () -> service.updateDatasourceTables(1L, 1, List.of("t1")));
	}

}
