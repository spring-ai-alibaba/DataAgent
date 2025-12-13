/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.schema.CreateLogicalRelationDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.UpdateLogicalRelationDTO;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// todo: 不要吞掉所有异常，可以直接抛出，写一个Advice拦截异常并做日志
@Slf4j
@RestController
@RequestMapping("/api/datasource")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class DatasourceController {

	private final DatasourceService datasourceService;

	/**
	 * Get all data source list
	 */
	@GetMapping
	public ResponseEntity<List<Datasource>> getAllDatasource(
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "type", required = false) String type) {

		List<Datasource> datasources;

		if (status != null && !status.isEmpty()) {
			datasources = datasourceService.getDatasourceByStatus(status);
		}
		else if (type != null && !type.isEmpty()) {
			datasources = datasourceService.getDatasourceByType(type);
		}
		else {
			datasources = datasourceService.getAllDatasource();
		}

		return ResponseEntity.ok(datasources);
	}

	/**
	 * Get data source details by ID
	 */
	@GetMapping("/{id}")
	public ResponseEntity<Datasource> getDatasourceById(@PathVariable(value = "id") Integer id) {
		Datasource datasource = datasourceService.getDatasourceById(id);
		if (datasource != null) {
			return ResponseEntity.ok(datasource);
		}
		else {
			return ResponseEntity.notFound().build();
		}
	}

	@GetMapping("/{id}/tables")
	public ResponseEntity<List<String>> getDatasourceTables(@PathVariable(value = "id") Integer id) {
		try {
			List<String> tables = datasourceService.getDatasourceTables(id);
			return ResponseEntity.ok(tables);
		}
		catch (Exception e) {
			return ResponseEntity.badRequest().build();
		}
	}

	/**
	 * Create data source
	 */
	@PostMapping
	public ResponseEntity<Datasource> createDatasource(@RequestBody Datasource datasource) {
		try {
			Datasource created = datasourceService.createDatasource(datasource);
			return ResponseEntity.ok(created);
		}
		catch (Exception e) {
			return ResponseEntity.badRequest().build();
		}
	}

	/**
	 * Update data source
	 */
	@PutMapping("/{id}")
	public ResponseEntity<Datasource> updateDatasource(@PathVariable(value = "id") Integer id,
			@RequestBody Datasource datasource) {
		try {
			Datasource updated = datasourceService.updateDatasource(id, datasource);
			return ResponseEntity.ok(updated);
		}
		catch (Exception e) {
			return ResponseEntity.badRequest().build();
		}
	}

	/**
	 * Delete data source
	 */
	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse> deleteDatasource(@PathVariable(value = "id") Integer id) {
		try {
			datasourceService.deleteDatasource(id);
			return ResponseEntity.ok(ApiResponse.success("数据源删除成功"));
		}
		catch (Exception e) {
			return ResponseEntity.badRequest().body(ApiResponse.error("删除失败：" + e.getMessage()));
		}
	}

	/**
	 * Test data source connection
	 */
	@PostMapping("/{id}/test")
	public ResponseEntity<ApiResponse> testConnection(@PathVariable(value = "id") Integer id) {
		try {
			boolean success = datasourceService.testConnection(id);
			ApiResponse response = success ? ApiResponse.success("连接测试成功") : ApiResponse.error("连接测试失败");
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			return ResponseEntity.badRequest().body(ApiResponse.error("测试失败：" + e.getMessage()));
		}
	}

	/**
	 * 获取数据源表的字段列表
	 */
	@GetMapping("/{id}/tables/{tableName}/columns")
	public ApiResponse<List<String>> getTableColumns(@PathVariable(value = "id") Integer id,
			@PathVariable(value = "tableName") String tableName) {
		try {
			List<String> columns = datasourceService.getTableColumns(id, tableName);
			return ApiResponse.success("获取字段列表成功", columns);
		}
		catch (Exception e) {
			return ApiResponse.error("获取字段列表失败：" + e.getMessage());
		}
	}

	/**
	 * 获取数据源的逻辑外键列表
	 */
	@GetMapping("/{id}/logical-relations")
	public ApiResponse<List<LogicalRelation>> getLogicalRelations(@PathVariable(value = "id") Integer datasourceId) {
		try {
			List<LogicalRelation> logicalRelations = datasourceService.getLogicalRelations(datasourceId);
			return ApiResponse.success("success get logical relations", logicalRelations);
		}
		catch (Exception e) {
			log.error("Failed to get logical relations for datasource: {}", datasourceId, e);
			return ApiResponse.error("获取逻辑外键失败：" + e.getMessage());
		}
	}

	/**
	 * 添加逻辑外键
	 */
	@PostMapping("/{id}/logical-relations")
	public ApiResponse<LogicalRelation> addLogicalRelation(@PathVariable(value = "id") Integer datasourceId,
			@Valid @RequestBody CreateLogicalRelationDTO dto) {
		try {
			LogicalRelation logicalRelation = LogicalRelation.builder()
				.sourceTableName(dto.getSourceTableName())
				.sourceColumnName(dto.getSourceColumnName())
				.targetTableName(dto.getTargetTableName())
				.targetColumnName(dto.getTargetColumnName())
				.relationType(dto.getRelationType())
				.description(dto.getDescription())
				.build();

			LogicalRelation created = datasourceService.addLogicalRelation(datasourceId, logicalRelation);
			return ApiResponse.success("success create logical relation", created);
		}
		catch (Exception e) {
			log.error("Failed to add logical relation for datasource: {}", datasourceId, e);
			return ApiResponse.error("添加逻辑外键失败：" + e.getMessage());
		}
	}

	/**
	 * 更新逻辑外键
	 */
	@PutMapping("/{id}/logical-relations/{relationId}")
	public ApiResponse<LogicalRelation> updateLogicalRelation(@PathVariable(value = "id") Integer datasourceId,
			@PathVariable(value = "relationId") Integer relationId, @RequestBody UpdateLogicalRelationDTO dto) {
		try {
			LogicalRelation logicalRelation = LogicalRelation.builder()
				.sourceTableName(dto.getSourceTableName())
				.sourceColumnName(dto.getSourceColumnName())
				.targetTableName(dto.getTargetTableName())
				.targetColumnName(dto.getTargetColumnName())
				.relationType(dto.getRelationType())
				.description(dto.getDescription())
				.build();

			LogicalRelation updated = datasourceService.updateLogicalRelation(datasourceId, relationId,
					logicalRelation);
			return ApiResponse.success("success update logical relation", updated);
		}
		catch (Exception e) {
			log.error("Failed to update logical relation {} for datasource: {}", relationId, datasourceId, e);
			return ApiResponse.error("更新逻辑外键失败：" + e.getMessage());
		}
	}

	/**
	 * 删除逻辑外键
	 */
	@DeleteMapping("/{id}/logical-relations/{relationId}")
	public ApiResponse<Void> deleteLogicalRelation(@PathVariable(value = "id") Integer datasourceId,
			@PathVariable(value = "relationId") Integer relationId) {
		try {
			datasourceService.deleteLogicalRelation(datasourceId, relationId);
			return ApiResponse.success("success delete logical relation");
		}
		catch (Exception e) {
			log.error("Failed to delete logical relation {} for datasource: {}", relationId, datasourceId, e);
			return ApiResponse.error("删除逻辑外键失败：" + e.getMessage());
		}
	}

	/**
	 * 批量保存逻辑外键（替换现有的所有外键）
	 */
	@PutMapping("/{id}/logical-relations")
	public ApiResponse<List<LogicalRelation>> saveLogicalRelations(@PathVariable(value = "id") Integer datasourceId,
			@RequestBody List<LogicalRelation> logicalRelations) {
		try {
			List<LogicalRelation> saved = datasourceService.saveLogicalRelations(datasourceId, logicalRelations);
			return ApiResponse.success("success save logical relations", saved);
		}
		catch (Exception e) {
			log.error("Failed to save logical relations for datasource: {}", datasourceId, e);
			return ApiResponse.error("批量保存逻辑外键失败：" + e.getMessage());
		}
	}

}
