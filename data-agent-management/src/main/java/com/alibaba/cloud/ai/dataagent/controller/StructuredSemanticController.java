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

import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticColumnQueryDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticColumnUpsertDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticRelationQueryDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticRelationUpsertDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticTableQueryDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticTableUpsertDTO;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticColumn;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticRelation;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticTable;
import com.alibaba.cloud.ai.dataagent.service.semantic.StructuredSemanticService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import com.alibaba.cloud.ai.dataagent.vo.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/semantic")
@RequiredArgsConstructor
public class StructuredSemanticController {

	private final StructuredSemanticService structuredSemanticService;

	@GetMapping("/tables")
	public PageResponse<List<SemanticTable>> queryTables(@Valid SemanticTableQueryDTO queryDTO) {
		return structuredSemanticService.queryTables(queryDTO);
	}

	@PostMapping("/tables")
	public ApiResponse<SemanticTable> createTable(@Valid @RequestBody SemanticTableUpsertDTO dto) {
		return ApiResponse.success("表语义创建成功", structuredSemanticService.saveTable(null, dto));
	}

	@PutMapping("/tables/{id}")
	public ApiResponse<SemanticTable> updateTable(@PathVariable Long id, @Valid @RequestBody SemanticTableUpsertDTO dto) {
		return ApiResponse.success("表语义更新成功", structuredSemanticService.saveTable(id, dto));
	}

	@DeleteMapping("/tables/{id}")
	public ApiResponse<Boolean> deleteTable(@PathVariable Long id) {
		structuredSemanticService.deleteTable(id);
		return ApiResponse.success("表语义删除成功", true);
	}

	@GetMapping("/columns")
	public PageResponse<List<SemanticColumn>> queryColumns(@Valid SemanticColumnQueryDTO queryDTO) {
		return structuredSemanticService.queryColumns(queryDTO);
	}

	@PostMapping("/columns")
	public ApiResponse<SemanticColumn> createColumn(@Valid @RequestBody SemanticColumnUpsertDTO dto) {
		return ApiResponse.success("列语义创建成功", structuredSemanticService.saveColumn(null, dto));
	}

	@PutMapping("/columns/{id}")
	public ApiResponse<SemanticColumn> updateColumn(@PathVariable Long id,
			@Valid @RequestBody SemanticColumnUpsertDTO dto) {
		return ApiResponse.success("列语义更新成功", structuredSemanticService.saveColumn(id, dto));
	}

	@DeleteMapping("/columns/{id}")
	public ApiResponse<Boolean> deleteColumn(@PathVariable Long id) {
		structuredSemanticService.deleteColumn(id);
		return ApiResponse.success("列语义删除成功", true);
	}

	@GetMapping("/relations")
	public PageResponse<List<SemanticRelation>> queryRelations(@Valid SemanticRelationQueryDTO queryDTO) {
		return structuredSemanticService.queryRelations(queryDTO);
	}

	@PostMapping("/relations")
	public ApiResponse<SemanticRelation> createRelation(@Valid @RequestBody SemanticRelationUpsertDTO dto) {
		return ApiResponse.success("关系语义创建成功", structuredSemanticService.saveRelation(null, dto));
	}

	@PutMapping("/relations/{id}")
	public ApiResponse<SemanticRelation> updateRelation(@PathVariable Long id,
			@Valid @RequestBody SemanticRelationUpsertDTO dto) {
		return ApiResponse.success("关系语义更新成功", structuredSemanticService.saveRelation(id, dto));
	}

	@DeleteMapping("/relations/{id}")
	public ApiResponse<Boolean> deleteRelation(@PathVariable Long id) {
		structuredSemanticService.deleteRelation(id);
		return ApiResponse.success("关系语义删除成功", true);
	}

}
