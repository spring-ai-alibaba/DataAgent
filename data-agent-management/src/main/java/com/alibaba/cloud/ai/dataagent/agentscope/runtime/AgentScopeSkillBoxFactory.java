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
package com.alibaba.cloud.ai.dataagent.agentscope.runtime;

import com.alibaba.cloud.ai.dataagent.agentscope.tool.skill.SkillBoundToolCatalogService;
import com.alibaba.cloud.ai.dataagent.service.skill.AgentSkillBindingService;
import com.alibaba.cloud.ai.dataagent.service.skill.LocalSkillService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AgentScopeSkillBoxFactory {

	private final AgentSkillBindingService agentSkillBindingService;

	private final LocalSkillService localSkillService;

	private final SkillBoundToolCatalogService skillBoundToolCatalogService;

	private final ObjectMapper objectMapper;

	public SkillBox create(String agentId, Toolkit toolkit) {
		if (!StringUtils.hasText(agentId) || toolkit == null) {
			return null;
		}
		Long numericAgentId = parseNumericAgentId(agentId);
		if (numericAgentId == null) {
			return null;
		}
		List<String> enabledSkillIds = agentSkillBindingService.listSkillIdsByAgentId(numericAgentId);
		if (enabledSkillIds.isEmpty()) {
			return null;
		}
		List<AgentSkill> skills = localSkillService.loadAgentSkills(enabledSkillIds);
		if (skills.isEmpty()) {
			return null;
		}
		SkillBox skillBox = new SkillBox(toolkit);
		for (AgentSkill skill : skills) {
			Map<String, ToolCallback> skillTools = skillBoundToolCatalogService.getToolCallbacks(agentId,
					skill.getSkillId());
			if (skillTools.isEmpty()) {
				skillBox.registration().skill(skill).apply();
				continue;
			}
			skillTools.values()
				.forEach(toolCallback -> skillBox.registration()
					.skill(skill)
					.toolkit(toolkit)
					.agentTool(new SpringToolCallbackAgentAdapter(toolCallback, objectMapper))
					.apply());
		}
		return skillBox;
	}

	private Long parseNumericAgentId(String agentId) {
		try {
			return Long.valueOf(agentId);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
