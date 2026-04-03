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
package com.alibaba.cloud.ai.dataagent.converter;

import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.CreateKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.vo.AgentKnowledgeVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class AgentKnowledgeConverterTest {

	private final AgentKnowledgeConverter converter = new AgentKnowledgeConverter();

	@Test
	void toVo_allFieldsMapped() {
		AgentKnowledge po = new AgentKnowledge();
		po.setId(1);
		po.setAgentId(2);
		po.setTitle("Test Knowledge");
		po.setType(KnowledgeType.QA);
		po.setQuestion("What is AI?");
		po.setContent("AI is...");
		po.setIsRecall(1);
		po.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
		po.setSplitterType("token");
		po.setErrorMsg(null);

		AgentKnowledgeVO vo = converter.toVo(po);

		assertEquals(1, vo.getId());
		assertEquals(2, vo.getAgentId());
		assertEquals("Test Knowledge", vo.getTitle());
		assertEquals("QA", vo.getType());
		assertEquals("What is AI?", vo.getQuestion());
		assertEquals("AI is...", vo.getContent());
		assertTrue(vo.getIsRecall());
		assertEquals(EmbeddingStatus.COMPLETED, vo.getEmbeddingStatus());
		assertEquals("token", vo.getSplitterType());
		assertNull(vo.getErrorMsg());
	}

	@Test
	void toVo_nullType_returnsNullTypeCode() {
		AgentKnowledge po = new AgentKnowledge();
		po.setId(1);
		po.setType(null);
		po.setIsRecall(0);

		AgentKnowledgeVO vo = converter.toVo(po);

		assertNull(vo.getType());
		assertFalse(vo.getIsRecall());
	}

	@Test
	void toEntityForCreate_qaType_setsDefaults() {
		CreateKnowledgeDTO dto = new CreateKnowledgeDTO();
		dto.setAgentId(1);
		dto.setTitle("QA Title");
		dto.setType("QA");
		dto.setQuestion("Q?");
		dto.setContent("A.");
		dto.setSplitterType("recursive");

		AgentKnowledge entity = converter.toEntityForCreate(dto, null);

		assertEquals(1, entity.getAgentId());
		assertEquals("QA Title", entity.getTitle());
		assertEquals(KnowledgeType.QA, entity.getType());
		assertEquals("Q?", entity.getQuestion());
		assertEquals("A.", entity.getContent());
		assertEquals(1, entity.getIsRecall());
		assertEquals(0, entity.getIsDeleted());
		assertEquals(EmbeddingStatus.PENDING, entity.getEmbeddingStatus());
		assertEquals(0, entity.getIsResourceCleaned());
		assertEquals("recursive", entity.getSplitterType());
		assertNotNull(entity.getCreatedTime());
		assertNotNull(entity.getUpdatedTime());
	}

	@Test
	void toEntityForCreate_nullSplitterType_defaultsToToken() {
		CreateKnowledgeDTO dto = new CreateKnowledgeDTO();
		dto.setAgentId(1);
		dto.setTitle("title");
		dto.setType("FAQ");
		dto.setSplitterType(null);

		AgentKnowledge entity = converter.toEntityForCreate(dto, null);

		assertEquals("token", entity.getSplitterType());
	}

	@Test
	void toEntityForCreate_blankSplitterType_defaultsToToken() {
		CreateKnowledgeDTO dto = new CreateKnowledgeDTO();
		dto.setAgentId(1);
		dto.setTitle("title");
		dto.setType("FAQ");
		dto.setSplitterType("   ");

		AgentKnowledge entity = converter.toEntityForCreate(dto, null);

		assertEquals("token", entity.getSplitterType());
	}

	@Test
	void toEntityForCreate_withFile_setsFileInfo() {
		CreateKnowledgeDTO dto = new CreateKnowledgeDTO();
		dto.setAgentId(1);
		dto.setTitle("Doc Title");
		dto.setType("DOCUMENT");

		MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "pdf content".getBytes());
		dto.setFile(file);

		AgentKnowledge entity = converter.toEntityForCreate(dto, "/uploads/test.pdf");

		assertEquals("test.pdf", entity.getSourceFilename());
		assertEquals("/uploads/test.pdf", entity.getFilePath());
		assertEquals(11L, entity.getFileSize());
		assertEquals("application/pdf", entity.getFileType());
	}

	@Test
	void toEntityForCreate_noFile_noFileInfo() {
		CreateKnowledgeDTO dto = new CreateKnowledgeDTO();
		dto.setAgentId(1);
		dto.setTitle("QA");
		dto.setType("QA");
		dto.setFile(null);

		AgentKnowledge entity = converter.toEntityForCreate(dto, null);

		assertNull(entity.getSourceFilename());
		assertNull(entity.getFilePath());
	}

}
