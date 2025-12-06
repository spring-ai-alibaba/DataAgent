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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.SemanticModelAddDTO;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticModelService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
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
public class SemanticModelController {

	private final SemanticModelService semanticModelService;

	@GetMapping
	public ApiResponse<List<SemanticModel>> list(@RequestParam(value = "keyword", required = false) String keyword,
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
		return ApiResponse.success("success list semanticModel", result);
	}

	@GetMapping("/{id}")
	public ApiResponse<SemanticModel> get(@PathVariable(value = "id") Long id) {
		SemanticModel model = semanticModelService.getById(id);
		return ApiResponse.success("success retrieve semanticModel", model);
	}

	@PostMapping
	public ApiResponse<Boolean> create(@RequestBody @Validated SemanticModelAddDTO semanticModelAddDto) {
		boolean success = semanticModelService.addSemanticModel(semanticModelAddDto);
		if (success) {
			return ApiResponse.success("Semantic model created successfully", true);
		}
		else {
			return ApiResponse.error("Failed to create semantic model");
		}
	}

	@PutMapping("/{id}")
	public ApiResponse<SemanticModel> update(@PathVariable(value = "id") Long id, @RequestBody SemanticModel model) {
		if (semanticModelService.getById(id) == null) {
			return ApiResponse.error("Semantic model not found");
		}
		model.setId(id);
		semanticModelService.updateSemanticModel(id, model);
		return ApiResponse.success("Semantic model updated successfully", model);
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Boolean> delete(@PathVariable(value = "id") Long id) {
		if (semanticModelService.getById(id) == null) {
			return ApiResponse.error("Semantic model not found");
		}
		semanticModelService.deleteSemanticModel(id);
		return ApiResponse.success("Semantic model deleted successfully", true);
	}

	// Enable
	@PutMapping("/enable")
	public ApiResponse<Boolean> enableFields(@RequestBody @NotEmpty(message = "ID列表不能为空") List<Long> ids) {
		semanticModelService.enableSemanticModels(ids);
		return ApiResponse.success("Semantic models enabled successfully", true);
	}

	// Disable
	@PutMapping("/disable")
	public ApiResponse<Boolean> disableFields(@RequestBody @NotEmpty(message = "ID列表不能为空") List<Long> ids) {
		ids.forEach(semanticModelService::disableSemanticModel);
		return ApiResponse.success("Semantic models disabled successfully", true);
	}

}
