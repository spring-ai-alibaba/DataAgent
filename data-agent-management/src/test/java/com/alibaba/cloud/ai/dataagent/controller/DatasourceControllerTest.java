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

import com.alibaba.cloud.ai.dataagent.dto.datasource.DatasourceTypeDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.CreateLogicalRelationDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.UpdateLogicalRelationDTO;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.exception.InternalServerException;
import com.alibaba.cloud.ai.dataagent.exception.InvalidInputException;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
	void createDatasource_serviceThrows_throwsResponseStatusException() {
		Datasource input = Datasource.builder().name("bad-db").type("MYSQL").build();
		when(datasourceService.createDatasource(any())).thenThrow(new RuntimeException("db error"));

		assertThrows(ResponseStatusException.class, () -> datasourceController.createDatasource(input));
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
	void testConnection_serviceThrows_throwsInternalServerException() {
		when(datasourceService.testConnection(1)).thenThrow(new RuntimeException("conn error"));

		assertThrows(InternalServerException.class, () -> datasourceController.testConnection(1));
	}

	@Test
	void getTableColumns_validTable_returnsColumns() throws Exception {
		when(datasourceService.getDatasourceById(1)).thenReturn(Datasource.builder().id(1).build());
		when(datasourceService.getTableColumns(1, "users")).thenReturn(List.of("id", "name", "email"));

		ApiResponse<List<String>> result = datasourceController.getTableColumns(1, "users");

		assertTrue(result.isSuccess());
		assertEquals(3, result.getData().size());
		assertTrue(result.getData().contains("id"));
	}

	@Test
	void getTableColumns_serviceThrows_throwsInternalServerException() throws Exception {
		when(datasourceService.getTableColumns(1, "bad_table")).thenThrow(new RuntimeException("no columns"));

		assertThrows(InternalServerException.class, () -> datasourceController.getTableColumns(1, "bad_table"));
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
	void getLogicalRelations_serviceThrows_throwsInternalServerException() {
		when(datasourceService.getLogicalRelations(1)).thenThrow(new RuntimeException("db error"));

		assertThrows(InternalServerException.class, () -> datasourceController.getLogicalRelations(1));
	}

	@Test
	void addLogicalRelation_success_returnsCreated() {
		CreateLogicalRelationDTO dto = new CreateLogicalRelationDTO();
		dto.setSourceTableName("t_order");
		dto.setSourceColumnName("user_id");
		dto.setTargetTableName("t_user");
		dto.setTargetColumnName("id");
		dto.setRelationType("N:1");
		dto.setDescription("order to user");

		LogicalRelation created = LogicalRelation.builder()
			.id(1)
			.datasourceId(1)
			.sourceTableName("t_order")
			.sourceColumnName("user_id")
			.targetTableName("t_user")
			.targetColumnName("id")
			.relationType("N:1")
			.build();
		when(datasourceService.addLogicalRelation(eq(1), any(LogicalRelation.class))).thenReturn(created);

		ApiResponse<LogicalRelation> result = datasourceController.addLogicalRelation(1, dto);

		assertTrue(result.isSuccess());
		assertEquals("t_order", result.getData().getSourceTableName());
	}

	@Test
	void addLogicalRelation_serviceThrows_throwsInternalServerException() {
		CreateLogicalRelationDTO dto = new CreateLogicalRelationDTO();
		dto.setSourceTableName("a");
		dto.setSourceColumnName("b");
		dto.setTargetTableName("c");
		dto.setTargetColumnName("d");

		when(datasourceService.addLogicalRelation(eq(1), any())).thenThrow(new RuntimeException("duplicate"));

		assertThrows(InternalServerException.class, () -> datasourceController.addLogicalRelation(1, dto));
	}

	@Test
	void updateLogicalRelation_success_returnsUpdated() {
		UpdateLogicalRelationDTO dto = new UpdateLogicalRelationDTO();
		dto.setSourceTableName("t_order");
		dto.setSourceColumnName("user_id");
		dto.setTargetTableName("t_user");
		dto.setTargetColumnName("id");
		dto.setRelationType("1:1");
		dto.setDescription("updated");

		LogicalRelation updated = LogicalRelation.builder()
			.id(5)
			.datasourceId(1)
			.sourceTableName("t_order")
			.sourceColumnName("user_id")
			.targetTableName("t_user")
			.targetColumnName("id")
			.relationType("1:1")
			.build();
		when(datasourceService.updateLogicalRelation(eq(1), eq(5), any(LogicalRelation.class))).thenReturn(updated);

		ApiResponse<LogicalRelation> result = datasourceController.updateLogicalRelation(1, 5, dto);

		assertTrue(result.isSuccess());
		assertEquals("1:1", result.getData().getRelationType());
	}

	@Test
	void updateLogicalRelation_serviceThrows_throwsInternalServerException() {
		UpdateLogicalRelationDTO dto = new UpdateLogicalRelationDTO();
		dto.setSourceTableName("a");
		when(datasourceService.updateLogicalRelation(eq(1), eq(5), any())).thenThrow(new RuntimeException("not found"));

		assertThrows(InternalServerException.class, () -> datasourceController.updateLogicalRelation(1, 5, dto));
	}

	@Test
	void deleteLogicalRelation_success_returnsSuccess() {
		doNothing().when(datasourceService).deleteLogicalRelation(1, 5);

		ApiResponse<Void> result = datasourceController.deleteLogicalRelation(1, 5);

		assertTrue(result.isSuccess());
		verify(datasourceService).deleteLogicalRelation(1, 5);
	}

	@Test
	void deleteLogicalRelation_serviceThrows_throwsInternalServerException() {
		doThrow(new RuntimeException("delete error")).when(datasourceService).deleteLogicalRelation(1, 5);

		assertThrows(InternalServerException.class, () -> datasourceController.deleteLogicalRelation(1, 5));
	}

	@Test
	void saveLogicalRelations_success_returnsSaved() {
		LogicalRelation lr = LogicalRelation.builder()
			.sourceTableName("a")
			.sourceColumnName("b")
			.targetTableName("c")
			.targetColumnName("d")
			.build();
		List<LogicalRelation> saved = List.of(LogicalRelation.builder().id(1).datasourceId(1).build());
		when(datasourceService.saveLogicalRelations(eq(1), anyList())).thenReturn(saved);

		ApiResponse<List<LogicalRelation>> result = datasourceController.saveLogicalRelations(1, List.of(lr));

		assertTrue(result.isSuccess());
		assertEquals(1, result.getData().size());
	}

	@Test
	void saveLogicalRelations_serviceThrows_throwsInternalServerException() {
		when(datasourceService.saveLogicalRelations(eq(1), anyList())).thenThrow(new RuntimeException("save error"));

		assertThrows(InternalServerException.class,
				() -> datasourceController.saveLogicalRelations(1, Collections.emptyList()));
	}

	@Test
	void getDatasourceById_nonExisting_throwsNotFoundException() {
		when(datasourceService.getDatasourceById(999)).thenReturn(null);

		assertThrows(ResponseStatusException.class, () -> datasourceController.getDatasourceById(999));
	}

	@Test
	void getDatasourceById_existing_returnsDatasource() {
		Datasource ds = Datasource.builder().id(1).name("test").build();
		when(datasourceService.getDatasourceById(1)).thenReturn(ds);

		Datasource result = datasourceController.getDatasourceById(1);

		assertEquals("test", result.getName());
	}

	@Test
	void getAllDatasource_noParams_returnsAll() {
		when(datasourceService.getAllDatasource()).thenReturn(List.of(Datasource.builder().id(1).build()));

		List<Datasource> result = datasourceController.getAllDatasource(null, null);

		assertEquals(1, result.size());
	}

	@Test
	void getAllDatasource_withStatus_returnsByStatus() {
		when(datasourceService.getDatasourceByStatus("active"))
			.thenReturn(List.of(Datasource.builder().id(1).build()));

		List<Datasource> result = datasourceController.getAllDatasource("active", null);

		assertEquals(1, result.size());
		verify(datasourceService).getDatasourceByStatus("active");
	}

	@Test
	void getAllDatasource_withType_returnsByType() {
		when(datasourceService.getDatasourceByType("mysql")).thenReturn(List.of(Datasource.builder().id(1).build()));

		List<Datasource> result = datasourceController.getAllDatasource(null, "mysql");

		assertEquals(1, result.size());
		verify(datasourceService).getDatasourceByType("mysql");
	}

	@Test
	void getDatasourceTypes_returnsStandardTypes() {
		ApiResponse<List<DatasourceTypeDTO>> result = datasourceController.getDatasourceTypes();

		assertTrue(result.isSuccess());
		assertNotNull(result.getData());
		assertFalse(result.getData().isEmpty());
	}

	@Test
	void getDatasourceTables_nonExisting_throwsNotFound() {
		when(datasourceService.getDatasourceById(999)).thenReturn(null);

		assertThrows(ResponseStatusException.class, () -> datasourceController.getDatasourceTables(999));
	}

	@Test
	void getDatasourceTables_serviceThrows_throwsResponseStatusException() throws Exception {
		when(datasourceService.getDatasourceById(1)).thenReturn(Datasource.builder().id(1).build());
		when(datasourceService.getDatasourceTables(1)).thenThrow(new RuntimeException("conn error"));

		assertThrows(ResponseStatusException.class, () -> datasourceController.getDatasourceTables(1));
	}

	@Test
	void updateDatasource_nonExisting_throwsNotFound() {
		when(datasourceService.getDatasourceById(999)).thenReturn(null);

		assertThrows(ResponseStatusException.class,
				() -> datasourceController.updateDatasource(999, Datasource.builder().build()));
	}

	@Test
	void updateDatasource_serviceThrows_throwsResponseStatusException() {
		when(datasourceService.getDatasourceById(1)).thenReturn(Datasource.builder().id(1).build());
		when(datasourceService.updateDatasource(eq(1), any())).thenThrow(new RuntimeException("update error"));

		assertThrows(ResponseStatusException.class,
				() -> datasourceController.updateDatasource(1, Datasource.builder().build()));
	}

	@Test
	void deleteDatasource_success_returnsSuccess() {
		when(datasourceService.getDatasourceById(1)).thenReturn(Datasource.builder().id(1).build());
		doNothing().when(datasourceService).deleteDatasource(1);

		ApiResponse result = datasourceController.deleteDatasource(1);

		assertTrue(result.isSuccess());
	}

	@Test
	void deleteDatasource_nonExisting_throwsInvalidInputException() {
		when(datasourceService.getDatasourceById(999)).thenReturn(null);

		assertThrows(InvalidInputException.class, () -> datasourceController.deleteDatasource(999));
	}

	@Test
	void deleteDatasource_serviceThrows_throwsInternalServerException() {
		when(datasourceService.getDatasourceById(1)).thenReturn(Datasource.builder().id(1).build());
		doThrow(new RuntimeException("delete error")).when(datasourceService).deleteDatasource(1);

		assertThrows(InternalServerException.class, () -> datasourceController.deleteDatasource(1));
	}

}
