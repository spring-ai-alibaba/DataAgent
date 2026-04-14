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
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AgentKnowledgeConverter {

	// toVo
	public AgentKnowledgeVO toVo(AgentKnowledge po) {
		AgentKnowledgeVO vo = new AgentKnowledgeVO();
		vo.setId(po.getId());
		vo.setAgentId(po.getAgentId());
		vo.setTitle(po.getTitle());
		vo.setType(po.getType() != null ? po.getType().getCode() : null);
		vo.setQuestion(po.getQuestion());
		vo.setContent(po.getContent());
		vo.setIsRecall(po.getIsRecall() == 1);
		vo.setEmbeddingStatus(po.getEmbeddingStatus());
		vo.setSplitterType(po.getSplitterType());
		vo.setErrorMsg(po.getErrorMsg());
		vo.setCreatedTime(po.getCreatedTime());
		vo.setUpdatedTime(po.getUpdatedTime());
		return vo;
	}

	public AgentKnowledge toEntityForCreate(CreateKnowledgeDTO createKnowledgeDto, String storagePath) {
		// 创建AgentKnowledge对象
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setAgentId(createKnowledgeDto.getAgentId());
		knowledge.setTitle(createKnowledgeDto.getTitle());
		knowledge.setType(KnowledgeType.valueOf(createKnowledgeDto.getType()));
		knowledge.setQuestion(createKnowledgeDto.getQuestion());
		knowledge.setContent(createKnowledgeDto.getContent());
		knowledge.setIsRecall(1); // 默认为召回状态
		knowledge.setIsDeleted(0); // 默认为未删除
		knowledge.setEmbeddingStatus(EmbeddingStatus.PENDING); // 初始状态为待处理
		knowledge.setIsResourceCleaned(0); // 默认为物理资源未清理

		// 设置创建和更新时间
		LocalDateTime now = LocalDateTime.now();
		knowledge.setCreatedTime(now);
		knowledge.setUpdatedTime(now);

		// 如果是文档类型，设置文件相关信息
		if (createKnowledgeDto.getFile() != null && !createKnowledgeDto.getFile().isEmpty()) {
			knowledge.setSourceFilename(createKnowledgeDto.getFile().getOriginalFilename());
			knowledge.setFilePath(storagePath);
			knowledge.setFileSize(createKnowledgeDto.getFile().getSize());
			knowledge.setFileType(createKnowledgeDto.getFile().getContentType());
		}

		// 设置分块策略类型，默认值为token
		String splitterType = createKnowledgeDto.getSplitterType();
		if (splitterType == null || splitterType.isBlank()) {
			splitterType = "token";
		}
		knowledge.setSplitterType(splitterType);

		return knowledge;
	}

}
