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

import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelAddDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelBatchImportDTO;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticModelService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import com.alibaba.cloud.ai.dataagent.vo.BatchImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SemanticModelControllerTest {

	@Mock
	private SemanticModelService semanticModelService;

	private SemanticModelController controller;

	@BeforeEach
	void setUp() {
		controller = new SemanticModelController(semanticModelService);
	}

	@Test
	void list_withKeyword_callsSearch() {
		SemanticModel model = SemanticModel.builder().id(1L).businessName("Revenue").build();
		when(semanticModelService.search("Rev")).thenReturn(List.of(model));

		ApiResponse<List<SemanticModel>> result = controller.list("Rev", null);

		assertTrue(result.isSuccess());
		assertEquals(1, result.getData().size());
	}

	@Test
	void list_withAgentId_callsGetByAgentId() {
		SemanticModel model = SemanticModel.builder().id(1L).agentId(1L).build();
		when(semanticModelService.getByAgentId(1L)).thenReturn(List.of(model));

		ApiResponse<List<SemanticModel>> result = controller.list(null, 1L);

		assertTrue(result.isSuccess());
		assertEquals(1, result.getData().size());
	}

	@Test
	void list_noParams_callsGetAll() {
		when(semanticModelService.getAll()).thenReturn(List.of());

		ApiResponse<List<SemanticModel>> result = controller.list(null, null);

		assertTrue(result.isSuccess());
		verify(semanticModelService).getAll();
	}

	@Test
	void list_keywordPrioritizedOverAgentId() {
		when(semanticModelService.search("test")).thenReturn(List.of());

		controller.list("test", 1L);

		verify(semanticModelService).search("test");
		verify(semanticModelService, never()).getByAgentId(anyLong());
	}

	@Test
	void get_existing_returnsModel() {
		SemanticModel model = SemanticModel.builder().id(1L).businessName("Revenue").build();
		when(semanticModelService.getById(1L)).thenReturn(model);

		ApiResponse<SemanticModel> result = controller.get(1L);

		assertTrue(result.isSuccess());
		assertEquals("Revenue", result.getData().getBusinessName());
	}

	@Test
	void create_success_returnsSuccess() {
		SemanticModelAddDTO dto = SemanticModelAddDTO.builder()
			.agentId(1L)
			.tableName("orders")
			.columnName("amount")
			.businessName("Order Amount")
			.dataType("decimal")
			.build();
		when(semanticModelService.addSemanticModel(dto)).thenReturn(true);

		ApiResponse<Boolean> result = controller.create(dto);

		assertTrue(result.isSuccess());
		assertTrue(result.getData());
	}

	@Test
	void create_failure_returnsError() {
		SemanticModelAddDTO dto = SemanticModelAddDTO.builder()
			.agentId(1L)
			.tableName("orders")
			.columnName("amount")
			.businessName("Order Amount")
			.dataType("decimal")
			.build();
		when(semanticModelService.addSemanticModel(dto)).thenReturn(false);

		ApiResponse<Boolean> result = controller.create(dto);

		assertFalse(result.isSuccess());
	}

	@Test
	void update_existing_returnsUpdated() {
		SemanticModel existing = SemanticModel.builder().id(1L).businessName("Old Name").build();
		SemanticModel updated = SemanticModel.builder().businessName("New Name").build();
		when(semanticModelService.getById(1L)).thenReturn(existing);

		ApiResponse<SemanticModel> result = controller.update(1L, updated);

		assertTrue(result.isSuccess());
		assertEquals(1L, result.getData().getId());
		verify(semanticModelService).updateSemanticModel(1L, updated);
	}

	@Test
	void update_notFound_returnsError() {
		when(semanticModelService.getById(999L)).thenReturn(null);

		ApiResponse<SemanticModel> result = controller.update(999L, new SemanticModel());

		assertFalse(result.isSuccess());
	}

	@Test
	void delete_existing_returnsSuccess() {
		SemanticModel existing = SemanticModel.builder().id(1L).build();
		when(semanticModelService.getById(1L)).thenReturn(existing);

		ApiResponse<Boolean> result = controller.delete(1L);

		assertTrue(result.isSuccess());
		verify(semanticModelService).deleteSemanticModel(1L);
	}

	@Test
	void delete_notFound_returnsError() {
		when(semanticModelService.getById(999L)).thenReturn(null);

		ApiResponse<Boolean> result = controller.delete(999L);

		assertFalse(result.isSuccess());
	}

	@Test
	void batchDelete_success_returnsSuccess() {
		List<Long> ids = List.of(1L, 2L, 3L);

		ApiResponse<Boolean> result = controller.batchDelete(ids);

		assertTrue(result.isSuccess());
		verify(semanticModelService).deleteSemanticModels(ids);
	}

	@Test
	void enableFields_success_returnsSuccess() {
		List<Long> ids = List.of(1L, 2L);

		ApiResponse<Boolean> result = controller.enableFields(ids);

		assertTrue(result.isSuccess());
		verify(semanticModelService).enableSemanticModels(ids);
	}

	@Test
	void disableFields_success_returnsSuccess() {
		List<Long> ids = List.of(1L, 2L);

		ApiResponse<Boolean> result = controller.disableFields(ids);

		assertTrue(result.isSuccess());
		verify(semanticModelService, times(2)).disableSemanticModel(anyLong());
	}

	@Test
	void batchImport_success_returnsResult() {
		SemanticModelBatchImportDTO dto = SemanticModelBatchImportDTO.builder().agentId(1L).items(List.of()).build();
		BatchImportResult importResult = BatchImportResult.builder().total(5).successCount(4).failCount(1).build();
		when(semanticModelService.batchImport(dto)).thenReturn(importResult);

		ApiResponse<BatchImportResult> result = controller.batchImport(dto);

		assertTrue(result.isSuccess());
		assertEquals(5, result.getData().getTotal());
		assertEquals(4, result.getData().getSuccessCount());
	}

	@Test
	void downloadTemplate_templateExists_returnsBytes() {
		ResponseEntity<byte[]> result = controller.downloadTemplate();

		assertNotNull(result);
	}

}
