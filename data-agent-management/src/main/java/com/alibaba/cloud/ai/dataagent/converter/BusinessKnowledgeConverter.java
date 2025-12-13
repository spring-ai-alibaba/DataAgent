/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import com.alibaba.cloud.ai.dataagent.common.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.vo.BusinessKnowledgeVO;
import org.springframework.stereotype.Component;

@Component
public class BusinessKnowledgeConverter {

	public BusinessKnowledgeVO toVo(BusinessKnowledge po) {
		return BusinessKnowledgeVO.builder()
			.id(po.getId())
			.businessTerm(po.getBusinessTerm())
			.description(po.getDescription())
			.synonyms(po.getSynonyms())
			.isRecall(po.getIsRecall() == 1)
			.agentId(po.getAgentId())
			.createdTime(po.getCreatedTime())
			.updatedTime(po.getUpdatedTime())
			.embeddingStatus(po.getEmbeddingStatus() != null ? po.getEmbeddingStatus().getValue() : null)
			.errorMsg(po.getErrorMsg())
			.build();
	}

	// toEntityForCreate
	public BusinessKnowledge toEntityForCreate(CreateBusinessKnowledgeDTO dto) {
		return BusinessKnowledge.builder()
			.businessTerm(dto.getBusinessTerm())
			.description(dto.getDescription())
			.synonyms(dto.getSynonyms())
			.agentId(dto.getAgentId())
			.isRecall(dto.getIsRecall() ? 1 : 0)
			.isDeleted(0)
			.embeddingStatus(EmbeddingStatus.PROCESSING)
			.build();

	}

}
