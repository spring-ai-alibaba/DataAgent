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

import com.alibaba.cloud.ai.entity.AgentKnowledge;

import java.util.List;

public interface AgentKnowledgeService {

	List<AgentKnowledge> getKnowledgeByAgentId(Integer agentId);

	AgentKnowledge getKnowledgeById(Integer id);

	boolean createKnowledge(AgentKnowledge knowledge);

	boolean updateKnowledge(Integer id, AgentKnowledge knowledge);

	boolean deleteKnowledge(Integer id);

	List<AgentKnowledge> getKnowledgeByType(Integer agentId, String type);

	List<AgentKnowledge> getKnowledgeByStatus(Integer agentId, String status);

	List<AgentKnowledge> searchKnowledge(Integer agentId, String keyword);

	boolean batchUpdateStatus(List<Integer> ids, String status);

	int countKnowledgeByAgent(Integer agentId);

	List<Object[]> countKnowledgeByType(Integer agentId);

	void addKnowledgeToVectorStore(Long agentId, AgentKnowledge knowledge);

	void deleteKnowledgeFromVectorStore(Long agentId, Integer knowledgeId);

}
