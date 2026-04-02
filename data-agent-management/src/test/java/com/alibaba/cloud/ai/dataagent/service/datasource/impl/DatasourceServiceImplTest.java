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

import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPool;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPoolFactory;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.DatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.LogicalRelationMapper;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.registry.DatasourceTypeHandlerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatasourceServiceImplTest {

	private DatasourceServiceImpl datasourceService;

	@Mock
	private DatasourceMapper datasourceMapper;

	@Mock
	private AgentDatasourceMapper agentDatasourceMapper;

	@Mock
	private LogicalRelationMapper logicalRelationMapper;

	@Mock
	private DBConnectionPoolFactory poolFactory;

	@Mock
	private AccessorFactory accessorFactory;

	@Mock
	private DatasourceTypeHandlerRegistry handlerRegistry;

	@Mock
	private DatasourceTypeHandler typeHandler;

	@Mock
	private DBConnectionPool dbConnectionPool;

	@BeforeEach
	void setUp() {
		datasourceService = new DatasourceServiceImpl(datasourceMapper, agentDatasourceMapper, logicalRelationMapper,
				poolFactory, accessorFactory, handlerRegistry);
	}

	@Test
	void getAllDatasource_returnsList() {
		Datasource ds = Datasource.builder().id(1).name("test").build();
		when(datasourceMapper.selectAll()).thenReturn(List.of(ds));

		List<Datasource> result = datasourceService.getAllDatasource();
		assertEquals(1, result.size());
		assertEquals("test", result.get(0).getName());
	}

	@Test
	void getDatasourceByStatus_returnsList() {
		when(datasourceMapper.selectByStatus("active")).thenReturn(List.of(Datasource.builder().build()));

		List<Datasource> result = datasourceService.getDatasourceByStatus("active");
		assertEquals(1, result.size());
	}

	@Test
	void getDatasourceByType_returnsList() {
		when(datasourceMapper.selectByType("mysql")).thenReturn(List.of(Datasource.builder().build()));

		List<Datasource> result = datasourceService.getDatasourceByType("mysql");
		assertEquals(1, result.size());
	}

	@Test
	void getDatasourceById_returnsDatasource() {
		Datasource ds = Datasource.builder().id(1).name("test").build();
		when(datasourceMapper.selectById(1)).thenReturn(ds);

		Datasource result = datasourceService.getDatasourceById(1);
		assertEquals("test", result.getName());
	}

	@Test
	void createDatasource_setsDefaultValues() {
		Datasource ds = Datasource.builder().type("mysql").build();
		when(handlerRegistry.getRequired("mysql")).thenReturn(typeHandler);
		when(typeHandler.resolveConnectionUrl(ds)).thenReturn("jdbc:mysql://localhost/test");

		Datasource result = datasourceService.createDatasource(ds);

		assertEquals("active", result.getStatus());
		assertEquals("unknown", result.getTestStatus());
		assertEquals("jdbc:mysql://localhost/test", result.getConnectionUrl());
		assertEquals("", result.getPassword());
		assertEquals("", result.getUsername());
		verify(datasourceMapper).insert(ds);
	}

	@Test
	void createDatasource_preservesExistingValues() {
		Datasource ds = Datasource.builder().type("mysql").status("inactive").testStatus("failed")
			.username("user").password("pass").build();
		when(handlerRegistry.getRequired("mysql")).thenReturn(typeHandler);
		when(typeHandler.resolveConnectionUrl(ds)).thenReturn(null);

		Datasource result = datasourceService.createDatasource(ds);

		assertEquals("inactive", result.getStatus());
		assertEquals("failed", result.getTestStatus());
		assertEquals("user", result.getUsername());
		assertEquals("pass", result.getPassword());
	}

	@Test
	void updateDatasource_updatesRecord() {
		Datasource ds = Datasource.builder().type("mysql").build();
		when(handlerRegistry.getRequired("mysql")).thenReturn(typeHandler);
		when(typeHandler.resolveConnectionUrl(ds)).thenReturn("jdbc:mysql://localhost/test");

		Datasource result = datasourceService.updateDatasource(5, ds);

		assertEquals(5, result.getId());
		verify(datasourceMapper).updateById(ds);
	}

	@Test
	void deleteDatasource_deletesAssociationsFirst() {
		datasourceService.deleteDatasource(1);

		verify(agentDatasourceMapper).deleteAllByDatasourceId(1);
		verify(datasourceMapper).deleteById(1);
	}

	@Test
	void updateTestStatus_callsMapper() {
		datasourceService.updateTestStatus(1, "success");
		verify(datasourceMapper).updateTestStatusById(1, "success");
	}

	@Test
	void testConnection_datasourceNotFound_returnsFalse() {
		when(datasourceMapper.selectById(1)).thenReturn(null);

		assertFalse(datasourceService.testConnection(1));
	}

	@Test
	void testConnection_success_returnsTrue() {
		Datasource ds = Datasource.builder().id(1).name("test").type("mysql")
			.username("user").password("pass").build();
		when(datasourceMapper.selectById(1)).thenReturn(ds);
		when(handlerRegistry.getRequired("mysql")).thenReturn(typeHandler);
		when(typeHandler.resolveConnectionUrl(ds)).thenReturn("jdbc:mysql://localhost/test");
		when(typeHandler.normalizeTestUrl(any(), anyString())).thenReturn("jdbc:mysql://localhost/test");
		when(poolFactory.getPoolByType("mysql")).thenReturn(dbConnectionPool);
		when(dbConnectionPool.ping(any())).thenReturn(ErrorCodeEnum.SUCCESS);

		assertTrue(datasourceService.testConnection(1));
		verify(datasourceMapper).updateTestStatusById(1, "success");
	}

	@Test
	void testConnection_poolNull_returnsFalse() {
		Datasource ds = Datasource.builder().id(1).name("test").type("mysql")
			.username("user").password("pass").build();
		when(datasourceMapper.selectById(1)).thenReturn(ds);
		when(handlerRegistry.getRequired("mysql")).thenReturn(typeHandler);
		when(typeHandler.resolveConnectionUrl(ds)).thenReturn("jdbc:mysql://localhost/test");
		when(typeHandler.normalizeTestUrl(any(), anyString())).thenReturn("jdbc:mysql://localhost/test");
		when(poolFactory.getPoolByType("mysql")).thenReturn(null);

		assertFalse(datasourceService.testConnection(1));
		verify(datasourceMapper).updateTestStatusById(1, "failed");
	}

	@Test
	void testConnection_exception_returnsFalse() {
		Datasource ds = Datasource.builder().id(1).name("test").type("mysql").build();
		when(datasourceMapper.selectById(1)).thenReturn(ds);
		when(handlerRegistry.getRequired("mysql")).thenThrow(new RuntimeException("error"));

		assertFalse(datasourceService.testConnection(1));
		verify(datasourceMapper).updateTestStatusById(1, "failed");
	}

	@Test
	void getLogicalRelations_returnsList() {
		LogicalRelation lr = new LogicalRelation();
		lr.setId(1);
		when(logicalRelationMapper.selectByDatasourceId(1)).thenReturn(List.of(lr));

		List<LogicalRelation> result = datasourceService.getLogicalRelations(1);
		assertEquals(1, result.size());
	}

	@Test
	void addLogicalRelation_checksExistence_throwsOnDuplicate() {
		LogicalRelation lr = new LogicalRelation();
		lr.setSourceTableName("a");
		lr.setSourceColumnName("b");
		lr.setTargetTableName("c");
		lr.setTargetColumnName("d");

		when(logicalRelationMapper.checkExists(1, "a", "b", "c", "d")).thenReturn(1);

		assertThrows(RuntimeException.class, () -> datasourceService.addLogicalRelation(1, lr));
	}

	@Test
	void addLogicalRelation_insertsNewRelation() {
		LogicalRelation lr = new LogicalRelation();
		lr.setSourceTableName("a");
		lr.setSourceColumnName("b");
		lr.setTargetTableName("c");
		lr.setTargetColumnName("d");

		when(logicalRelationMapper.checkExists(1, "a", "b", "c", "d")).thenReturn(0);

		LogicalRelation result = datasourceService.addLogicalRelation(1, lr);
		assertEquals(1, result.getDatasourceId());
		verify(logicalRelationMapper).insert(lr);
	}

	@Test
	void updateLogicalRelation_notFound_throwsException() {
		when(logicalRelationMapper.selectById(99)).thenReturn(null);
		LogicalRelation lr = new LogicalRelation();

		assertThrows(RuntimeException.class, () -> datasourceService.updateLogicalRelation(1, 99, lr));
	}

	@Test
	void updateLogicalRelation_wrongDatasource_throwsException() {
		LogicalRelation existing = new LogicalRelation();
		existing.setDatasourceId(2);
		when(logicalRelationMapper.selectById(1)).thenReturn(existing);

		assertThrows(RuntimeException.class,
				() -> datasourceService.updateLogicalRelation(1, 1, new LogicalRelation()));
	}

	@Test
	void deleteLogicalRelation_notFound_throwsException() {
		when(logicalRelationMapper.selectById(99)).thenReturn(null);

		assertThrows(RuntimeException.class, () -> datasourceService.deleteLogicalRelation(1, 99));
	}

	@Test
	void deleteLogicalRelation_wrongDatasource_throwsException() {
		LogicalRelation existing = new LogicalRelation();
		existing.setDatasourceId(2);
		when(logicalRelationMapper.selectById(1)).thenReturn(existing);

		assertThrows(RuntimeException.class, () -> datasourceService.deleteLogicalRelation(1, 1));
	}

	@Test
	void saveLogicalRelations_handlesInsertAndDelete() {
		LogicalRelation existing = new LogicalRelation();
		existing.setId(1);
		existing.setSourceTableName("a");
		existing.setTargetTableName("b");

		when(logicalRelationMapper.selectByDatasourceId(1)).thenReturn(List.of(existing), Collections.emptyList());

		LogicalRelation newRelation = new LogicalRelation();
		newRelation.setSourceTableName("c");
		newRelation.setSourceColumnName("d");
		newRelation.setTargetTableName("e");
		newRelation.setTargetColumnName("f");

		datasourceService.saveLogicalRelations(1, List.of(newRelation));

		verify(logicalRelationMapper).deleteById(1);
		verify(logicalRelationMapper).insert(newRelation);
	}

}
