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

import com.alibaba.cloud.ai.dataagent.dto.knowledge.businessknowledge.CreateBusinessKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.vo.BusinessKnowledgeVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BusinessKnowledgeConverterTest {

	private final BusinessKnowledgeConverter converter = new BusinessKnowledgeConverter();

	@Test
	void toVo_allFieldsMapped() {
		LocalDateTime now = LocalDateTime.now();
		BusinessKnowledge po = BusinessKnowledge.builder()
			.id(1L)
			.businessTerm("GMV")
			.description("Gross Merchandise Volume")
			.synonyms("sales,revenue")
			.isRecall(1)
			.agentId(10L)
			.createdTime(now)
			.updatedTime(now)
			.embeddingStatus(EmbeddingStatus.COMPLETED)
			.errorMsg(null)
			.build();

		BusinessKnowledgeVO vo = converter.toVo(po);

		assertEquals(1L, vo.getId());
		assertEquals("GMV", vo.getBusinessTerm());
		assertEquals("Gross Merchandise Volume", vo.getDescription());
		assertEquals("sales,revenue", vo.getSynonyms());
		assertTrue(vo.getIsRecall());
		assertEquals(10L, vo.getAgentId());
		assertEquals(now, vo.getCreatedTime());
		assertEquals(now, vo.getUpdatedTime());
		assertEquals("COMPLETED", vo.getEmbeddingStatus());
		assertNull(vo.getErrorMsg());
	}

	@Test
	void toVo_isRecallZero_returnsFalse() {
		BusinessKnowledge po = BusinessKnowledge.builder().id(1L).isRecall(0).build();

		BusinessKnowledgeVO vo = converter.toVo(po);

		assertFalse(vo.getIsRecall());
	}

	@Test
	void toVo_nullEmbeddingStatus_returnsNullStatus() {
		BusinessKnowledge po = BusinessKnowledge.builder().id(1L).isRecall(1).embeddingStatus(null).build();

		BusinessKnowledgeVO vo = converter.toVo(po);

		assertNull(vo.getEmbeddingStatus());
	}

	@Test
	void toVo_withErrorMsg_mapsErrorMsg() {
		BusinessKnowledge po = BusinessKnowledge.builder()
			.id(1L)
			.isRecall(1)
			.embeddingStatus(EmbeddingStatus.FAILED)
			.errorMsg("embedding failed")
			.build();

		BusinessKnowledgeVO vo = converter.toVo(po);

		assertEquals("FAILED", vo.getEmbeddingStatus());
		assertEquals("embedding failed", vo.getErrorMsg());
	}

	@Test
	void toEntityForCreate_allFieldsMapped() {
		CreateBusinessKnowledgeDTO dto = CreateBusinessKnowledgeDTO.builder()
			.businessTerm("DAU")
			.description("Daily Active Users")
			.synonyms("active users,daily users")
			.isRecall(true)
			.agentId(5L)
			.build();

		BusinessKnowledge entity = converter.toEntityForCreate(dto);

		assertEquals("DAU", entity.getBusinessTerm());
		assertEquals("Daily Active Users", entity.getDescription());
		assertEquals("active users,daily users", entity.getSynonyms());
		assertEquals(5L, entity.getAgentId());
		assertEquals(1, entity.getIsRecall());
		assertEquals(0, entity.getIsDeleted());
		assertEquals(EmbeddingStatus.PROCESSING, entity.getEmbeddingStatus());
	}

	@Test
	void toEntityForCreate_isRecallFalse_setsZero() {
		CreateBusinessKnowledgeDTO dto = CreateBusinessKnowledgeDTO.builder()
			.businessTerm("term")
			.description("desc")
			.isRecall(false)
			.agentId(1L)
			.build();

		BusinessKnowledge entity = converter.toEntityForCreate(dto);

		assertEquals(0, entity.getIsRecall());
	}

}
