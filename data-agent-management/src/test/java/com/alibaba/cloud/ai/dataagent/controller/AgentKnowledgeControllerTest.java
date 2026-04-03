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

import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.AgentKnowledgeQueryDTO;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.UpdateKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeService;
import com.alibaba.cloud.ai.dataagent.vo.AgentKnowledgeVO;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import com.alibaba.cloud.ai.dataagent.vo.PageResponse;
import com.alibaba.cloud.ai.dataagent.vo.PageResult;
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
class AgentKnowledgeControllerTest {

	@Mock
	private AgentKnowledgeService agentKnowledgeService;

	private AgentKnowledgeController controller;

	@BeforeEach
	void setUp() {
		controller = new AgentKnowledgeController(agentKnowledgeService);
	}

	@Test
	void getKnowledgeById_existing_returnsSuccess() {
		AgentKnowledgeVO vo = new AgentKnowledgeVO();
		vo.setId(1);
		vo.setTitle("Test Knowledge");
		when(agentKnowledgeService.getKnowledgeById(1)).thenReturn(vo);

		ApiResponse<AgentKnowledgeVO> result = controller.getKnowledgeById(1);

		assertTrue(result.isSuccess());
		assertEquals("Test Knowledge", result.getData().getTitle());
	}

	@Test
	void getKnowledgeById_notFound_returnsError() {
		when(agentKnowledgeService.getKnowledgeById(999)).thenReturn(null);

		ApiResponse<AgentKnowledgeVO> result = controller.getKnowledgeById(999);

		assertFalse(result.isSuccess());
	}

	@Test
	void getKnowledgeById_serviceThrows_returnsError() {
		when(agentKnowledgeService.getKnowledgeById(1)).thenThrow(new RuntimeException("db error"));

		ApiResponse<AgentKnowledgeVO> result = controller.getKnowledgeById(1);

		assertFalse(result.isSuccess());
	}

	@Test
	void updateKnowledge_success_returnsUpdated() {
		UpdateKnowledgeDTO dto = new UpdateKnowledgeDTO();
		dto.setTitle("Updated Title");
		dto.setContent("Updated Content");
		AgentKnowledgeVO vo = new AgentKnowledgeVO();
		vo.setId(1);
		vo.setTitle("Updated Title");
		when(agentKnowledgeService.updateKnowledge(1, dto)).thenReturn(vo);

		ApiResponse<AgentKnowledgeVO> result = controller.updateKnowledge(1, dto);

		assertTrue(result.isSuccess());
		assertEquals("Updated Title", result.getData().getTitle());
	}

	@Test
	void updateRecallStatus_success_returnsUpdated() {
		AgentKnowledgeVO vo = new AgentKnowledgeVO();
		vo.setId(1);
		vo.setIsRecall(true);
		when(agentKnowledgeService.updateKnowledgeRecallStatus(1, true)).thenReturn(vo);

		ApiResponse<AgentKnowledgeVO> result = controller.updateRecallStatus(1, true);

		assertTrue(result.isSuccess());
		assertTrue(result.getData().getIsRecall());
	}

	@Test
	void deleteKnowledge_success_returnsSuccess() {
		when(agentKnowledgeService.deleteKnowledge(1)).thenReturn(true);

		ApiResponse<Boolean> result = controller.deleteKnowledge(1);

		assertTrue(result.isSuccess());
	}

	@Test
	void deleteKnowledge_failure_returnsError() {
		when(agentKnowledgeService.deleteKnowledge(1)).thenReturn(false);

		ApiResponse<Boolean> result = controller.deleteKnowledge(1);

		assertFalse(result.isSuccess());
	}

	@Test
	void queryByPage_success_returnsPagedResults() {
		AgentKnowledgeQueryDTO queryDTO = new AgentKnowledgeQueryDTO();
		queryDTO.setAgentId(1);
		queryDTO.setPageNum(1);
		queryDTO.setPageSize(10);
		PageResult<AgentKnowledgeVO> pageResult = new PageResult<>();
		pageResult.setData(List.of(new AgentKnowledgeVO()));
		pageResult.setTotal(1L);
		pageResult.setPageNum(1);
		pageResult.setPageSize(10);
		pageResult.setTotalPages(1);
		when(agentKnowledgeService.queryByConditionsWithPage(queryDTO)).thenReturn(pageResult);

		PageResponse<List<AgentKnowledgeVO>> result = controller.queryByPage(queryDTO);

		assertTrue(result.isSuccess());
		assertEquals(1, result.getData().size());
		assertEquals(1L, result.getTotal());
	}

	@Test
	void queryByPage_serviceThrows_returnsError() {
		AgentKnowledgeQueryDTO queryDTO = new AgentKnowledgeQueryDTO();
		queryDTO.setAgentId(1);
		when(agentKnowledgeService.queryByConditionsWithPage(queryDTO)).thenThrow(new RuntimeException("db error"));

		PageResponse<List<AgentKnowledgeVO>> result = controller.queryByPage(queryDTO);

		assertFalse(result.isSuccess());
	}

	@Test
	void retryEmbedding_success_returnsSuccess() {
		ApiResponse<AgentKnowledgeVO> result = controller.retryEmbedding(1);

		assertTrue(result.isSuccess());
		verify(agentKnowledgeService).retryEmbedding(1);
	}

}
