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
package com.alibaba.cloud.ai.dataagent.service.skill;

import com.alibaba.cloud.ai.dataagent.dto.skill.SaveLocalSkillRequest;
import com.alibaba.cloud.ai.dataagent.vo.LocalSkillDetailVO;
import com.alibaba.cloud.ai.dataagent.vo.LocalSkillSummaryVO;
import io.agentscope.core.skill.AgentSkill;
import java.nio.file.Path;
import java.util.List;

public interface LocalSkillService {

	String BUILTIN_CURRENT_TIME_SKILL_ID = "builtin-current-time";

	String BUILTIN_DOMAIN_BUSINESS_KNOWLEDGE_SKILL_ID = "builtin-domain-business-knowledge";

	List<LocalSkillSummaryVO> listSkills();

	LocalSkillDetailVO getSkill(String skillId);

	LocalSkillDetailVO createSkill(SaveLocalSkillRequest request);

	LocalSkillDetailVO updateSkill(String skillId, SaveLocalSkillRequest request);

	void deleteSkill(String skillId);

	List<AgentSkill> loadAgentSkills(List<String> skillIds);

	boolean exists(String skillId);

	boolean isBuiltinSkill(String skillId);

	Path getStoragePath();

}
