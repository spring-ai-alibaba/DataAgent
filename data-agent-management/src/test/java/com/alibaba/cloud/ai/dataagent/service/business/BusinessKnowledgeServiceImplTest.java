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
package com.alibaba.cloud.ai.dataagent.service.business;

import com.alibaba.cloud.ai.dataagent.converter.BusinessKnowledgeConverter;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.businessknowledge.CreateBusinessKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.businessknowledge.UpdateBusinessKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.mapper.BusinessKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.vo.BusinessKnowledgeVO;
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
class BusinessKnowledgeServiceImplTest {

	@Mock
	private BusinessKnowledgeMapper businessKnowledgeMapper;

	@Mock
	private AgentVectorStoreService agentVectorStoreService;

	@Mock
	private BusinessKnowledgeConverter businessKnowledgeConverter;

	@InjectMocks
	private BusinessKnowledgeServiceImpl service;

	private BusinessKnowledge testKnowledge;

	private BusinessKnowledgeVO testVO;

	@BeforeEach
	void setUp() {
		testKnowledge = new BusinessKnowledge();
		testKnowledge.setId(1L);
		testKnowledge.setAgentId(100L);
		testKnowledge.setBusinessTerm("Revenue");
		testKnowledge.setDescription("Total revenue");
		testKnowledge.setIsRecall(1);
		testKnowledge.setIsDeleted(0);
		testKnowledge.setEmbeddingStatus(EmbeddingStatus.COMPLETED);

		testVO = new BusinessKnowledgeVO();
		testVO.setId(1L);
		testVO.setBusinessTerm("Revenue");
	}

	@Test
	void getKnowledge_withResults() {
		when(businessKnowledgeMapper.selectByAgentId(100L)).thenReturn(List.of(testKnowledge));
		when(businessKnowledgeConverter.toVo(testKnowledge)).thenReturn(testVO);

		List<BusinessKnowledgeVO> result = service.getKnowledge(100L);
		assertEquals(1, result.size());
		assertEquals("Revenue", result.get(0).getBusinessTerm());
	}

	@Test
	void getKnowledge_empty() {
		when(businessKnowledgeMapper.selectByAgentId(100L)).thenReturn(Collections.emptyList());
		List<BusinessKnowledgeVO> result = service.getKnowledge(100L);
		assertTrue(result.isEmpty());
	}

	@Test
	void getAllKnowledge_withResults() {
		when(businessKnowledgeMapper.selectAll()).thenReturn(List.of(testKnowledge));
		when(businessKnowledgeConverter.toVo(testKnowledge)).thenReturn(testVO);

		List<BusinessKnowledgeVO> result = service.getAllKnowledge();
		assertEquals(1, result.size());
	}

	@Test
	void getAllKnowledge_empty() {
		when(businessKnowledgeMapper.selectAll()).thenReturn(Collections.emptyList());
		List<BusinessKnowledgeVO> result = service.getAllKnowledge();
		assertTrue(result.isEmpty());
	}

	@Test
	void searchKnowledge_withResults() {
		when(businessKnowledgeMapper.searchInAgent(100L, "rev")).thenReturn(List.of(testKnowledge));
		when(businessKnowledgeConverter.toVo(testKnowledge)).thenReturn(testVO);

		List<BusinessKnowledgeVO> result = service.searchKnowledge(100L, "rev");
		assertEquals(1, result.size());
	}

	@Test
	void searchKnowledge_empty() {
		when(businessKnowledgeMapper.searchInAgent(100L, "xyz")).thenReturn(Collections.emptyList());
		List<BusinessKnowledgeVO> result = service.searchKnowledge(100L, "xyz");
		assertTrue(result.isEmpty());
	}

	@Test
	void getKnowledgeById_found() {
		when(businessKnowledgeMapper.selectById(1L)).thenReturn(testKnowledge);
		when(businessKnowledgeConverter.toVo(testKnowledge)).thenReturn(testVO);

		BusinessKnowledgeVO result = service.getKnowledgeById(1L);
		assertNotNull(result);
		assertEquals(1L, result.getId());
	}

	@Test
	void getKnowledgeById_notFound() {
		when(businessKnowledgeMapper.selectById(999L)).thenReturn(null);
		BusinessKnowledgeVO result = service.getKnowledgeById(999L);
		assertNull(result);
	}

	@Test
	void addKnowledge_success() {
		CreateBusinessKnowledgeDTO dto = new CreateBusinessKnowledgeDTO();
		BusinessKnowledge entity = new BusinessKnowledge();
		entity.setId(2L);
		entity.setAgentId(100L);

		when(businessKnowledgeConverter.toEntityForCreate(dto)).thenReturn(entity);
		when(businessKnowledgeMapper.insert(entity)).thenReturn(1);
		when(businessKnowledgeConverter.toVo(entity)).thenReturn(testVO);

		BusinessKnowledgeVO result = service.addKnowledge(dto);
		assertNotNull(result);
		verify(agentVectorStoreService).addDocuments(anyString(), anyList());
	}

	@Test
	void addKnowledge_insertFails() {
		CreateBusinessKnowledgeDTO dto = new CreateBusinessKnowledgeDTO();
		BusinessKnowledge entity = new BusinessKnowledge();
		entity.setAgentId(100L);

		when(businessKnowledgeConverter.toEntityForCreate(dto)).thenReturn(entity);
		when(businessKnowledgeMapper.insert(entity)).thenReturn(0);

		assertThrows(RuntimeException.class, () -> service.addKnowledge(dto));
	}

