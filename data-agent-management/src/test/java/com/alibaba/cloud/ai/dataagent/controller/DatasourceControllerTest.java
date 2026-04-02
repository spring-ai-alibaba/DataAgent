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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatasourceControllerTest {

	@Mock
	private DatasourceService datasourceService;

	private DatasourceController datasourceController;

	@BeforeEach
	void setUp() {
		datasourceController = new DatasourceController(datasourceService);
	}

	@Test
	void createDatasource_validRequest_returnsCreated() {
		Datasource input = Datasource.builder().name("test-db").type("MYSQL").host("localhost").port(3306).build();
		Datasource saved = Datasource.builder()
			.id(1)
			.name("test-db")
			.type("MYSQL")
			.host("localhost")
			.port(3306)
			.build();
		when(datasourceService.createDatasource(any(Datasource.class))).thenReturn(saved);

		Datasource result = datasourceController.createDatasource(input);

		assertNotNull(result);
		assertEquals(1, result.getId());
		assertEquals("test-db", result.getName());
	}

	@Test
	void testConnection_validDatasource_returnsSuccess() {
		when(datasourceService.testConnection(1)).thenReturn(true);

		ApiResponse result = datasourceController.testConnection(1);

		assertTrue(result.isSuccess());
	}

	@Test
	void testConnection_failedConnection_returnsError() {
		when(datasourceService.testConnection(1)).thenReturn(false);

		ApiResponse result = datasourceController.testConnection(1);

		assertFalse(result.isSuccess());
	}

	@Test
	void getTableColumns_validTable_returnsColumns() throws Exception {
		when(datasourceService.getTableColumns(1, "users")).thenReturn(List.of("id", "name", "email"));

		ApiResponse<List<String>> result = datasourceController.getTableColumns(1, "users");

		assertTrue(result.isSuccess());
		assertEquals(3, result.getData().size());
		assertTrue(result.getData().contains("id"));
	}

	@Test
	void getLogicalRelations_validDatasource_returnsRelations() {
		LogicalRelation relation = LogicalRelation.builder()
			.id(1)
			.datasourceId(1)
			.sourceTableName("t_order")
			.sourceColumnName("buyer_uid")
			.targetTableName("t_user")
			.targetColumnName("id")
			.relationType("N:1")
			.build();
		when(datasourceService.getLogicalRelations(1)).thenReturn(List.of(relation));

		ApiResponse<List<LogicalRelation>> result = datasourceController.getLogicalRelations(1);

		assertTrue(result.isSuccess());
		assertEquals(1, result.getData().size());
		assertEquals("t_order", result.getData().get(0).getSourceTableName());
	}

	@Test
	void getDatasourceById_nonExisting_throwsNotFoundException() {
		when(datasourceService.getDatasourceById(999)).thenReturn(null);

		assertThrows(ResponseStatusException.class, () -> datasourceController.getDatasourceById(999));
	}

}
