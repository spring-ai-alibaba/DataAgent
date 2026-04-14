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

import com.alibaba.cloud.ai.dataagent.dto.datasource.ToggleDatasourceDTO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.UpdateDatasourceTablesDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.exception.InternalServerException;
import com.alibaba.cloud.ai.dataagent.exception.InvalidInputException;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
	public ApiResponse<?> initSchema(@PathVariable Long agentId) {
		// 防止前端恶意请求，dto数据应该在后端获取
		try {
			AgentDatasource agentDatasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
			log.info("Initializing schema for agent: {}", agentId);

			// Extract data source ID and table list from request
			Integer datasourceId = agentDatasource.getDatasourceId();
			List<String> tables = Optional.ofNullable(agentDatasource.getSelectTables()).orElse(List.of());

			// Validate request parameters
			if (datasourceId == null) {
				throw new InvalidInputException("数据源ID不能为空");
			}

			if (tables.isEmpty()) {
				throw new InvalidInputException("表列表不能为空");
			}

			// Execute Schema initialization
			Boolean result = agentDatasourceService.initializeSchemaForAgentWithDatasource(agentId, datasourceId,
					tables);

			if (result) {
				log.info("Successfully initialized schema for agent: {}, tables: {}", agentId, tables.size());
				return ApiResponse.success("Schema初始化成功");
			}
			else {
				throw new InternalServerException("Schema初始化失败");
			}
		}
		catch (Exception e) {
			log.error("Failed to initialize schema for agent: {}", agentId, e);
			throw new InternalServerException("Schema初始化失败：%s".formatted(e.getMessage()));
		}
	}

	/** Get list of data sources configured for agent */
	@GetMapping
	public ApiResponse<List<AgentDatasource>> getAgentDatasource(@PathVariable Long agentId) {
		try {
			log.info("Getting datasources for agent: {}", agentId);
			List<AgentDatasource> datasources = agentDatasourceService.getAgentDatasource(agentId);
			log.info("Successfully retrieved {} datasources for agent: {}", datasources.size(), agentId);
			return ApiResponse.success("操作成功", datasources);
		}
		catch (Exception e) {
			log.error("Failed to get datasources for agent: {}", agentId, e);
			throw new InvalidInputException("获取数据源失败：%s".formatted(e.getMessage()), List.of());
		}
	}

	@GetMapping("/active")
	public ApiResponse<AgentDatasource> getActiveAgentDatasource(@PathVariable Long agentId) {
		try {
			log.info("Getting active datasource for agent: {}", agentId);
			AgentDatasource datasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
			return ApiResponse.success("操作成功", datasource);
		}
		catch (Exception e) {
			log.error("Failed to get active datasource for agent: {}", agentId, e);
			throw new InvalidInputException("获取数据源失败：%s".formatted(e.getMessage()), List.of());
		}
	}

	/** Add data source for agent */
	@PostMapping("/{datasourceId}")
	public ApiResponse<AgentDatasource> addDatasourceToAgent(@PathVariable Long agentId,
			@PathVariable Integer datasourceId) {
		try {
			if (datasourceId == null) {
				throw new InvalidInputException("数据源ID不能为空");
			}

			AgentDatasource agentDatasource = agentDatasourceService.addDatasourceToAgent(agentId, datasourceId);
			return ApiResponse.success("数据源添加成功", agentDatasource);
		}
		catch (Exception e) {
			throw new InternalServerException("数据源添加失败：%s".formatted(e.getMessage()));
		}
	}

	// 更新选择的数据表
	@PostMapping("/tables")
	public ApiResponse<?> updateDatasourceTables(@PathVariable Long agentId,
			@RequestBody @Validated UpdateDatasourceTablesDTO dto) {
		try {
			dto.setTables(Optional.ofNullable(dto.getTables()).orElse(List.of()));
			agentDatasourceService.updateDatasourceTables(agentId, dto.getDatasourceId(), dto.getTables());
			return ApiResponse.success("更新成功");
		}
		catch (Exception e) {
			log.error("Error: ", e);
			throw new InternalServerException("更新失败：%s".formatted(e.getMessage()));
		}
	}

	/** Remove data source association from agent */
	@DeleteMapping("/{datasourceId}")
	public ApiResponse<?> removeDatasourceFromAgent(@PathVariable Long agentId, @PathVariable Integer datasourceId) {
		try {
			agentDatasourceService.removeDatasourceFromAgent(agentId, datasourceId);
			return ApiResponse.success("数据源已移除");
		}
		catch (Exception e) {
			throw new InternalServerException("移除失败：%s".formatted(e.getMessage()));
		}
	}

	/** 启用/禁用智能体的数据源 */
	@PutMapping("/toggle")
	public ApiResponse<AgentDatasource> toggleDatasourceForAgent(@PathVariable Long agentId,
			@RequestBody ToggleDatasourceDTO dto) {
		try {
			Boolean isActive = dto.getIsActive();
			Integer datasourceId = dto.getDatasourceId();
			if (isActive == null || datasourceId == null) {
				throw new InvalidInputException("激活状态不能为空");
			}
			AgentDatasource agentDatasource = agentDatasourceService.toggleDatasourceForAgent(agentId,
					dto.getDatasourceId(), isActive);
			return ApiResponse.success(isActive ? "数据源已启用" : "数据源已禁用", agentDatasource);
		}
		catch (Exception e) {
			throw new InternalServerException("操作失败：%s".formatted(e.getMessage()));
		}
	}

}