	@Test
	void addKnowledge_vectorStoreFails_setsFailedStatus() {
		CreateBusinessKnowledgeDTO dto = new CreateBusinessKnowledgeDTO();
		BusinessKnowledge entity = new BusinessKnowledge();
		entity.setId(2L);
		entity.setAgentId(100L);

		when(businessKnowledgeConverter.toEntityForCreate(dto)).thenReturn(entity);
		when(businessKnowledgeMapper.insert(entity)).thenReturn(1);
		doThrow(new RuntimeException("vector error")).when(agentVectorStoreService)
			.addDocuments(anyString(), anyList());
		when(businessKnowledgeConverter.toVo(entity)).thenReturn(testVO);

		BusinessKnowledgeVO result = service.addKnowledge(dto);
		assertNotNull(result);
		assertEquals(EmbeddingStatus.FAILED, entity.getEmbeddingStatus());
	}

	@Test
	void updateKnowledge_notFound_throws() {
		when(businessKnowledgeMapper.selectById(999L)).thenReturn(null);
		UpdateBusinessKnowledgeDTO dto = new UpdateBusinessKnowledgeDTO();
		assertThrows(RuntimeException.class, () -> service.updateKnowledge(999L, dto));
	}

	@Test
	void updateKnowledge_success() {
		when(businessKnowledgeMapper.selectById(1L)).thenReturn(testKnowledge);
		when(businessKnowledgeMapper.updateById(testKnowledge)).thenReturn(1);
		when(businessKnowledgeConverter.toVo(testKnowledge)).thenReturn(testVO);

		UpdateBusinessKnowledgeDTO dto = new UpdateBusinessKnowledgeDTO();
		dto.setBusinessTerm("Updated Term");
		dto.setDescription("Updated desc");
		dto.setSynonyms("syn1,syn2");

		BusinessKnowledgeVO result = service.updateKnowledge(1L, dto);
		assertNotNull(result);
		assertEquals("Updated Term", testKnowledge.getBusinessTerm());
	}

	@Test
	void updateKnowledge_dbUpdateFails_throws() {
		when(businessKnowledgeMapper.selectById(1L)).thenReturn(testKnowledge);
		when(businessKnowledgeMapper.updateById(testKnowledge)).thenReturn(0);

		UpdateBusinessKnowledgeDTO dto = new UpdateBusinessKnowledgeDTO();
		dto.setBusinessTerm("T");
		dto.setDescription("D");

		assertThrows(RuntimeException.class, () -> service.updateKnowledge(1L, dto));
	}

	@Test
	void deleteKnowledge_found() {
		when(businessKnowledgeMapper.selectById(1L)).thenReturn(testKnowledge);
		when(businessKnowledgeMapper.logicalDelete(1L, 1)).thenReturn(1);

		service.deleteKnowledge(1L);
		verify(agentVectorStoreService).deleteDocumentsByMetedata(anyString(), anyMap());
	}

	@Test
	void deleteKnowledge_notFound_noException() {
		when(businessKnowledgeMapper.selectById(999L)).thenReturn(null);
		service.deleteKnowledge(999L);
		verify(businessKnowledgeMapper, never()).logicalDelete(anyLong(), anyInt());
	}

	@Test
	void deleteKnowledge_logicalDeleteFails_throws() {
		when(businessKnowledgeMapper.selectById(1L)).thenReturn(testKnowledge);
		when(businessKnowledgeMapper.logicalDelete(1L, 1)).thenReturn(0);

		assertThrows(RuntimeException.class, () -> service.deleteKnowledge(1L));
	}

	@Test
	void recallKnowledge_enableRecall() {
		when(businessKnowledgeMapper.selectById(1L)).thenReturn(testKnowledge);

		service.recallKnowledge(1L, true);
		assertEquals(1, testKnowledge.getIsRecall());
		verify(businessKnowledgeMapper).updateById(testKnowledge);
	}

	@Test
	void recallKnowledge_disableRecall() {
		when(businessKnowledgeMapper.selectById(1L)).thenReturn(testKnowledge);

		service.recallKnowledge(1L, false);
		assertEquals(0, testKnowledge.getIsRecall());
	}

	@Test
	void recallKnowledge_notFound_throws() {
		when(businessKnowledgeMapper.selectById(999L)).thenReturn(null);
		assertThrows(RuntimeException.class, () -> service.recallKnowledge(999L, true));
	}

	@Test
	void retryEmbedding_notFound_throws() {
		when(businessKnowledgeMapper.selectById(999L)).thenReturn(null);
		assertThrows(RuntimeException.class, () -> service.retryEmbedding(999L));
	}

	@Test
	void retryEmbedding_processing_throws() {
		testKnowledge.setEmbeddingStatus(EmbeddingStatus.PROCESSING);
		when(businessKnowledgeMapper.selectById(1L)).thenReturn(testKnowledge);
		assertThrows(RuntimeException.class, () -> service.retryEmbedding(1L));
	}

	@Test
	void retryEmbedding_notRecalled_throws() {
		testKnowledge.setIsRecall(0);
		when(businessKnowledgeMapper.selectById(1L)).thenReturn(testKnowledge);
		assertThrows(RuntimeException.class, () -> service.retryEmbedding(1L));
	}

}
