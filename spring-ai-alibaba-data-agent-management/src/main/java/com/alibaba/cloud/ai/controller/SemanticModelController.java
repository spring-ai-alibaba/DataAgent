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

import com.alibaba.cloud.ai.entity.SemanticModel;
import com.alibaba.cloud.ai.service.semantic.SemanticModelService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Semantic Model Configuration Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/semantic-model")
@CrossOrigin(origins = "*")
@AllArgsConstructor
// todo: 完成后与前端对接
public class SemanticModelController {

	private final SemanticModelService semanticModelService;

	@GetMapping
	public ResponseEntity<List<SemanticModel>> list(@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "agentId", required = false) Long agentId) {
		List<SemanticModel> result;
		if (keyword != null && !keyword.trim().isEmpty()) {
			result = semanticModelService.search(keyword);
		}
		else if (agentId != null) {
			result = semanticModelService.getByAgentId(agentId);
		}
		else {
			result = semanticModelService.getAll();
		}
		return ResponseEntity.ok(result);
	}

	@GetMapping("/{id}")
	public ResponseEntity<SemanticModel> get(@PathVariable(value = "id") Long id) {
		SemanticModel model = semanticModelService.getById(id);
		if (model == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(model);
	}

	@PostMapping
	public ResponseEntity<SemanticModel> create(@RequestBody SemanticModel model) {
		semanticModelService.addSemanticModel(model);
		return ResponseEntity.ok(model);
	}

	@PutMapping("/{id}")
	public ResponseEntity<SemanticModel> update(@PathVariable(value = "id") Long id, @RequestBody SemanticModel model) {
		if (semanticModelService.getById(id) == null) {
			return ResponseEntity.notFound().build();
		}
		model.setId(id);
		semanticModelService.updateSemanticModel(id, model);
		return ResponseEntity.ok(model);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable(value = "id") Long id) {
		if (semanticModelService.getById(id) == null) {
			return ResponseEntity.notFound().build();
		}
		semanticModelService.deleteSemanticModel(id);
		return ResponseEntity.ok().build();
	}

	// Enable
	@PutMapping("/enable")
	public ResponseEntity<Void> enableFields(@RequestBody List<Long> ids) {
		semanticModelService.enableSemanticModels(ids);
		return ResponseEntity.ok().build();
	}

	// Disable
	@PutMapping("/disable")
	public ResponseEntity<Void> disableFields(@RequestBody List<Long> ids) {
		semanticModelService.deleteSemanticModels(ids);
		return ResponseEntity.ok().build();
	}

}
