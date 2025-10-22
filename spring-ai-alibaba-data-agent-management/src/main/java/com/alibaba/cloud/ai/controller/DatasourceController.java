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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

// todo: 不要吞掉所有异常，可以直接抛出，写一个Advice拦截异常并做日志
@RestController
@RequestMapping("/api/datasource")
@CrossOrigin(origins = "*")
public class DatasourceController {

	private final DatasourceService datasourceService;

	public DatasourceController(DatasourceService datasourceService) {
		this.datasourceService = datasourceService;
	}

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
	public ResponseEntity<Map<String, Object>> deleteDatasource(@PathVariable(value = "id") Integer id) {
		try {
			datasourceService.deleteDatasource(id);
			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("message", "数据源删除成功");
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			Map<String, Object> response = new HashMap<>();
			response.put("success", false);
			response.put("message", "删除失败：" + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	/**
	 * Test data source connection
	 */
	@PostMapping("/{id}/test")
	public ResponseEntity<Map<String, Object>> testConnection(@PathVariable(value = "id") Integer id) {
		try {
			boolean success = datasourceService.testConnection(id);
			Map<String, Object> response = new HashMap<>();
			response.put("success", success);
			response.put("message", success ? "连接测试成功" : "连接测试失败");
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			Map<String, Object> response = new HashMap<>();
			response.put("success", false);
			response.put("message", "测试失败：" + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	/**
	 * Get data source statistics
	 */
	@GetMapping("/stats")
	public ResponseEntity<Map<String, Object>> getDatasourceStats() {
		try {
			Map<String, Object> stats = datasourceService.getDatasourceStats();
			return ResponseEntity.ok(stats);
		}
		catch (Exception e) {
			return ResponseEntity.badRequest().build();
		}
	}

}
