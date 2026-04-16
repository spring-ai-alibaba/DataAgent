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
package com.alibaba.cloud.ai.dataagent.service.skill.impl;

import com.alibaba.cloud.ai.dataagent.mapper.AgentSkillBindingMapper;
import com.alibaba.cloud.ai.dataagent.service.skill.AgentSkillBindingService;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentSkillBindingServiceImpl implements AgentSkillBindingService {

	private final AgentSkillBindingMapper agentSkillBindingMapper;

	@Override
	public List<String> listSkillIdsByAgentId(Long agentId) {
		if (agentId == null) {
			return List.of();
		}
		return agentSkillBindingMapper.findByAgentId(agentId).stream().map(binding -> binding.getSkillId()).toList();
	}

	@Override
	@Transactional
	public void replaceAgentSkills(Long agentId, List<String> skillIds) {
		if (agentId == null) {
			throw new IllegalArgumentException("Agent ID cannot be null");
		}
		agentSkillBindingMapper.deleteByAgentId(agentId);
		List<String> normalized = skillIds == null ? List.of() : new LinkedHashSet<>(skillIds).stream().toList();
		if (!normalized.isEmpty()) {
			agentSkillBindingMapper.batchInsert(agentId, normalized);
		}
	}

	@Override
	@Transactional
	public void deleteBySkillId(String skillId) {
		agentSkillBindingMapper.deleteBySkillId(skillId);
	}

}
