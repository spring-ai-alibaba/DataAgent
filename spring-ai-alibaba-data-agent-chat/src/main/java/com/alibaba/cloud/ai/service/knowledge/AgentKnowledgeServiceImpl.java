/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.service.knowledge;

import com.alibaba.cloud.ai.constant.Constant;
import com.alibaba.cloud.ai.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.entity.AgentKnowledge;
import com.alibaba.cloud.ai.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.service.vectorstore.AgentVectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.util.DocumentConverterUtil.createDocumentFromKnowledge;

@Slf4j
@Service
public class AgentKnowledgeServiceImpl implements AgentKnowledgeService {

	private final AgentKnowledgeMapper agentKnowledgeMapper;

	private final AgentVectorStoreService vectorStoreService;

	public AgentKnowledgeServiceImpl(AgentKnowledgeMapper agentKnowledgeMapper,
			AgentVectorStoreService vectorStoreService) {
		this.agentKnowledgeMapper = agentKnowledgeMapper;
		this.vectorStoreService = vectorStoreService;
	}

	@Override
	public List<AgentKnowledge> getKnowledgeByAgentId(Integer agentId) {
		return agentKnowledgeMapper.selectByAgentId(agentId);
	}

	@Override
	public AgentKnowledge getKnowledgeById(Integer id) {
		return agentKnowledgeMapper.selectById(id);
	}

	@Override
	public boolean createKnowledge(AgentKnowledge knowledge) {
		LocalDateTime now = LocalDateTime.now();

		// Set default values
		if (knowledge.getType() == null) {
			knowledge.setType("document");
		}
		if (knowledge.getStatus() == null) {
			knowledge.setStatus("active");
		}
		if (knowledge.getEmbeddingStatus() == null) {
			knowledge.setEmbeddingStatus("pending");
		}

		// Set creation and update time
		knowledge.setCreateTime(now);
		knowledge.setUpdateTime(now);

		// Insert into database, the ID will be auto-filled by MyBatis
		return agentKnowledgeMapper.insert(knowledge) > 0;
	}

	@Override
	public boolean updateKnowledge(Integer id, AgentKnowledge knowledge) {
		LocalDateTime now = LocalDateTime.now();

		// Ensure the knowledge object has the correct ID
		knowledge.setId(id);
		knowledge.setUpdateTime(now);

		int updatedRows = agentKnowledgeMapper.update(knowledge);
		return updatedRows > 0;
	}

	@Override
	public boolean deleteKnowledge(Integer id) {
		int deletedRows = agentKnowledgeMapper.deleteById(id);
		return deletedRows > 0;
	}

	@Override
	public List<AgentKnowledge> getKnowledgeByType(Integer agentId, String type) {
		return agentKnowledgeMapper.selectByAgentIdAndType(agentId, type);
	}

	@Override
	public List<AgentKnowledge> getKnowledgeByStatus(Integer agentId, String status) {
		return agentKnowledgeMapper.selectByAgentIdAndStatus(agentId, status);
	}

	@Override
	public List<AgentKnowledge> searchKnowledge(Integer agentId, String keyword) {
		return agentKnowledgeMapper.searchByAgentIdAndKeyword(agentId, keyword);
	}

	@Override
	public boolean batchUpdateStatus(List<Integer> ids, String status) {
		LocalDateTime now = LocalDateTime.now();

		int totalUpdated = 0;
		for (Integer id : ids) {
			totalUpdated += agentKnowledgeMapper.updateStatus(id, status, now);
		}
		return totalUpdated == ids.size();
	}

	@Override
	public int countKnowledgeByAgent(Integer agentId) {
		return agentKnowledgeMapper.countByAgentId(agentId);
	}

	@Override
	public List<Object[]> countKnowledgeByType(Integer agentId) {
		return agentKnowledgeMapper.countByType(agentId);
	}

	@Override
	public void addKnowledgeToVectorStore(Long agentId, AgentKnowledge knowledge) {
		try {
			String agentIdStr = String.valueOf(agentId);
			log.info("Adding knowledge to vector store for agent: {}, knowledge ID: {}", agentIdStr, knowledge.getId());

			// Create document
			Document document = createDocumentFromKnowledge(agentIdStr, knowledge);

			// Add to vector store
			vectorStoreService.addDocuments(agentIdStr, List.of(document));

			log.info("Successfully added knowledge to vector store for agent: {}", agentIdStr);
		}
		catch (Exception e) {
			log.error("Failed to add knowledge to vector store for agent: {}, knowledge ID: {}", agentId,
					knowledge.getId(), e);
			throw new RuntimeException("Failed to add knowledge to vector store: " + e.getMessage(), e);
		}
	}

	@Override
	public void deleteKnowledgeFromVectorStore(Long agentId, Integer knowledgeId) {
		log.info("Deleting knowledge from vector store for agent: {}, knowledge ID: {}", agentId, knowledgeId);
		try {
			String agentIdStr = String.valueOf(agentId);
			Map<String, Object> metadata = new HashMap<>(Map.ofEntries(Map.entry(Constant.AGENT_ID, agentIdStr),
					Map.entry(DocumentMetadataConstant.KNOWLEDGE_ID, knowledgeId)));

			vectorStoreService.deleteDocumentsByMetedata(agentIdStr, metadata);

			log.info("Successfully deleted knowledge from vector store for agent: {}", agentIdStr);
		}
		catch (Exception e) {
			log.error("Failed to delete knowledge from vector store for agent: {}, knowledge ID: {}", agentId,
					knowledgeId, e);
			throw new RuntimeException("Failed to delete knowledge from vector store: " + e.getMessage(), e);
		}
	}

}
