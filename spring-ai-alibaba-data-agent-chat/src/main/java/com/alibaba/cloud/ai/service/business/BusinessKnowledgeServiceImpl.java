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

import java.util.List;

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
			Document document = DocumentConverterUtil.convertBusinessKnowledgeToDocument(entity);
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
		// 从数据库获取原始数据
		BusinessKnowledge oldKnowledge = businessKnowledgeMapper.selectById(id);
		if (oldKnowledge == null) {
			throw new RuntimeException("Knowledge not found with id: " + id);
		}

		BusinessKnowledge newKnowledge = knowledgeDTO.toEntity();
		newKnowledge.setId(id);

		// 先更新数据库
		updateDatabase(newKnowledge);

		// 更新向量库
		updateVectorStore(oldKnowledge, newKnowledge);
	}

	/**
	 * 更新数据库中的知识记录
	 */
	private void updateDatabase(BusinessKnowledge newKnowledge) {
		if (businessKnowledgeMapper.updateById(newKnowledge) <= 0) {
			throw new RuntimeException("Failed to update knowledge in database");
		}
	}

	// TODO 2025/11/17 后续可优化改造事务性发件箱模式以确保最终一致性
	/**
	 * 更新向量库中的知识向量
	 */
	private void updateVectorStore(BusinessKnowledge oldKnowledge, BusinessKnowledge newKnowledge) {
		String fixedBusinessKnowledgeDocId = DocumentConverterUtil
			.generateFixedBusinessKnowledgeDocId(oldKnowledge.getAgentId().toString(), oldKnowledge.getId());

		// 保存旧的文档，以便在添加新文档失败时回滚
		Document oldDocument = null;
		try {
			// 获取旧文档内容，用于回滚
			oldDocument = DocumentConverterUtil.convertBusinessKnowledgeToDocument(oldKnowledge);

			// 先删除旧的向量数据
			vectorStore.delete(List.of(fixedBusinessKnowledgeDocId));

			// 添加新的向量数据
			Document newDocument = DocumentConverterUtil.convertBusinessKnowledgeToDocument(newKnowledge);
			vectorStore.add(List.of(newDocument));

			log.info("Successfully updated vector store for knowledge id: {}", newKnowledge.getId());
		}
		catch (Exception e) {
			// 向量库更新失败，尝试回滚向量库更改
			rollbackVectorStoreChanges(oldDocument, fixedBusinessKnowledgeDocId, e);
		}
	}

	/**
	 * 回滚向量库更改并抛出异常
	 */
	private void rollbackVectorStoreChanges(Document oldDocument, String documentId, Exception originalException) {
		log.error("Failed to update vector store for document id: {}, attempting to rollback vector store changes",
				documentId);

		boolean rollbackSuccessful = false;
		if (oldDocument != null) {
			try {
				// 回滚向量库更改 - 重新添加旧的文档
				vectorStore.add(List.of(oldDocument));
				rollbackSuccessful = true;
				log.info("Successfully rolled back vector store changes for document id: {}", documentId);
			}
			catch (Exception rollbackException) {
				log.error("Failed to rollback vector store changes for document id: {}: {}", documentId,
						rollbackException.getMessage());
				// 回滚失败，记录严重错误
				log.error(
						"CRITICAL: Both vector store update and rollback failed for document id: {}. Manual intervention required.",
						documentId);
			}
		}
		else {
			log.error("Cannot rollback vector store changes for document id: {}: old document is null", documentId);
		}

		// 记录详细的错误信息，以便后续可能的补偿操作
		log.error("Vector store update failed for document id: {}: {}", documentId, originalException.getMessage());

		// 根据回滚是否成功，提供不同的错误消息
		String errorMessage;
		if (rollbackSuccessful) {
			errorMessage = "Failed to update knowledge in vector store. Vector store changes have been rolled back.";
		}
		else {
			errorMessage = "Failed to update knowledge in vector store. Rollback attempt also failed. Manual intervention required.";
		}

		throw new RuntimeException(errorMessage, originalException);
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

		String documentId = DocumentConverterUtil.generateFixedBusinessKnowledgeDocId(knowledge.getAgentId().toString(),
				id);
		vectorStore.delete(List.of(documentId));

		// 从数据库删除记录
		if (businessKnowledgeMapper.deleteById(id) <= 0) {
			// 重新添加修复被删除的记录
			vectorStore.add(List.of(DocumentConverterUtil.convertBusinessKnowledgeToDocument(knowledge)));
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

		// 召回是要添加到向量库，取消召回是从向量库删除
		if (isRecall) {
			knowledge.setIsRecall(1);
			businessKnowledgeMapper.updateById(knowledge);
			vectorStore.add(List.of(DocumentConverterUtil.convertBusinessKnowledgeToDocument(knowledge)));
		}
		else {
			knowledge.setIsRecall(0);
			businessKnowledgeMapper.updateById(knowledge);
			vectorStore.delete(List
				.of(DocumentConverterUtil.generateFixedBusinessKnowledgeDocId(knowledge.getAgentId().toString(), id)));
		}
	}

	@Override
	public void refreshAllKnowledgeToVectorStore(String agentId) throws Exception {
		agentVectorStoreService.deleteDocumentsByVectorType(agentId, DocumentMetadataConstant.BUSINESS_TERM);

		// 获取所有 isRecall 等于 1 的 BusinessKnowledge
		List<BusinessKnowledge> allKnowledge = businessKnowledgeMapper.selectAll();
		List<BusinessKnowledge> recalledKnowledge = allKnowledge.stream()
			.filter(knowledge -> knowledge.getIsRecall() != null && knowledge.getIsRecall() == 1)
			.filter(knowledge -> agentId.equals(knowledge.getAgentId().toString()))
			.toList();

		// 转换为 Document 并插入到 vectorStore
		if (!recalledKnowledge.isEmpty()) {
			List<Document> documents = recalledKnowledge.stream()
				.map(DocumentConverterUtil::convertBusinessKnowledgeToDocument)
				.toList();
			vectorStore.add(documents);
		}
	}

}
