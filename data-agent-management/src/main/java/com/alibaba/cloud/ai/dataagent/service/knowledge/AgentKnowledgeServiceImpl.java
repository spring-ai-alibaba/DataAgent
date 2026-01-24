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

import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.converter.AgentKnowledgeConverter;
import com.alibaba.cloud.ai.dataagent.vo.PageResult;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.AgentKnowledgeQueryDTO;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.CreateKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.UpdateKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.event.AgentKnowledgeDeletionEvent;
import com.alibaba.cloud.ai.dataagent.event.AgentKnowledgeEmbeddingEvent;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.vo.AgentKnowledgeVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class AgentKnowledgeServiceImpl implements AgentKnowledgeService {

	private static final String AGENT_KNOWLEDGE_FILE_PATH = "agent-knowledge";

	private final AgentKnowledgeMapper agentKnowledgeMapper;

	private final FileStorageService fileStorageService;

	private final AgentKnowledgeConverter agentKnowledgeConverter;

	private final ApplicationEventPublisher eventPublisher;

	@Override
	public AgentKnowledgeVO getKnowledgeById(Integer id) {
		AgentKnowledge agentKnowledge = agentKnowledgeMapper.selectById(id);
		return agentKnowledge == null ? null : agentKnowledgeConverter.toVo(agentKnowledge);
	}

	@Override
	@Transactional
	public AgentKnowledgeVO createKnowledge(CreateKnowledgeDTO createKnowledgeDto) {
		String storagePath = null;
		checkCreateKnowledgeDto(createKnowledgeDto);

		if (createKnowledgeDto.getType().equals(KnowledgeType.DOCUMENT.getCode())) {
			// 将文件保存到磁盘
			try {
				storagePath = fileStorageService.storeFile(createKnowledgeDto.getFile(), AGENT_KNOWLEDGE_FILE_PATH);
			}
			catch (Exception e) {
				log.error("Failed to store file, agentId:{} title:{} type:{} ", createKnowledgeDto.getAgentId(),
						createKnowledgeDto.getTitle(), createKnowledgeDto.getType());
				throw new RuntimeException("Failed to store file.");
			}
		}

		AgentKnowledge knowledge = agentKnowledgeConverter.toEntityForCreate(createKnowledgeDto, storagePath);

		if (agentKnowledgeMapper.insert(knowledge) <= 0) {
			log.error("Failed to create knowledge, agentId:{} title:{} type:{} ", knowledge.getAgentId(),
					knowledge.getTitle(), knowledge.getType());
			throw new RuntimeException("Failed to create knowledge in database.");
		}

		eventPublisher
			.publishEvent(new AgentKnowledgeEmbeddingEvent(this, knowledge.getId(), knowledge.getSplitterType()));
		log.info("Knowledge created and event published. Id: {}, splitterType: {}", knowledge.getId(),
				knowledge.getSplitterType());

		return agentKnowledgeConverter.toVo(knowledge);
	}

	private static void checkCreateKnowledgeDto(CreateKnowledgeDTO createKnowledgeDto) {
		if (createKnowledgeDto.getType().equals(KnowledgeType.DOCUMENT.getCode())
				&& createKnowledgeDto.getFile() == null) {
			throw new RuntimeException("File is required for document type.");
		}
		if (createKnowledgeDto.getType().equals(KnowledgeType.QA.getCode())
				|| createKnowledgeDto.getType().equals(KnowledgeType.FAQ.getCode())) {

			if (!StringUtils.hasText(createKnowledgeDto.getQuestion())) {
				throw new RuntimeException("Question is required for QA or FAQ type.");
			}
			if (!StringUtils.hasText(createKnowledgeDto.getContent())) {
				throw new RuntimeException("Content is required for QA or FAQ type.");
			}

		}
	}

	@Override
	@Transactional
	public AgentKnowledgeVO updateKnowledge(Integer id, UpdateKnowledgeDTO updateKnowledgeDto) {
		// 基础校验：根据 id 查询数据库
		AgentKnowledge existingKnowledge = agentKnowledgeMapper.selectById(id);
		if (existingKnowledge == null) {
			log.warn("Knowledge not found with id: {}", id);
			throw new RuntimeException("Knowledge not found.");
		}

		if (StringUtils.hasText(updateKnowledgeDto.getTitle()))
			existingKnowledge.setTitle(updateKnowledgeDto.getTitle());

		// content
		if (StringUtils.hasText(updateKnowledgeDto.getContent()))
			existingKnowledge.setContent(updateKnowledgeDto.getContent());

		// 更新数据库
		int updateResult = agentKnowledgeMapper.update(existingKnowledge);
		if (updateResult <= 0) {
			log.error("Failed to update knowledge with id: {}", existingKnowledge.getId());
			throw new RuntimeException("Failed to update knowledge in database.");
		}
		return agentKnowledgeConverter.toVo(existingKnowledge);
	}

	@Override
	@Transactional
	public boolean deleteKnowledge(Integer id) {
		// 先获取知识信息，用于后续删除文件和向量数据
		AgentKnowledge knowledge = agentKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			log.warn("Knowledge not found with id: {}, treating as already deleted", id);
			return true;
		}

		// 同步执行软删除
		knowledge.setIsDeleted(1);
		knowledge.setIsResourceCleaned(0);
		knowledge.setUpdatedTime(LocalDateTime.now());

		if (agentKnowledgeMapper.update(knowledge) > 0) {
			eventPublisher.publishEvent(new AgentKnowledgeDeletionEvent(this, id));
			return true;
		}
		return false;
	}

	@Override
	public PageResult<AgentKnowledgeVO> queryByConditionsWithPage(AgentKnowledgeQueryDTO queryDTO) {

		int offset = (queryDTO.getPageNum() - 1) * queryDTO.getPageSize();

		Long total = agentKnowledgeMapper.countByConditions(queryDTO);

		List<AgentKnowledge> dataList = agentKnowledgeMapper.selectByConditionsWithPage(queryDTO, offset);
		List<AgentKnowledgeVO> dataListVO = dataList.stream().map(agentKnowledgeConverter::toVo).toList();
		PageResult<AgentKnowledgeVO> pageResult = new PageResult<>();
		pageResult.setData(dataListVO);
		pageResult.setTotal(total);
		pageResult.setPageNum(queryDTO.getPageNum());
		pageResult.setPageSize(queryDTO.getPageSize());
		pageResult.calculateTotalPages();

		return pageResult;
	}

	@Override
	public AgentKnowledgeVO updateKnowledgeRecallStatus(Integer id, Boolean recalled) {
		// 查询知识
		AgentKnowledge knowledge = agentKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			throw new RuntimeException("Knowledge not found.");
		}

		// 更新召回状态
		knowledge.setIsRecall(recalled ? 1 : 0);

		// 更新数据库
		boolean res = agentKnowledgeMapper.update(knowledge) > 0;
		if (!res) {
			log.error("Failed to update knowledge with id: {}", knowledge.getId());
			throw new RuntimeException("Failed to update knowledge in database.");
		}
		return agentKnowledgeConverter.toVo(knowledge);
	}

	@Override
	@Transactional
	public void retryEmbedding(Integer id) {
		AgentKnowledge knowledge = agentKnowledgeMapper.selectById(id);
		if (knowledge.getEmbeddingStatus().equals(EmbeddingStatus.PROCESSING)) {
			throw new RuntimeException("BusinessKnowledge is processing, please wait.");
		}

		// 非召回的不处理
		if (knowledge.getIsRecall() == null || knowledge.getIsRecall() == 0) {
			throw new RuntimeException("BusinessKnowledge is not recalled, please recall it first.");
		}

		// 重置状态
		// 立刻给用户反馈"已变成处理中"
		knowledge.setEmbeddingStatus(EmbeddingStatus.PENDING);
		knowledge.setErrorMsg("");
		agentKnowledgeMapper.update(knowledge);
		eventPublisher
			.publishEvent(new AgentKnowledgeEmbeddingEvent(this, knowledge.getId(), knowledge.getSplitterType()));
		log.info("Retry embedding for knowledgeId: {}, splitterType: {}", id, knowledge.getSplitterType());
	}

}
