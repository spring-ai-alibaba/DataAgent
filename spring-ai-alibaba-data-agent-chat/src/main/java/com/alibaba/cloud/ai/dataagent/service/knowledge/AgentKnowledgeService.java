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

package com.alibaba.cloud.ai.dataagent.service.knowledge;

import com.alibaba.cloud.ai.dataagent.dto.PageResult;
import com.alibaba.cloud.ai.dataagent.dto.agentknowledge.AgentKnowledgeQueryDTO;
import com.alibaba.cloud.ai.dataagent.dto.agentknowledge.CreateKnowledgeDto;
import com.alibaba.cloud.ai.dataagent.dto.agentknowledge.UpdateKnowledgeDto;
import com.alibaba.cloud.ai.dataagent.vo.AgentKnowledgeVO;

public interface AgentKnowledgeService {

	AgentKnowledgeVO getKnowledgeById(Integer id);

	AgentKnowledgeVO createKnowledge(CreateKnowledgeDto createKnowledgeDto);

	AgentKnowledgeVO updateKnowledge(Integer id, UpdateKnowledgeDto updateKnowledgeDto);

	boolean deleteKnowledge(Integer id);

	PageResult<AgentKnowledgeVO> queryByConditionsWithPage(AgentKnowledgeQueryDTO queryDTO);

	AgentKnowledgeVO updateKnowledgeRecallStatus(Integer id, Boolean recalled);

	void retryEmbedding(Integer id);

}
