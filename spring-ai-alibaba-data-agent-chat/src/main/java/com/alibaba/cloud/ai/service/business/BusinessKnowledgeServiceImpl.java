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

import com.alibaba.cloud.ai.dto.BusinessKnowledgeDTO;
import com.alibaba.cloud.ai.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.mapper.BusinessKnowledgeMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BusinessKnowledgeServiceImpl implements BusinessKnowledgeService {

	private final BusinessKnowledgeMapper businessKnowledgeMapper;

	public BusinessKnowledgeServiceImpl(BusinessKnowledgeMapper businessKnowledgeMapper) {
		this.businessKnowledgeMapper = businessKnowledgeMapper;
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
	public Long addKnowledge(BusinessKnowledgeDTO knowledgeDTO) {
		BusinessKnowledge entity = knowledgeDTO.toEntity();
		if (businessKnowledgeMapper.insert(entity) > 0) {
			return entity.getId();
		}
		throw new RuntimeException("Failed to add knowledge");
	}

	@Override
	public void updateKnowledge(Long id, BusinessKnowledgeDTO knowledgeDTO) {
		BusinessKnowledge entity = knowledgeDTO.toEntity();
		entity.setId(id);
		if (businessKnowledgeMapper.updateById(entity) > 0) {
			return;
		}
		throw new RuntimeException("Failed to update knowledge");
	}

	@Override
	public void deleteKnowledge(Long id) {
		if (businessKnowledgeMapper.deleteById(id) > 0) {
			return;
		}
		throw new RuntimeException("Failed to delete knowledge");
	}

	@Override
	public void recallKnowledge(Long id, boolean isRecall) {
		if (businessKnowledgeMapper.changeRecall(id, isRecall) > 0) {
			return;
		}
		throw new RuntimeException("Failed to change recall status");
	}

}
