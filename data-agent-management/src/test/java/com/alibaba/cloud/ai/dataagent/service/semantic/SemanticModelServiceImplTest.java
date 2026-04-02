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
package com.alibaba.cloud.ai.dataagent.service.semantic;

import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelAddDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelBatchImportDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelImportItem;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticModelMapper;
import com.alibaba.cloud.ai.dataagent.vo.BatchImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SemanticModelServiceImplTest {

	@Mock
	private SemanticModelMapper semanticModelMapper;

	@Mock
	private AgentDatasourceMapper agentDatasourceMapper;

	@Mock
	private SemanticModelExcelService excelService;

	@InjectMocks
	private SemanticModelServiceImpl service;

	private AgentDatasource activeDs;

	@BeforeEach
	void setUp() {
		activeDs = new AgentDatasource();
		activeDs.setDatasourceId(10);
		activeDs.setIsActive(1);
	}

	@Test
	void getAll_delegatesToMapper() {
		SemanticModel model = SemanticModel.builder().id(1L).build();
		when(semanticModelMapper.selectAll()).thenReturn(List.of(model));

		List<SemanticModel> result = service.getAll();
		assertEquals(1, result.size());
		verify(semanticModelMapper).selectAll();
	}

	@Test
	void getEnabledByAgentId_delegatesToMapper() {
		when(semanticModelMapper.selectEnabledByAgentId(1L)).thenReturn(List.of());

		List<SemanticModel> result = service.getEnabledByAgentId(1L);
		assertTrue(result.isEmpty());
	}

	@Test
	void getById_delegatesToMapper() {
		SemanticModel model = SemanticModel.builder().id(1L).build();
		when(semanticModelMapper.selectById(1L)).thenReturn(model);

		SemanticModel result = service.getById(1L);
		assertEquals(1L, result.getId());
	}

	@Test
	void addSemanticModel_entity_delegatesToMapper() {
		SemanticModel model = SemanticModel.builder().build();
		service.addSemanticModel(model);
		verify(semanticModelMapper).insert(model);
	}

	@Test
	void addSemanticModel_dto_createsAndInserts() {
		when(agentDatasourceMapper.selectByAgentId(1L)).thenReturn(List.of(activeDs));

		SemanticModelAddDTO dto = new SemanticModelAddDTO();
		dto.setAgentId(1L);
		dto.setTableName("users");
		dto.setColumnName("name");
		dto.setBusinessName("User Name");

		boolean result = service.addSemanticModel(dto);
		assertTrue(result);
		verify(semanticModelMapper).insert(any(SemanticModel.class));
	}

	@Test
	void addSemanticModel_dto_noDatasource_throws() {
		when(agentDatasourceMapper.selectByAgentId(1L)).thenReturn(Collections.emptyList());

		SemanticModelAddDTO dto = new SemanticModelAddDTO();
		dto.setAgentId(1L);

		assertThrows(RuntimeException.class, () -> service.addSemanticModel(dto));
	}

	@Test
	void enableSemanticModel_delegatesToMapper() {
		service.enableSemanticModel(1L);
		verify(semanticModelMapper).enableById(1L);
	}

	@Test
	void disableSemanticModel_delegatesToMapper() {
		service.disableSemanticModel(1L);
		verify(semanticModelMapper).disableById(1L);
	}

	@Test
	void getByAgentId_delegatesToMapper() {
		when(semanticModelMapper.selectByAgentId(1L)).thenReturn(List.of());
		List<SemanticModel> result = service.getByAgentId(1L);
		assertTrue(result.isEmpty());
	}

	@Test
	void search_delegatesToMapper() {
		when(semanticModelMapper.searchByKeyword("test")).thenReturn(List.of());
		List<SemanticModel> result = service.search("test");
		assertTrue(result.isEmpty());
	}

	@Test
	void deleteSemanticModel_delegatesToMapper() {
		service.deleteSemanticModel(1L);
		verify(semanticModelMapper).deleteById(1L);
	}

	@Test
	void updateSemanticModel_setsIdAndDelegates() {
		SemanticModel model = SemanticModel.builder().build();
		service.updateSemanticModel(5L, model);
		assertEquals(5L, model.getId());
		verify(semanticModelMapper).updateById(model);
	}

	@Test
	void getByAgentIdAndTableNames_noDatasource_returnsEmpty() {
		when(agentDatasourceMapper.selectByAgentId(1L)).thenReturn(Collections.emptyList());

		assertThrows(RuntimeException.class, () -> service.getByAgentIdAndTableNames(1L, List.of("users")));
	}

	@Test
	void getByAgentIdAndTableNames_nullTableNames_returnsEmpty() {
		when(agentDatasourceMapper.selectByAgentId(1L)).thenReturn(List.of(activeDs));
		List<SemanticModel> result = service.getByAgentIdAndTableNames(1L, null);
		assertTrue(result.isEmpty());
	}

	@Test
	void getByAgentIdAndTableNames_emptyTableNames_returnsEmpty() {
		when(agentDatasourceMapper.selectByAgentId(1L)).thenReturn(List.of(activeDs));
		List<SemanticModel> result = service.getByAgentIdAndTableNames(1L, Collections.emptyList());
		assertTrue(result.isEmpty());
	}

	@Test
	void getByAgentIdAndTableNames_withValidData() {
		when(agentDatasourceMapper.selectByAgentId(1L)).thenReturn(List.of(activeDs));
		when(semanticModelMapper.selectByDatasourceIdAndTableNames(eq(10), anyList())).thenReturn(List.of());

		List<SemanticModel> result = service.getByAgentIdAndTableNames(1L, List.of("users"));
		assertNotNull(result);
	}

	@Test
	void findDatasourceId_prefersActiveDs() {
		AgentDatasource inactiveDs = new AgentDatasource();
		inactiveDs.setDatasourceId(20);
		inactiveDs.setIsActive(0);

		when(agentDatasourceMapper.selectByAgentId(1L)).thenReturn(List.of(inactiveDs, activeDs));

		SemanticModelAddDTO dto = new SemanticModelAddDTO();
		dto.setAgentId(1L);
		dto.setTableName("t");
		dto.setColumnName("c");

		service.addSemanticModel(dto);
		verify(semanticModelMapper).insert(argThat(m -> m.getDatasourceId().equals(10)));
	}

	@Test
	void findDatasourceId_fallsBackToFirst() {
		AgentDatasource inactiveDs = new AgentDatasource();
		inactiveDs.setDatasourceId(20);
		inactiveDs.setIsActive(0);

		when(agentDatasourceMapper.selectByAgentId(1L)).thenReturn(List.of(inactiveDs));

		SemanticModelAddDTO dto = new SemanticModelAddDTO();
		dto.setAgentId(1L);
		dto.setTableName("t");
		dto.setColumnName("c");

		service.addSemanticModel(dto);
		verify(semanticModelMapper).insert(argThat(m -> m.getDatasourceId().equals(20)));
	}

	@Test
	void batchImport_successfulInsert() {
		when(agentDatasourceMapper.selectByAgentId(1L)).thenReturn(List.of(activeDs));
		when(semanticModelMapper.selectByAgentIdAndTableNameAndColumnName(anyInt(), anyString(), anyString()))
			.thenReturn(null);

		SemanticModelImportItem item = new SemanticModelImportItem();
		item.setTableName("users");
		item.setColumnName("name");
		item.setBusinessName("Name");

		SemanticModelBatchImportDTO dto = SemanticModelBatchImportDTO.builder()
			.agentId(1L)
			.items(List.of(item))
			.build();

		BatchImportResult result = service.batchImport(dto);
		assertEquals(1, result.getTotal());
		assertEquals(1, result.getSuccessCount());
		assertEquals(0, result.getFailCount());
	}

	@Test
	void batchImport_updatesExisting() {
		when(agentDatasourceMapper.selectByAgentId(1L)).thenReturn(List.of(activeDs));
		SemanticModel existing = SemanticModel.builder().id(1L).build();
		when(semanticModelMapper.selectByAgentIdAndTableNameAndColumnName(anyInt(), anyString(), anyString()))
			.thenReturn(existing);

		SemanticModelImportItem item = new SemanticModelImportItem();
		item.setTableName("users");
		item.setColumnName("name");
		item.setBusinessName("Updated Name");

		SemanticModelBatchImportDTO dto = SemanticModelBatchImportDTO.builder()
			.agentId(1L)
			.items(List.of(item))
			.build();

		BatchImportResult result = service.batchImport(dto);
		assertEquals(1, result.getSuccessCount());
		verify(semanticModelMapper).updateById(existing);
	}

	@Test
	void batchImport_datasourceLookupFails() {
		when(agentDatasourceMapper.selectByAgentId(1L)).thenReturn(Collections.emptyList());

		SemanticModelImportItem item = new SemanticModelImportItem();
		item.setTableName("t");
		item.setColumnName("c");

		SemanticModelBatchImportDTO dto = SemanticModelBatchImportDTO.builder()
			.agentId(1L)
			.items(List.of(item))
			.build();

		BatchImportResult result = service.batchImport(dto);
		assertEquals(1, result.getFailCount());
	}

}
