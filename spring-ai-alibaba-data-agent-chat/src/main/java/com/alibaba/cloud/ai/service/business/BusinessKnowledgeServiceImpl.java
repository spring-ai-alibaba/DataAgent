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

package com.alibaba.cloud.ai.service.business;

import com.alibaba.cloud.ai.constant.Constant;
import com.alibaba.cloud.ai.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dto.BusinessKnowledgeDTO;
import com.alibaba.cloud.ai.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.mapper.BusinessKnowledgeMapper;
import com.alibaba.cloud.ai.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.util.DocumentConverterUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BusinessKnowledgeServiceImpl implements BusinessKnowledgeService {

	private final BusinessKnowledgeMapper businessKnowledgeMapper;

	private final VectorStore vectorStore;

	private final AgentVectorStoreService agentVectorStoreService;

	public BusinessKnowledgeServiceImpl(BusinessKnowledgeMapper businessKnowledgeMapper, VectorStore vectorStore,
			AgentVectorStoreService agentVectorStoreService) {
		this.businessKnowledgeMapper = businessKnowledgeMapper;
		this.vectorStore = vectorStore;
		this.agentVectorStoreService = agentVectorStoreService;
	}

	@Override
	public List<BusinessKnowledge> getKnowledge(Long agentId) {
		return businessKnowledgeMapper.selectByAgentId(agentId);
	}

	@Override
	public List<BusinessKnowledge> getKnowledgeRecalled(Long agentId) {
		return businessKnowledgeMapper.selectRecalledByAgentId(agentId);
	}

	@Override
	public List<BusinessKnowledge> getAllKnowledge() {
		return businessKnowledgeMapper.selectAll();
	}

	@Override
	public List<BusinessKnowledge> searchKnowledge(Long agentId, String keyword) {
		return businessKnowledgeMapper.searchInAgent(agentId, keyword);
	}

	@Override
	public BusinessKnowledge getKnowledgeById(Long id) {
		return businessKnowledgeMapper.selectById(id);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long addKnowledge(BusinessKnowledgeDTO knowledgeDTO) {
		BusinessKnowledge entity = knowledgeDTO.toEntity();

		// 插入数据库
		if (businessKnowledgeMapper.insert(entity) <= 0) {
			throw new RuntimeException("Failed to add knowledge to database");
		}

		try {
			// 转换为文档并插入向量库
			Document document = DocumentConverterUtil.convertBusinessTermToDocument(knowledgeDTO,
					entity.getId().toString());
			vectorStore.add(List.of(document));
			return entity.getId();
		}
		catch (Exception e) {
			// 向量库插入失败，清理数据库记录
			try {
				businessKnowledgeMapper.deleteById(entity.getId());
			}
			catch (Exception deleteException) {
				log.error("Failed to clean up database record after vector store error: {}",
						deleteException.getMessage());
			}
			throw new RuntimeException("Failed to add knowledge: " + e.getMessage(), e);
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateKnowledge(Long id, BusinessKnowledgeDTO knowledgeDTO) {
		BusinessKnowledge entity = knowledgeDTO.toEntity();
		entity.setId(id);

		// 更新数据库
		if (businessKnowledgeMapper.updateById(entity) <= 0) {
			throw new RuntimeException("Failed to update knowledge in database");
		}

		try {
			// 更新向量库中的文档
			Map<String, Object> metadata = new HashMap<>();
			metadata.put(Constant.AGENT_ID, entity.getAgentId());
			metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.BUSINESS_TERM);
			metadata.put(DocumentMetadataConstant.DB_RECORD_ID, id.toString());
			agentVectorStoreService.deleteDocumentsByMetedata(knowledgeDTO.getAgentId().toString(), metadata);

			Document document = DocumentConverterUtil.convertBusinessTermToDocument(knowledgeDTO, id.toString());
			vectorStore.add(List.of(document));
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to update knowledge in vector store: " + e.getMessage(), e);
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteKnowledge(Long id) {
		// 从数据库获取原始数据
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			log.warn("Knowledge not found with id: " + id);
			return;
		}

		try {
			// 从向量库删除文档
			Map<String, Object> metadata = new HashMap<>();
			metadata.put(Constant.AGENT_ID, knowledge.getAgentId());
			metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.BUSINESS_TERM);
			metadata.put(DocumentMetadataConstant.DB_RECORD_ID, id.toString());
			agentVectorStoreService.deleteDocumentsByMetedata(knowledge.getAgentId().toString(), metadata);
		}
		catch (Exception e) {
			log.warn("Failed to delete document from vector store: {}", e.getMessage());
		}

		// 从数据库删除记录
		if (businessKnowledgeMapper.deleteById(id) <= 0) {
			throw new RuntimeException("Failed to delete knowledge from database");
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void recallKnowledge(Long id, boolean isRecall) {
		// 从数据库获取原始数据
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			throw new RuntimeException("Knowledge not found with id: " + id);
		}

		// 更新数据库中的召回状态
		if (businessKnowledgeMapper.changeRecall(id, isRecall) <= 0) {
			throw new RuntimeException("Failed to change recall status in database");
		}

		try {
			// 更新向量库中的文档
			Map<String, Object> metadata = new HashMap<>();
			metadata.put(Constant.AGENT_ID, knowledge.getAgentId());
			metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.BUSINESS_TERM);
			metadata.put(DocumentMetadataConstant.DB_RECORD_ID, id.toString());
			agentVectorStoreService.deleteDocumentsByMetedata(knowledge.getAgentId().toString(), metadata);

			BusinessKnowledgeDTO dto = new BusinessKnowledgeDTO();
			dto.setBusinessTerm(knowledge.getBusinessTerm());
			dto.setDescription(knowledge.getDescription());
			dto.setSynonyms(knowledge.getSynonyms());
			dto.setAgentId(knowledge.getAgentId());
			dto.setIsRecall(isRecall);

			Document document = DocumentConverterUtil.convertBusinessTermToDocument(dto, id.toString());
			vectorStore.add(List.of(document));
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to update recall status in vector store: " + e.getMessage(), e);
		}
	}

}
