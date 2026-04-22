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

import com.alibaba.cloud.ai.dataagent.dto.skill.SaveLocalSkillRequest;
import com.alibaba.cloud.ai.dataagent.service.skill.AgentSkillBindingService;
import com.alibaba.cloud.ai.dataagent.service.skill.LocalSkillService;
import com.alibaba.cloud.ai.dataagent.vo.LocalSkillDetailVO;
import com.alibaba.cloud.ai.dataagent.vo.LocalSkillSummaryVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SkillController {

	private final LocalSkillService localSkillService;

	private final AgentSkillBindingService agentSkillBindingService;

	@GetMapping
	public List<LocalSkillSummaryVO> listSkills() {
		return localSkillService.listSkills();
	}

	@GetMapping("/{skillId}")
	public LocalSkillDetailVO getSkill(@PathVariable String skillId) {
		return localSkillService.getSkill(skillId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public LocalSkillDetailVO createSkill(@RequestBody SaveLocalSkillRequest request) {
		return localSkillService.createSkill(request);
	}

	@PutMapping("/{skillId}")
	public LocalSkillDetailVO updateSkill(@PathVariable String skillId, @RequestBody SaveLocalSkillRequest request) {
		return localSkillService.updateSkill(skillId, request);
	}

	@DeleteMapping("/{skillId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteSkill(@PathVariable String skillId) {
		localSkillService.deleteSkill(skillId);
		agentSkillBindingService.deleteBySkillId(skillId);
	}

}
