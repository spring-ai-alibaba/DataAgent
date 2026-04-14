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

import java.util.List;

public interface AgentPresetQuestionService {

	/**
	 * Get the list of preset questions by agent ID (only active ones, ordered by
	 * sort_order and id)
	 */
	List<AgentPresetQuestion> findByAgentId(Long agentId);

	/**
	 * Get all preset questions by agent ID (including inactive ones, ordered by
	 * sort_order and id)
	 */
	List<AgentPresetQuestion> findAllByAgentId(Long agentId);

	/**
	 * Create a new preset question
	 */
	AgentPresetQuestion create(AgentPresetQuestion question);

	/**
	 * Update an existing preset question
	 */
	void update(Long id, AgentPresetQuestion question);

	/**
	 * Delete a preset question by ID
	 */
	void deleteById(Long id);

	/**
	 * Delete all preset questions for a given agent
	 */
	void deleteByAgentId(Long agentId);

	/**
	 * Batch save preset questions: delete all existing ones for the agent, then insert
	 * the new list
	 */
	void batchSave(Long agentId, List<AgentPresetQuestion> questions);

}
