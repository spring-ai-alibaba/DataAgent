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
package com.alibaba.cloud.ai.dataagent.service.knowledge;

import com.alibaba.cloud.ai.dataagent.converter.AgentKnowledgeConverter;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.AgentKnowledgeQueryDTO;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.CreateKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.UpdateKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;

import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.vo.AgentKnowledgeVO;
import com.alibaba.cloud.ai.dataagent.vo.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentKnowledgeServiceImplTest {

	private AgentKnowledgeServiceImpl service;

	@Mock
	private AgentKnowledgeMapper agentKnowledgeMapper;

	@Mock
	private FileStorageService fileStorageService;

	@Mock
	private AgentKnowledgeConverter agentKnowledgeConverter;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@BeforeEach
	void setUp() {
		service = new AgentKnowledgeServiceImpl(agentKnowledgeMapper, fileStorageService, agentKnowledgeConverter,
				eventPublisher);
	}

	@Test
	void getKnowledgeById_found_returnsVO() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		AgentKnowledgeVO vo = new AgentKnowledgeVO();
		vo.setId(1);

		when(agentKnowledgeMapper.selectById(1)).thenReturn(knowledge);
		when(agentKnowledgeConverter.toVo(knowledge)).thenReturn(vo);

		AgentKnowledgeVO result = service.getKnowledgeById(1);
		assertNotNull(result);
		assertEquals(1, result.getId());
	}

	@Test
	void getKnowledgeById_notFound_returnsNull() {
		when(agentKnowledgeMapper.selectById(99)).thenReturn(null);

		assertNull(service.getKnowledgeById(99));
	}

	@Test
	void createKnowledge_qaType_success() {
		CreateKnowledgeDTO dto = new CreateKnowledgeDTO();
		dto.setAgentId(1);
		dto.setTitle("Test QA");
		dto.setType("QA");
		dto.setQuestion("What is this?");
		dto.setContent("This is a test");

		AgentKnowledge entity = new AgentKnowledge();
		entity.setId(1);
		entity.setSplitterType("token");

		AgentKnowledgeVO vo = new AgentKnowledgeVO();
		vo.setId(1);

		when(agentKnowledgeConverter.toEntityForCreate(eq(dto), isNull())).thenReturn(entity);
		when(agentKnowledgeMapper.insert(entity)).thenReturn(1);
		when(agentKnowledgeConverter.toVo(entity)).thenReturn(vo);

		AgentKnowledgeVO result = service.createKnowledge(dto);
		assertNotNull(result);
		verify(eventPublisher).publishEvent(any());
	}

	@Test
	void createKnowledge_documentType_noFile_throwsException() {
		CreateKnowledgeDTO dto = new CreateKnowledgeDTO();
		dto.setType("DOCUMENT");
		dto.setFile(null);

		assertThrows(RuntimeException.class, () -> service.createKnowledge(dto));
	}

	@Test
	void createKnowledge_qaType_noQuestion_throwsException() {
		CreateKnowledgeDTO dto = new CreateKnowledgeDTO();
		dto.setType("QA");
		dto.setQuestion(null);

		assertThrows(RuntimeException.class, () -> service.createKnowledge(dto));
	}

	@Test
	void createKnowledge_qaType_noContent_throwsException() {
		CreateKnowledgeDTO dto = new CreateKnowledgeDTO();
		dto.setType("QA");
		dto.setQuestion("question");
		dto.setContent(null);

		assertThrows(RuntimeException.class, () -> service.createKnowledge(dto));
	}

	@Test
	void createKnowledge_faqType_noQuestion_throwsException() {
		CreateKnowledgeDTO dto = new CreateKnowledgeDTO();
		dto.setType("FAQ");
		dto.setQuestion(null);

		assertThrows(RuntimeException.class, () -> service.createKnowledge(dto));
	}

	@Test
	void createKnowledge_insertFails_throwsException() {
		CreateKnowledgeDTO dto = new CreateKnowledgeDTO();
		dto.setType("QA");
		dto.setQuestion("q");
		dto.setContent("c");

		AgentKnowledge entity = new AgentKnowledge();
		entity.setId(1);

		when(agentKnowledgeConverter.toEntityForCreate(eq(dto), isNull())).thenReturn(entity);
		when(agentKnowledgeMapper.insert(entity)).thenReturn(0);

		assertThrows(RuntimeException.class, () -> service.createKnowledge(dto));
	}

	@Test
	void updateKnowledge_notFound_throwsException() {
		when(agentKnowledgeMapper.selectById(99)).thenReturn(null);

		assertThrows(RuntimeException.class, () -> service.updateKnowledge(99, new UpdateKnowledgeDTO()));
	}

	@Test
	void updateKnowledge_success_updatesFields() {
		AgentKnowledge existing = new AgentKnowledge();
		existing.setId(1);
		existing.setTitle("Old Title");
		existing.setContent("Old Content");

		UpdateKnowledgeDTO dto = new UpdateKnowledgeDTO();
		dto.setTitle("New Title");
		dto.setContent("New Content");

		AgentKnowledgeVO vo = new AgentKnowledgeVO();
		vo.setTitle("New Title");

		when(agentKnowledgeMapper.selectById(1)).thenReturn(existing);
		when(agentKnowledgeMapper.update(existing)).thenReturn(1);
		when(agentKnowledgeConverter.toVo(existing)).thenReturn(vo);

		AgentKnowledgeVO result = service.updateKnowledge(1, dto);
		assertEquals("New Title", result.getTitle());
	}

	@Test
	void updateKnowledge_updateFails_throwsException() {
		AgentKnowledge existing = new AgentKnowledge();
		existing.setId(1);

		UpdateKnowledgeDTO dto = new UpdateKnowledgeDTO();
		dto.setTitle("New Title");

		when(agentKnowledgeMapper.selectById(1)).thenReturn(existing);
		when(agentKnowledgeMapper.update(existing)).thenReturn(0);

		assertThrows(RuntimeException.class, () -> service.updateKnowledge(1, dto));
	}

	@Test
	void deleteKnowledge_notFound_returnsTrue() {
		when(agentKnowledgeMapper.selectById(99)).thenReturn(null);

		assertTrue(service.deleteKnowledge(99));
	}

	@Test
	void deleteKnowledge_success_softDeletes() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);

		when(agentKnowledgeMapper.selectById(1)).thenReturn(knowledge);
		when(agentKnowledgeMapper.update(knowledge)).thenReturn(1);

		assertTrue(service.deleteKnowledge(1));
		assertEquals(1, knowledge.getIsDeleted());
		assertEquals(0, knowledge.getIsResourceCleaned());
		verify(eventPublisher).publishEvent(any());
	}

	@Test
	void deleteKnowledge_updateFails_returnsFalse() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);

		when(agentKnowledgeMapper.selectById(1)).thenReturn(knowledge);
		when(agentKnowledgeMapper.update(knowledge)).thenReturn(0);

		assertFalse(service.deleteKnowledge(1));
	}

	@Test
	void queryByConditionsWithPage_returnsPageResult() {
		AgentKnowledgeQueryDTO queryDTO = new AgentKnowledgeQueryDTO();
		queryDTO.setAgentId(1);
		queryDTO.setPageNum(1);
		queryDTO.setPageSize(10);

		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		AgentKnowledgeVO vo = new AgentKnowledgeVO();
		vo.setId(1);

		when(agentKnowledgeMapper.countByConditions(queryDTO)).thenReturn(1L);
		when(agentKnowledgeMapper.selectByConditionsWithPage(queryDTO, 0)).thenReturn(List.of(knowledge));
		when(agentKnowledgeConverter.toVo(knowledge)).thenReturn(vo);

		PageResult<AgentKnowledgeVO> result = service.queryByConditionsWithPage(queryDTO);
		assertEquals(1L, result.getTotal());
		assertEquals(1, result.getData().size());
	}

	@Test
	void updateKnowledgeRecallStatus_notFound_throwsException() {
		when(agentKnowledgeMapper.selectById(99)).thenReturn(null);

		assertThrows(RuntimeException.class, () -> service.updateKnowledgeRecallStatus(99, true));
	}

	@Test
	void updateKnowledgeRecallStatus_enable_setsRecallTo1() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		AgentKnowledgeVO vo = new AgentKnowledgeVO();

		when(agentKnowledgeMapper.selectById(1)).thenReturn(knowledge);
		when(agentKnowledgeMapper.update(knowledge)).thenReturn(1);
		when(agentKnowledgeConverter.toVo(knowledge)).thenReturn(vo);

		service.updateKnowledgeRecallStatus(1, true);
		assertEquals(1, knowledge.getIsRecall());
	}

	@Test
	void updateKnowledgeRecallStatus_disable_setsRecallTo0() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		AgentKnowledgeVO vo = new AgentKnowledgeVO();

		when(agentKnowledgeMapper.selectById(1)).thenReturn(knowledge);
		when(agentKnowledgeMapper.update(knowledge)).thenReturn(1);
		when(agentKnowledgeConverter.toVo(knowledge)).thenReturn(vo);

		service.updateKnowledgeRecallStatus(1, false);
		assertEquals(0, knowledge.getIsRecall());
	}

	@Test
	void updateKnowledgeRecallStatus_updateFails_throwsException() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);

		when(agentKnowledgeMapper.selectById(1)).thenReturn(knowledge);
		when(agentKnowledgeMapper.update(knowledge)).thenReturn(0);

		assertThrows(RuntimeException.class, () -> service.updateKnowledgeRecallStatus(1, true));
	}

	@Test
	void retryEmbedding_processing_throwsException() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setEmbeddingStatus(EmbeddingStatus.PROCESSING);

		when(agentKnowledgeMapper.selectById(1)).thenReturn(knowledge);

		assertThrows(RuntimeException.class, () -> service.retryEmbedding(1));
	}

	@Test
	void retryEmbedding_notRecalled_throwsException() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setEmbeddingStatus(EmbeddingStatus.FAILED);
		knowledge.setIsRecall(0);

		when(agentKnowledgeMapper.selectById(1)).thenReturn(knowledge);

		assertThrows(RuntimeException.class, () -> service.retryEmbedding(1));
	}

	@Test
	void retryEmbedding_nullRecall_throwsException() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setEmbeddingStatus(EmbeddingStatus.FAILED);
		knowledge.setIsRecall(null);

		when(agentKnowledgeMapper.selectById(1)).thenReturn(knowledge);

		assertThrows(RuntimeException.class, () -> service.retryEmbedding(1));
	}

	@Test
	void retryEmbedding_success_resetsStatusAndPublishesEvent() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		knowledge.setEmbeddingStatus(EmbeddingStatus.FAILED);
		knowledge.setIsRecall(1);
		knowledge.setSplitterType("token");

		when(agentKnowledgeMapper.selectById(1)).thenReturn(knowledge);

		service.retryEmbedding(1);

		assertEquals(EmbeddingStatus.PENDING, knowledge.getEmbeddingStatus());
		assertEquals("", knowledge.getErrorMsg());
		verify(agentKnowledgeMapper).update(knowledge);
		verify(eventPublisher).publishEvent(any());
	}

}
