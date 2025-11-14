/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.controller;

import com.alibaba.cloud.ai.dto.BusinessKnowledgeDTO;
import com.alibaba.cloud.ai.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.service.business.BusinessKnowledgeService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// TODO 需要优化返回结果为ApiResponse
@RestController
@RequestMapping("/api/business-knowledge")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class BusinessKnowledgeController {

	private final BusinessKnowledgeService businessKnowledgeService;

	@GetMapping
	public ResponseEntity<List<BusinessKnowledge>> list(
			@RequestParam(value = "agentId", required = false) String agentIdStr,
			@RequestParam(value = "keyword", required = false) String keyword) {
		List<BusinessKnowledge> result;
		Long agentId = null;
		try {
			agentId = Long.parseLong(agentIdStr);
		}
		catch (Exception e) {
			// ignore
		}
		if (StringUtils.hasText(keyword) && agentId != null) {
			result = businessKnowledgeService.searchKnowledge(agentId, keyword);
		}
		else if (agentId != null) {
			result = businessKnowledgeService.getKnowledge(agentId);
		}
		else {
			result = businessKnowledgeService.getAllKnowledge();
		}
		return ResponseEntity.ok(result);
	}

	@GetMapping("/{id}")
	public ResponseEntity<BusinessKnowledge> get(@PathVariable(value = "id") Long id) {
		BusinessKnowledge knowledge = businessKnowledgeService.getKnowledgeById(id);
		if (knowledge == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(knowledge);
	}

	@PostMapping
	public ResponseEntity<BusinessKnowledge> create(@RequestBody @Validated BusinessKnowledgeDTO knowledge) {
		Long id = businessKnowledgeService.addKnowledge(knowledge);
		return this.get(id);
	}

	@PutMapping("/{id}")
	public ResponseEntity<BusinessKnowledge> update(@PathVariable(value = "id") Long id,
			@RequestBody BusinessKnowledgeDTO knowledge) {
		if (businessKnowledgeService.getKnowledgeById(id) == null) {
			return ResponseEntity.notFound().build();
		}
		businessKnowledgeService.updateKnowledge(id, knowledge);
		return ResponseEntity.ok(businessKnowledgeService.getKnowledgeById(id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable(value = "id") Long id) {
		if (businessKnowledgeService.getKnowledgeById(id) == null) {
			return ResponseEntity.notFound().build();
		}
		businessKnowledgeService.deleteKnowledge(id);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/recall/{id}")
	public ResponseEntity<Boolean> recallKnowledge(@PathVariable(value = "id") Long id,
			@RequestParam(value = "isRecall") boolean isRecall) {
		businessKnowledgeService.recallKnowledge(id, isRecall);
		return ResponseEntity.ok().body(true);
	}

}
