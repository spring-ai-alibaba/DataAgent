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

package com.alibaba.cloud.ai.dataagent.service.business;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.converter.BusinessKnowledgeConverter;
import com.alibaba.cloud.ai.dataagent.dto.businessknowledge.CreateBusinessKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.dto.businessknowledge.UpdateBusinessKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.mapper.BusinessKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.util.DocumentConverterUtil;
import com.alibaba.cloud.ai.dataagent.vo.BusinessKnowledgeVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class BusinessKnowledgeServiceImpl implements BusinessKnowledgeService {

	private final BusinessKnowledgeMapper businessKnowledgeMapper;

	private final AgentVectorStoreService agentVectorStoreService;

	private final BusinessKnowledgeConverter businessKnowledgeConverter;

	@Override
	public List<BusinessKnowledgeVO> getKnowledge(Long agentId) {
		List<BusinessKnowledge> businessKnowledges = businessKnowledgeMapper.selectByAgentId(agentId);
		if (CollectionUtils.isEmpty(businessKnowledges)) {
			return Collections.emptyList();
		}
		return businessKnowledges.stream().map(businessKnowledgeConverter::toVo).toList();
	}

	@Override
	public List<BusinessKnowledgeVO> getAllKnowledge() {
		List<BusinessKnowledge> businessKnowledges = businessKnowledgeMapper.selectAll();
		if (CollectionUtils.isEmpty(businessKnowledges)) {
			return Collections.emptyList();
		}
		return businessKnowledges.stream().map(businessKnowledgeConverter::toVo).toList();
	}

	@Override
	public List<BusinessKnowledgeVO> searchKnowledge(Long agentId, String keyword) {
		List<BusinessKnowledge> businessKnowledges = businessKnowledgeMapper.searchInAgent(agentId, keyword);
		if (CollectionUtils.isEmpty(businessKnowledges)) {
			return Collections.emptyList();
		}
		return businessKnowledges.stream().map(businessKnowledgeConverter::toVo).toList();
	}

	@Override
	public BusinessKnowledgeVO getKnowledgeById(Long id) {
		BusinessKnowledge businessKnowledge = businessKnowledgeMapper.selectById(id);
		if (businessKnowledge == null) {
			return null;
		}
		return businessKnowledgeConverter.toVo(businessKnowledge);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BusinessKnowledgeVO addKnowledge(CreateBusinessKnowledgeDTO knowledgeDTO) {
		BusinessKnowledge entity = businessKnowledgeConverter.toEntityForCreate(knowledgeDTO);

		// 插入数据库
		if (businessKnowledgeMapper.insert(entity) <= 0) {
			throw new RuntimeException("Failed to add knowledge to database");
		}

		try {
			Document document = DocumentConverterUtil.convertBusinessKnowledgeToDocument(entity);
			agentVectorStoreService.addDocuments(entity.getAgentId().toString(), List.of(document));
			entity.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
			entity.setErrorMsg(null);
			businessKnowledgeMapper.updateById(entity);
		}
		catch (Exception e) {
			String errorMsg = "Failed to add to vector store: " + e.getMessage();
			entity.setEmbeddingStatus(EmbeddingStatus.FAILED);
			entity.setErrorMsg(errorMsg);
			businessKnowledgeMapper.updateById(entity);
			log.error("Failed to add knowledge to vector store for id: {}, error: {}", entity.getId(), errorMsg);
		}
		return businessKnowledgeConverter.toVo(entity);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BusinessKnowledgeVO updateKnowledge(Long id, UpdateBusinessKnowledgeDTO knowledgeDTO) {
		// 从数据库获取原始数据
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			throw new RuntimeException("Knowledge not found with id: " + id);
		}
		// 更新属性
		knowledge.setBusinessTerm(knowledgeDTO.getBusinessTerm());
		knowledge.setDescription(knowledgeDTO.getDescription());
		if (StringUtils.hasText(knowledgeDTO.getSynonyms()))
			knowledge.setSynonyms(knowledgeDTO.getSynonyms());

		// 设置初始状态为处理中
		knowledge.setEmbeddingStatus(EmbeddingStatus.PROCESSING);

		// 先更新数据库
		if (businessKnowledgeMapper.updateById(knowledge) <= 0) {
			throw new RuntimeException("Failed to update knowledge in database");
		}

		// 尝试更新向量库
		try {
			syncToVectorStore(knowledge);
			knowledge.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
			knowledge.setErrorMsg(null);
			businessKnowledgeMapper.updateById(knowledge);
		}
		catch (Exception e) {
			// 向量库更新失败，不回滚MySQL，只标记状态为失败
			String errorMsg = "Failed to update vector store: " + e.getMessage();
			knowledge.setEmbeddingStatus(EmbeddingStatus.FAILED);
			knowledge.setErrorMsg(errorMsg);
			businessKnowledgeMapper.updateById(knowledge);
			log.error("Failed to update vector store for knowledge id: {}, error: {}", id, errorMsg);
		}
		return businessKnowledgeConverter.toVo(knowledge);
	}

	/**
	 * 更新向量库中的知识向量
	 */
	private void syncToVectorStore(BusinessKnowledge knowledge) {
		// 先删除旧的向量数据
		this.doDelVector(knowledge);

		// 添加新的向量数据
		Document newDocument = DocumentConverterUtil.convertBusinessKnowledgeToDocument(knowledge);
		agentVectorStoreService.addDocuments(knowledge.getAgentId().toString(), List.of(newDocument));

		log.info("Successfully updated vector store for knowledge id: {}", knowledge.getId());
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

		doDelVector(knowledge);

		if (businessKnowledgeMapper.logicalDelete(id, 1) <= 0) {
			// 重新添加修复被删除的记录
			agentVectorStoreService.addDocuments(knowledge.getAgentId().toString(),
					List.of(DocumentConverterUtil.convertBusinessKnowledgeToDocument(knowledge)));
			throw new RuntimeException("Failed to logically delete knowledge from database");
		}
	}

	private void doDelVector(BusinessKnowledge knowledge) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(Constant.AGENT_ID, knowledge.getAgentId().toString());
		metadata.put(DocumentMetadataConstant.DB_BUSINESS_TERM_ID, knowledge.getId());
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.BUSINESS_TERM);
		agentVectorStoreService.deleteDocumentsByMetedata(knowledge.getAgentId().toString(), metadata);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void recallKnowledge(Long id, Boolean isRecall) {
		// 从数据库获取原始数据
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			throw new RuntimeException("Knowledge not found with id: " + id);
		}

		// 更新数据库即可，不需要更新向量库，混合检索的的时候DynamicFilterService会根据 isRecall 字段过滤了
		knowledge.setIsRecall(isRecall ? 1 : 0);
		businessKnowledgeMapper.updateById(knowledge);

	}

	@Override
	public void refreshAllKnowledgeToVectorStore(String agentId) throws Exception {
		agentVectorStoreService.deleteDocumentsByVectorType(agentId, DocumentMetadataConstant.BUSINESS_TERM);

		// 获取所有 isRecall 等于 1 且未逻辑删除的 BusinessKnowledge
		List<BusinessKnowledge> allKnowledge = businessKnowledgeMapper.selectAll();
		List<BusinessKnowledge> recalledKnowledge = allKnowledge.stream()
			.filter(knowledge -> knowledge.getIsRecall() != null && knowledge.getIsRecall() == 1)
			.filter(knowledge -> knowledge.getIsDeleted() == null || knowledge.getIsDeleted() == 0)
			.filter(knowledge -> agentId.equals(knowledge.getAgentId().toString()))
			.toList();

		// 转换为 Document 并插入到 vectorStore
		if (!recalledKnowledge.isEmpty()) {
			List<Document> documents = recalledKnowledge.stream()
				.map(DocumentConverterUtil::convertBusinessKnowledgeToDocument)
				.toList();
			agentVectorStoreService.addDocuments(agentId, documents);
		}
	}

	@Override
	public void retryEmbedding(Long id) {
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			throw new RuntimeException("BusinessKnowledge not found with id: " + id);
		}

		if (knowledge.getEmbeddingStatus().equals(EmbeddingStatus.PROCESSING)) {
			throw new RuntimeException("BusinessKnowledge is processing, please wait.");
		}

		// 非召回的不处理
		if (knowledge.getIsRecall() == null || knowledge.getIsRecall() == 0) {
			throw new RuntimeException("BusinessKnowledge is not recalled, please recall it first.");
		}

		try {
			syncToVectorStore(knowledge);
			knowledge.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
			knowledge.setErrorMsg(null);
			businessKnowledgeMapper.updateById(knowledge);
		}
		catch (Exception e) {
			// 再次失败，更新错误信息
			knowledge.setEmbeddingStatus(EmbeddingStatus.FAILED);
			knowledge.setErrorMsg(e.getMessage().length() > 200 ? e.getMessage().substring(0, 200) : e.getMessage());
			businessKnowledgeMapper.updateById(knowledge);
			throw new RuntimeException("重试失败: " + e.getMessage());
		}

	}

}
