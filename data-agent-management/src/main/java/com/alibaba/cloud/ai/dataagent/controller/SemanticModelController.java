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

import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelAddDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelBatchImportDTO;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticModelService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import com.alibaba.cloud.ai.dataagent.vo.BatchImportResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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

	private static final String TEMPLATE_FILE_NAME = "semantic_model_template.xlsx";

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

	/**
	 * 批量删除语义模型
	 */
	@DeleteMapping("/batch")
	public ApiResponse<Boolean> batchDelete(@RequestBody @NotEmpty(message = "ID列表不能为空") List<Long> ids) {
		semanticModelService.deleteSemanticModels(ids);
		return ApiResponse.success("批量删除成功", true);
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

	/**
	 * 批量导入语义模型（JSON格式）
	 */
	@PostMapping("/batch-import")
	public ApiResponse<BatchImportResult> batchImport(@RequestBody @Valid SemanticModelBatchImportDTO dto) {
		log.info("开始批量导入语义模型: agentId={}, 数量={}", dto.getAgentId(), dto.getItems().size());
		BatchImportResult result = semanticModelService.batchImport(dto);
		log.info("批量导入完成: 总数={}, 成功={}, 失败={}", result.getTotal(), result.getSuccessCount(), result.getFailCount());
		return ApiResponse.success("批量导入完成", result);
	}

	@GetMapping("/template/download")
	public ResponseEntity<byte[]> downloadTemplate() {
		try {
			ClassPathResource resource = new ClassPathResource("excel/" + TEMPLATE_FILE_NAME);
			if (!resource.exists()) {
				log.error("模板文件不存在: excel/{}", TEMPLATE_FILE_NAME);
				return ResponseEntity.notFound().build();
			}

			byte[] template;
			try (InputStream inputStream = resource.getInputStream()) {
				template = StreamUtils.copyToByteArray(inputStream);
			}

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
			headers.setContentDispositionFormData("attachment", TEMPLATE_FILE_NAME);
			return ResponseEntity.ok().headers(headers).body(template);
		}
		catch (IOException e) {
			log.error("读取Excel模板失败", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	@PostMapping("/import/excel")
	public ApiResponse<BatchImportResult> importExcel(@RequestParam("file") MultipartFile file,
			@RequestParam("agentId") Long agentId) {
		try {
			BatchImportResult result = semanticModelService.importFromExcel(file, agentId);
			return ApiResponse.success("Excel导入完成", result);
		}
		catch (IllegalArgumentException e) {
			log.error("Excel导入失败: {}", e.getMessage());
			return ApiResponse.error("Excel导入失败: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Excel导入失败", e);
			return ApiResponse.error("Excel导入失败: " + e.getMessage());
		}
	}

}
