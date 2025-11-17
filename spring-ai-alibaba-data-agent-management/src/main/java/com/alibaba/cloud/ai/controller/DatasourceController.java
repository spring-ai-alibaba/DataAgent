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

package com.alibaba.cloud.ai.controller;

import com.alibaba.cloud.ai.entity.Datasource;
import com.alibaba.cloud.ai.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.vo.ApiResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// todo: 不要吞掉所有异常，可以直接抛出，写一个Advice拦截异常并做日志
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
			return ResponseEntity.ok(ApiResponse.success(success ? "连接测试成功" : "连接测试失败"));
		}
		catch (Exception e) {
			return ResponseEntity.badRequest().body(ApiResponse.error("测试失败：" + e.getMessage()));
		}
	}

}
