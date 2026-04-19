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

import com.alibaba.cloud.ai.dataagent.dto.knowledge.businessknowledge.CreateBusinessKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.businessknowledge.UpdateBusinessKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.service.business.BusinessKnowledgeService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import com.alibaba.cloud.ai.dataagent.vo.BusinessKnowledgeVO;
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
class BusinessKnowledgeControllerTest {

	@Mock
	private BusinessKnowledgeService businessKnowledgeService;

	private BusinessKnowledgeController controller;

	@BeforeEach
	void setUp() {
		controller = new BusinessKnowledgeController(businessKnowledgeService);
	}

	@Test
	void list_withoutKeyword_returnsAllForAgent() {
		BusinessKnowledgeVO vo = BusinessKnowledgeVO.builder().id(1L).businessTerm("Revenue").build();
		when(businessKnowledgeService.getKnowledge(1L)).thenReturn(List.of(vo));

		ApiResponse<List<BusinessKnowledgeVO>> result = controller.list("1", null);

		assertTrue(result.isSuccess());
		assertEquals(1, result.getData().size());
		verify(businessKnowledgeService).getKnowledge(1L);
	}

	@Test
	void list_withKeyword_callsSearch() {
		BusinessKnowledgeVO vo = BusinessKnowledgeVO.builder().id(1L).businessTerm("Revenue").build();
		when(businessKnowledgeService.searchKnowledge(1L, "Rev")).thenReturn(List.of(vo));

		ApiResponse<List<BusinessKnowledgeVO>> result = controller.list("1", "Rev");

		assertTrue(result.isSuccess());
		assertEquals(1, result.getData().size());
		verify(businessKnowledgeService).searchKnowledge(1L, "Rev");
	}

	@Test
	void get_existing_returnsKnowledge() {
		BusinessKnowledgeVO vo = BusinessKnowledgeVO.builder().id(1L).businessTerm("Revenue").build();
		when(businessKnowledgeService.getKnowledgeById(1L)).thenReturn(vo);

		ApiResponse<BusinessKnowledgeVO> result = controller.get(1L);

		assertTrue(result.isSuccess());
		assertEquals("Revenue", result.getData().getBusinessTerm());
	}

	@Test
	void get_notFound_returnsError() {
		when(businessKnowledgeService.getKnowledgeById(999L)).thenReturn(null);

		ApiResponse<BusinessKnowledgeVO> result = controller.get(999L);

		assertFalse(result.isSuccess());
	}

	@Test
	void create_validInput_returnsCreated() {
		CreateBusinessKnowledgeDTO dto = CreateBusinessKnowledgeDTO.builder()
			.businessTerm("GMV")
			.description("Gross Merchandise Volume")
			.agentId(1L)
			.build();
		BusinessKnowledgeVO vo = BusinessKnowledgeVO.builder().id(1L).businessTerm("GMV").build();
		when(businessKnowledgeService.addKnowledge(dto)).thenReturn(vo);

		ApiResponse<BusinessKnowledgeVO> result = controller.create(dto);

		assertTrue(result.isSuccess());
		assertEquals("GMV", result.getData().getBusinessTerm());
	}

	@Test
	void update_validInput_returnsUpdated() {
		UpdateBusinessKnowledgeDTO dto = UpdateBusinessKnowledgeDTO.builder()
			.businessTerm("Updated GMV")
			.description("Updated description")
			.agentId(1L)
			.build();
		BusinessKnowledgeVO vo = BusinessKnowledgeVO.builder().id(1L).businessTerm("Updated GMV").build();
		when(businessKnowledgeService.updateKnowledge(1L, dto)).thenReturn(vo);

		ApiResponse<BusinessKnowledgeVO> result = controller.update(1L, dto);

		assertTrue(result.isSuccess());
		assertEquals("Updated GMV", result.getData().getBusinessTerm());
	}

	@Test
	void delete_existing_returnsSuccess() {
		BusinessKnowledgeVO vo = BusinessKnowledgeVO.builder().id(1L).build();
		when(businessKnowledgeService.getKnowledgeById(1L)).thenReturn(vo);

		ApiResponse<Boolean> result = controller.delete(1L);

		assertTrue(result.isSuccess());
		verify(businessKnowledgeService).deleteKnowledge(1L);
	}

	@Test
	void delete_notFound_returnsError() {
		when(businessKnowledgeService.getKnowledgeById(999L)).thenReturn(null);

		ApiResponse<Boolean> result = controller.delete(999L);

		assertFalse(result.isSuccess());
		verify(businessKnowledgeService, never()).deleteKnowledge(anyLong());
	}

	@Test
	void recallKnowledge_success_returnsSuccess() {
		ApiResponse<Boolean> result = controller.recallKnowledge(1L, true);

		assertTrue(result.isSuccess());
		verify(businessKnowledgeService).recallKnowledge(1L, true);
	}

	@Test
	void refreshVectorStore_validAgentId_returnsSuccess() throws Exception {
		ApiResponse<Boolean> result = controller.refreshAllKnowledgeToVectorStore("1");

		assertTrue(result.isSuccess());
		verify(businessKnowledgeService).refreshAllKnowledgeToVectorStore("1");
	}

	@Test
	void refreshVectorStore_emptyAgentId_returnsError() throws Exception {
		ApiResponse<Boolean> result = controller.refreshAllKnowledgeToVectorStore("");

		assertFalse(result.isSuccess());
	}

	@Test
	void refreshVectorStore_serviceThrows_returnsError() throws Exception {
		doThrow(new RuntimeException("vector error")).when(businessKnowledgeService)
			.refreshAllKnowledgeToVectorStore("1");

		ApiResponse<Boolean> result = controller.refreshAllKnowledgeToVectorStore("1");

		assertFalse(result.isSuccess());
	}

	@Test
	void retryEmbedding_success_returnsSuccess() {
		ApiResponse<Boolean> result = controller.retryEmbedding(1L);

		assertTrue(result.isSuccess());
		verify(businessKnowledgeService).retryEmbedding(1L);
	}

}
