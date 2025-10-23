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

import com.alibaba.cloud.ai.dto.InitSchemaDto;
import com.alibaba.cloud.ai.dto.ToggleDatasourceDto;
import com.alibaba.cloud.ai.entity.AgentDatasource;
import com.alibaba.cloud.ai.service.AgentDatasourceService;
import com.alibaba.cloud.ai.vo.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent Schema Initialization Controller Handles agent's database Schema initialization
 * to vector storage
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/{agentId}/datasources")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class AgentDatasourceController {

	private final AgentDatasourceService agentDatasourceService;

	/**
	 * Initialize agent's database Schema to vector storage Corresponds to the "Initialize
	 * Information Source" function on the frontend
	 */
	@PostMapping("/init")
	public ResponseEntity<ApiResponse> initSchema(@PathVariable(value = "agentId") Long agentId,
			@RequestBody InitSchemaDto dto) {
		try {
			log.info("Initializing schema for agent: {}", agentId);

			// Extract data source ID and table list from request
			Integer datasourceId = dto.getDatasourceId();
			List<String> tables = dto.getTables();

			// Validate request parameters
			if (datasourceId == null) {
				return ResponseEntity.badRequest().body(ApiResponse.error("数据源ID不能为空"));
			}

			if (tables == null || tables.isEmpty()) {
				return ResponseEntity.badRequest().body(ApiResponse.error("表列表不能为空"));
			}

			// Execute Schema initialization
			Boolean result = agentDatasourceService.initializeSchemaForAgentWithDatasource(agentId, datasourceId,
					tables);

			if (result) {
				log.info("Successfully initialized schema for agent: {}, tables: {}", agentId, tables.size());
				return ResponseEntity.ok(ApiResponse.success("Schema初始化成功"));
			}
			else {
				return ResponseEntity.internalServerError().body(ApiResponse.error("Schema初始化失败"));
			}
		}
		catch (Exception e) {
			log.error("Failed to initialize schema for agent: {}", agentId, e);
			return ResponseEntity.internalServerError().body(ApiResponse.error("Schema初始化失败：" + e.getMessage()));
		}
	}

	/**
	 * Get list of data sources configured for agent
	 */
	@GetMapping
	public ResponseEntity<ApiResponse> getAgentDatasource(@PathVariable(value = "agentId") Long agentId) {
		try {
			log.info("Getting datasources for agent: {}", agentId);
			List<AgentDatasource> datasources = agentDatasourceService.getAgentDatasource(Math.toIntExact(agentId));
			log.info("Successfully retrieved {} datasources for agent: {}", datasources.size(), agentId);
			return ResponseEntity.ok(ApiResponse.success("操作成功", datasources));
		}
		catch (Exception e) {
			log.error("Failed to get datasources for agent: {}", agentId, e);
			return ResponseEntity.badRequest().body(ApiResponse.error("获取数据源失败：" + e.getMessage(), List.of()));
		}
	}

	/**
	 * Get table list of data source
	 */
	@GetMapping("/{datasourceId}/tables")
	public ResponseEntity<ApiResponse> getDatasourceTables(@PathVariable(value = "datasourceId") Integer datasourceId) {
		try {
			log.info("Getting tables for datasource: {}", datasourceId);
			List<String> tables = agentDatasourceService.getDatasourceTables(datasourceId);
			log.info("Successfully retrieved {} tables for datasource: {}", tables.size(), datasourceId);
			return ResponseEntity.ok(ApiResponse.success("操作成功", tables));

		}
		catch (Exception e) {
			log.error("Failed to get tables for datasource: {}", datasourceId, e);
			return ResponseEntity.badRequest().body(ApiResponse.error("获取表列表失败：" + e.getMessage(), List.of()));
		}
	}

	/**
	 * Add data source for agent
	 */
	@PostMapping("/{datasourceId}")
	public ResponseEntity<ApiResponse> addDatasourceToAgent(@PathVariable(value = "agentId") Integer agentId,
			@PathVariable(value = "datasourceId") Integer datasourceId) {
		try {
			if (datasourceId == null) {
				return ResponseEntity.badRequest().body(ApiResponse.error("数据源ID不能为空"));
			}

			AgentDatasource agentDatasource = agentDatasourceService.addDatasourceToAgent(agentId, datasourceId);
			return ResponseEntity.ok(ApiResponse.success("数据源添加成功", agentDatasource));
		}
		catch (Exception e) {
			return ResponseEntity.badRequest().body(ApiResponse.error("添加失败：" + e.getMessage()));
		}
	}

	/**
	 * Remove data source association from agent
	 */
	@DeleteMapping("/{datasourceId}")
	public ResponseEntity<ApiResponse> removeDatasourceFromAgent(@PathVariable("agentId") Integer agentId,
			@PathVariable("datasourceId") Integer datasourceId) {
		try {
			agentDatasourceService.removeDatasourceFromAgent(agentId, datasourceId);
			return ResponseEntity.ok(ApiResponse.success("数据源已移除"));
		}
		catch (Exception e) {
			return ResponseEntity.badRequest().body(ApiResponse.error("移除失败：" + e.getMessage()));
		}
	}

	/**
	 * 启用/禁用智能体的数据源
	 */
	@PutMapping("/toggle")
	public ResponseEntity<ApiResponse> toggleDatasourceForAgent(@PathVariable("agentId") Integer agentId,
			@RequestBody ToggleDatasourceDto dto) {
		try {
			Boolean isActive = dto.getIsActive();
			Integer datasourceId = dto.getDatasourceId();
			if (isActive == null || datasourceId == null) {
				return ResponseEntity.badRequest().body(ApiResponse.error("激活状态不能为空"));
			}
			AgentDatasource agentDatasource = agentDatasourceService.toggleDatasourceForAgent(agentId,
					dto.getDatasourceId(), isActive);
			return ResponseEntity.ok(ApiResponse.success(isActive ? "数据源已启用" : "数据源已禁用", agentDatasource));
		}
		catch (Exception e) {
			return ResponseEntity.badRequest().body(ApiResponse.error("操作失败：" + e.getMessage()));
		}
	}

}
