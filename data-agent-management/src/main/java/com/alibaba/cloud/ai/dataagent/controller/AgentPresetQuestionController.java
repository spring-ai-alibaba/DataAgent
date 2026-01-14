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

import com.alibaba.cloud.ai.dataagent.entity.AgentPresetQuestion;
import com.alibaba.cloud.ai.dataagent.service.agent.AgentPresetQuestionService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
@AllArgsConstructor
// todo: 部分返回值和参数需要定义DTO
public class AgentPresetQuestionController {

	private final AgentPresetQuestionService presetQuestionService;

	/**
	 * Get preset question list of agent
	 */
	@GetMapping("/{agentId}/preset-questions")
	public ResponseEntity<List<AgentPresetQuestion>> getPresetQuestions(@PathVariable(value = "agentId") Long agentId) {
		try {
			List<AgentPresetQuestion> questions = presetQuestionService.findAllByAgentId(agentId);
			return ResponseEntity.ok(questions);
		}
		catch (Exception e) {
			log.error("Error getting preset questions for agent {}", agentId, e);
			return ResponseEntity.internalServerError().build();
		}
	}

	/**
	 * Batch save preset questions of agent
	 */
	@PostMapping("/{agentId}/preset-questions")
	public ResponseEntity<Map<String, String>> savePresetQuestions(@PathVariable(value = "agentId") Long agentId,
			@RequestBody List<Map<String, Object>> questionsData) {
		try {
			List<AgentPresetQuestion> questions = questionsData.stream().map(data -> {
				AgentPresetQuestion question = new AgentPresetQuestion();
				question.setQuestion((String) data.get("question"));
				Object isActiveObj = data.get("isActive");
				if (isActiveObj instanceof Boolean) {
					question.setIsActive((Boolean) isActiveObj);
				}
				else if (isActiveObj != null) {
					question.setIsActive(Boolean.parseBoolean(isActiveObj.toString()));
				}
				else {
					question.setIsActive(true);
				}
				return question;
			}).toList();

			presetQuestionService.batchSave(agentId, questions);
			return ResponseEntity.ok(Map.of("message", "预设问题保存成功"));
		}
		catch (Exception e) {
			log.error("Error saving preset questions for agent {}", agentId, e);
			return ResponseEntity.internalServerError().body(Map.of("error", "保存预设问题失败: " + e.getMessage()));
		}
	}

	/**
	 * Delete preset question
	 */
	@DeleteMapping("/{agentId}/preset-questions/{questionId}")
	public ResponseEntity<Map<String, String>> deletePresetQuestion(@PathVariable(value = "agentId") Long agentId,
			@PathVariable Long questionId) {
		try {
			presetQuestionService.deleteById(questionId);
			return ResponseEntity.ok(Map.of("message", "预设问题删除成功"));
		}
		catch (Exception e) {
			log.error("Error deleting preset question {} for agent {}", questionId, agentId, e);
			return ResponseEntity.internalServerError().body(Map.of("error", "删除预设问题失败: " + e.getMessage()));
		}
	}

}
