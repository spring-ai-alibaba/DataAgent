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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.skill.UpdateAgentSkillsRequest;
import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.service.agent.AgentService;
import com.alibaba.cloud.ai.dataagent.service.skill.AgentSkillBindingService;
import com.alibaba.cloud.ai.dataagent.service.skill.LocalSkillService;
import com.alibaba.cloud.ai.dataagent.vo.AgentSkillConfigVO;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/agent/{agentId}/skills")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AgentSkillController {

	private final AgentService agentService;

	private final AgentSkillBindingService agentSkillBindingService;

	private final LocalSkillService localSkillService;

	@GetMapping
	public AgentSkillConfigVO getAgentSkillConfig(@PathVariable Long agentId) {
		ensureAgentExists(agentId);
		return new AgentSkillConfigVO(localSkillService.getStoragePath().toString(),
				agentSkillBindingService.listSkillIdsByAgentId(agentId), localSkillService.listSkills());
	}

	@PutMapping
	@ResponseStatus(HttpStatus.OK)
	public AgentSkillConfigVO updateAgentSkillConfig(@PathVariable Long agentId,
			@RequestBody UpdateAgentSkillsRequest request) {
		ensureAgentExists(agentId);
		List<String> normalizedSkillIds = request == null || request.skillIds() == null ? List.of()
				: new LinkedHashSet<>(request.skillIds()).stream().toList();
		normalizedSkillIds.forEach(skillId -> {
			if (!localSkillService.exists(skillId)) {
				throw new IllegalArgumentException("Skill not found: " + skillId);
			}
		});
		agentSkillBindingService.replaceAgentSkills(agentId, normalizedSkillIds);
		return getAgentSkillConfig(agentId);
	}

	private void ensureAgentExists(Long agentId) {
		Agent agent = agentService.findById(agentId);
		if (agent == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "agent with id: %d not found".formatted(agentId));
		}
	}

}
