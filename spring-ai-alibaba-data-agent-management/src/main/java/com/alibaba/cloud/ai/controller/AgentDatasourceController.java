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

import com.alibaba.cloud.ai.entity.AgentDatasource;
import com.alibaba.cloud.ai.entity.Datasource;
import com.alibaba.cloud.ai.service.AgentDatasourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Schema Initialization Controller Handles agent's database Schema initialization
 * to vector storage
 */
@Controller
@RequestMapping("/api/agent/{agentId}/datasources")
@CrossOrigin(origins = "*")
public class AgentDatasourceController {

	private static final Logger log = LoggerFactory.getLogger(AgentDatasourceController.class);

	private final AgentDatasourceService agentDatasourceService;

	public AgentDatasourceController(AgentDatasourceService agentDatasourceService) {
		this.agentDatasourceService = agentDatasourceService;
	}

	/**
	 * Initialize agent's database Schema to vector storage Corresponds to the "Initialize
	 * Information Source" function on the frontend
	 */
	@PostMapping("/init")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> initializeSchema(@PathVariable(value = "agentId") Long agentId,
			@RequestBody Map<String, Object> requestData) {

		Map<String, Object> response = new HashMap<>();

		try {
			log.info("Initializing schema for agent: {}", agentId);

			// Extract data source ID and table list from request
			Integer datasourceId = null;
			List<String> tables = null;

			// Try to extract data from different request formats
			if (requestData.containsKey("datasourceId")) {
				datasourceId = (Integer) requestData.get("datasourceId");
			}
			else if (requestData.containsKey("dbConfig")) {
				Map<String, Object> dbConfig = (Map<String, Object>) requestData.get("dbConfig");
				if (dbConfig.containsKey("id")) {
					datasourceId = (Integer) dbConfig.get("id");
				}
			}

			if (requestData.containsKey("tables")) {
				tables = (List<String>) requestData.get("tables");
			}

			// Validate request parameters
			if (datasourceId == null) {
				response.put("success", false);
				response.put("message", "数据源ID不能为空");
				return ResponseEntity.badRequest().body(response);
			}

			if (tables == null || tables.isEmpty()) {
				response.put("success", false);
				response.put("message", "表列表不能为空");
				return ResponseEntity.badRequest().body(response);
			}

			// Execute Schema initialization
			Boolean result = agentDatasourceService.initializeSchemaForAgentWithDatasource(agentId, datasourceId,
					tables);

			if (result) {
				response.put("success", true);
				response.put("message", "Schema初始化成功");
				response.put("agentId", agentId);
				response.put("tablesCount", tables.size());

				log.info("Successfully initialized schema for agent: {}, tables: {}", agentId, tables.size());

				return ResponseEntity.ok(response);
			}
			else {
				response.put("success", false);
				response.put("message", "Schema初始化失败");
				return ResponseEntity.badRequest().body(response);
			}

		}
		catch (Exception e) {
			log.error("Failed to initialize schema for agent: {}", agentId, e);
			response.put("success", false);
			response.put("message", "Schema初始化失败：" + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	/**
	 * Get list of data sources configured for agent
	 */
	@GetMapping
	@ResponseBody
	public ResponseEntity<Map<String, Object>> getAgentDatasources(@PathVariable(value = "agentId") Long agentId) {
		Map<String, Object> response = new HashMap<>();

		try {
			log.info("Getting datasources for agent: {}", agentId);
			List<Datasource> datasources = agentDatasourceService.getAgentDatasourcesWithDetails(agentId);

			response.put("success", true);
			response.put("data", datasources);
			response.put("agentId", agentId);

			log.info("Successfully retrieved {} datasources for agent: {}", datasources.size(), agentId);
			return ResponseEntity.ok(response);

		}
		catch (Exception e) {
			log.error("Failed to get datasources for agent: {}", agentId, e);
			response.put("success", false);
			response.put("message", "获取数据源失败：" + e.getMessage());
			response.put("data", new ArrayList<>());
			return ResponseEntity.badRequest().body(response);
		}
	}

	/**
	 * Get table list of data source
	 */
	@GetMapping("/{datasourceId}/tables")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> getDatasourceTables(
			@PathVariable(value = "datasourceId") Integer datasourceId) {
		Map<String, Object> response = new HashMap<>();

		try {
			log.info("Getting tables for datasource: {}", datasourceId);

			List<String> tables = agentDatasourceService.getDatasourceTables(datasourceId);

			response.put("success", true);
			response.put("data", tables);
			response.put("datasourceId", datasourceId);

			log.info("Successfully retrieved {} tables for datasource: {}", tables.size(), datasourceId);
			return ResponseEntity.ok(response);

		}
		catch (Exception e) {
			log.error("Failed to get tables for datasource: {}", datasourceId, e);
			response.put("success", false);
			response.put("message", "获取表列表失败：" + e.getMessage());
			response.put("data", new ArrayList<>());
			return ResponseEntity.badRequest().body(response);
		}
	}

	/**
	 * Add data source for agent
	 */
	@PostMapping
	@ResponseBody
	public ResponseEntity<Map<String, Object>> addDatasourceToAgent(@PathVariable(value = "agentId") Integer agentId,
			@RequestBody Map<String, Integer> request) {
		try {
			Integer datasourceId = request.get("datasourceId");
			if (datasourceId == null) {
				Map<String, Object> response = new HashMap<>();
				response.put("success", false);
				response.put("message", "数据源ID不能为空");
				return ResponseEntity.badRequest().body(response);
			}

			AgentDatasource agentDatasource = agentDatasourceService.addDatasourceToAgent(agentId, datasourceId);
			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("message", "数据源添加成功");
			response.put("data", agentDatasource);
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			Map<String, Object> response = new HashMap<>();
			response.put("success", false);
			response.put("message", "添加失败：" + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	/**
	 * Remove data source association from agent
	 */
	@DeleteMapping("/{datasourceId}")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> removeDatasourceFromAgent(@PathVariable("agentId") Integer agentId,
			@PathVariable("datasourceId") Integer datasourceId) {
		try {
			agentDatasourceService.removeDatasourceFromAgent(agentId, datasourceId);
			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("message", "数据源移除成功");
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			Map<String, Object> response = new HashMap<>();
			response.put("success", false);
			response.put("message", "移除失败：" + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	/**
	 * 启用/禁用智能体的数据源
	 */
	@PutMapping("/{datasourceId}/toggle")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> toggleDatasourceForAgent(@PathVariable("agentId") Integer agentId,
			@PathVariable("datasourceId") Integer datasourceId, @RequestBody Map<String, Boolean> request) {
		try {
			Boolean isActive = request.get("isActive");
			if (isActive == null) {
				Map<String, Object> response = new HashMap<>();
				response.put("success", false);
				response.put("message", "激活状态不能为空");
				return ResponseEntity.badRequest().body(response);
			}

			AgentDatasource agentDatasource = agentDatasourceService.toggleDatasourceForAgent(agentId, datasourceId,
					isActive);
			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("message", isActive ? "数据源已启用" : "数据源已禁用");
			response.put("data", agentDatasource);
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			Map<String, Object> response = new HashMap<>();
			response.put("success", false);
			response.put("message", "操作失败：" + e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

}
