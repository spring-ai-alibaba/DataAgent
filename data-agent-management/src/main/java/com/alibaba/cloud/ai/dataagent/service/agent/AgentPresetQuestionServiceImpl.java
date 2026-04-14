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
package com.alibaba.cloud.ai.dataagent.service.agent;

import com.alibaba.cloud.ai.dataagent.entity.AgentPresetQuestion;
import com.alibaba.cloud.ai.dataagent.mapper.AgentPresetQuestionMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AgentPresetQuestion Service Class
 */
@Service
@AllArgsConstructor
public class AgentPresetQuestionServiceImpl implements AgentPresetQuestionService {

	private final AgentPresetQuestionMapper agentPresetQuestionMapper;

	@Override
	public List<AgentPresetQuestion> findByAgentId(Long agentId) {
		return agentPresetQuestionMapper.selectByAgentId(agentId);
	}

	@Override
	public List<AgentPresetQuestion> findAllByAgentId(Long agentId) {
		return agentPresetQuestionMapper.selectAllByAgentId(agentId);
	}

	@Override
	public AgentPresetQuestion create(AgentPresetQuestion question) {
		// Ensure default values
		if (question.getSortOrder() == null) {
			question.setSortOrder(0);
		}
		if (question.getIsActive() == null) {
			question.setIsActive(true);
		}

		agentPresetQuestionMapper.insert(question);
		return question; // ID will be auto-filled by MyBatis
	}

	@Override
	public void update(Long id, AgentPresetQuestion question) {
		question.setId(id); // Ensure the ID is set
		agentPresetQuestionMapper.update(question);
	}

	@Override
	public void deleteById(Long id) {
		agentPresetQuestionMapper.deleteById(id);
	}

	@Override
	public void deleteByAgentId(Long agentId) {
		agentPresetQuestionMapper.deleteByAgentId(agentId);
	}

	@Override
	public void batchSave(Long agentId, List<AgentPresetQuestion> questions) {
		// Step 1: Delete all existing preset questions for the agent
		deleteByAgentId(agentId);

		// Step 2: Insert new questions with proper order and active status
		for (int i = 0; i < questions.size(); i++) {
			AgentPresetQuestion question = questions.get(i);
			question.setAgentId(agentId);
			question.setSortOrder(i);
			if (question.getIsActive() == null) {
				question.setIsActive(true);
			}
			create(question); // Reuses create() which sets defaults and inserts
		}
	}

}
