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

import com.alibaba.cloud.ai.dataagent.dto.datasource.TableColumnsSelectionDTO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.ToggleDatasourceDTO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.UpdateDatasourceColumnsDTO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.UpdateDatasourceTablesDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.exception.InternalServerException;
import com.alibaba.cloud.ai.dataagent.exception.InvalidInputException;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent datasource controller.
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/{agentId}/datasources")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class AgentDatasourceController {

	private final AgentDatasourceService agentDatasourceService;

	@PostMapping("/init")
	public ApiResponse<?> initSchema(@PathVariable Long agentId) {
		try {
			AgentDatasource agentDatasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
			log.info("Initializing schema for agent: {}", agentId);

			Integer datasourceId = agentDatasource.getDatasourceId();
			List<String> tables = Optional.ofNullable(agentDatasource.getSelectTables()).orElse(List.of());

			if (datasourceId == null) {
				throw new InvalidInputException("datasourceId cannot be null");
			}
			if (tables.isEmpty()) {
				throw new InvalidInputException("tables cannot be empty");
			}

			Boolean result = agentDatasourceService.initializeSchemaForAgentWithDatasource(agentId, datasourceId, tables);
			if (Boolean.TRUE.equals(result)) {
				log.info("Successfully initialized schema for agent: {}, tables: {}", agentId, tables.size());
				return ApiResponse.success("Schema initialized successfully");
			}
			throw new InternalServerException("Schema initialization failed");
		}
		catch (InvalidInputException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to initialize schema for agent: {}", agentId, e);
			throw new InternalServerException("Schema initialization failed: %s".formatted(e.getMessage()));
		}
	}

	@GetMapping
	public ApiResponse<List<AgentDatasource>> getAgentDatasource(@PathVariable Long agentId) {
		try {
			log.info("Getting datasources for agent: {}", agentId);
			List<AgentDatasource> datasources = agentDatasourceService.getAgentDatasource(agentId);
			log.info("Successfully retrieved {} datasources for agent: {}", datasources.size(), agentId);
			return ApiResponse.success("success", datasources);
		}
		catch (Exception e) {
			log.error("Failed to get datasources for agent: {}", agentId, e);
			throw new InvalidInputException("Failed to get datasources: %s".formatted(e.getMessage()), List.of());
		}
	}

	@GetMapping("/active")
	public ApiResponse<AgentDatasource> getActiveAgentDatasource(@PathVariable Long agentId) {
		try {
			log.info("Getting active datasource for agent: {}", agentId);
			AgentDatasource datasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
			return ApiResponse.success("success", datasource);
		}
		catch (Exception e) {
			log.error("Failed to get active datasource for agent: {}", agentId, e);
			throw new InvalidInputException("Failed to get active datasource: %s".formatted(e.getMessage()), List.of());
		}
	}

	@PostMapping("/{datasourceId}")
	public ApiResponse<AgentDatasource> addDatasourceToAgent(@PathVariable Long agentId,
			@PathVariable Integer datasourceId) {
		try {
			if (datasourceId == null) {
				throw new InvalidInputException("datasourceId cannot be null");
			}
			AgentDatasource agentDatasource = agentDatasourceService.addDatasourceToAgent(agentId, datasourceId);
			return ApiResponse.success("Datasource added successfully", agentDatasource);
		}
		catch (InvalidInputException e) {
			throw e;
		}
		catch (Exception e) {
			throw new InternalServerException("Failed to add datasource: %s".formatted(e.getMessage()));
		}
	}

	@PostMapping("/tables")
	public ApiResponse<AgentDatasource> updateDatasourceTables(@PathVariable Long agentId,
			@RequestBody @Validated UpdateDatasourceTablesDTO dto) {
		try {
			dto.setTables(Optional.ofNullable(dto.getTables()).orElse(List.of()));
			AgentDatasource agentDatasource = agentDatasourceService.updateDatasourceTables(agentId, dto.getDatasourceId(),
					dto.getTables());
			return ApiResponse.success("Update successful", agentDatasource);
		}
		catch (IllegalArgumentException e) {
			log.warn("Invalid datasource tables update request, agentId={}, datasourceId={}, message={}", agentId,
					dto.getDatasourceId(), e.getMessage());
			throw new InvalidInputException(e.getMessage());
		}
		catch (Exception e) {
			log.error("Error updating datasource tables", e);
			throw new InternalServerException("Update failed: %s".formatted(e.getMessage()));
		}
	}

	@PostMapping("/columns")
	public ApiResponse<AgentDatasource> updateDatasourceColumns(@PathVariable Long agentId,
			@RequestBody @Valid UpdateDatasourceColumnsDTO dto) {
		try {
			List<TableColumnsSelectionDTO> tableSelections = Optional.ofNullable(dto.getTables()).orElse(List.of());
			Map<String, List<String>> columnsByTable = new LinkedHashMap<>();
			for (TableColumnsSelectionDTO tableSelection : tableSelections) {
				if (tableSelection == null) {
					continue;
				}
				columnsByTable.put(tableSelection.getTableName(),
						Optional.ofNullable(tableSelection.getColumns()).orElse(List.of()));
			}
			AgentDatasource agentDatasource = agentDatasourceService.updateDatasourceColumns(agentId, dto.getDatasourceId(),
					columnsByTable);
			return ApiResponse.success("Update successful", agentDatasource);
		}
		catch (IllegalArgumentException e) {
			log.warn("Invalid datasource columns update request, agentId={}, datasourceId={}, message={}", agentId,
					dto.getDatasourceId(), e.getMessage());
			throw new InvalidInputException(e.getMessage());
		}
		catch (Exception e) {
			log.error("Error updating datasource columns", e);
			throw new InternalServerException("Update failed: %s".formatted(e.getMessage()));
		}
	}

	@GetMapping("/{datasourceId}/tables/{tableName}/columns")
	public ApiResponse<List<String>> getVisibleTableColumns(@PathVariable Long agentId, @PathVariable Integer datasourceId,
			@PathVariable String tableName) {
		try {
			List<String> columns = agentDatasourceService.getVisibleTableColumns(agentId, datasourceId, tableName);
			return ApiResponse.success("success", columns);
		}
		catch (IllegalArgumentException e) {
			log.warn("Invalid visible columns request, agentId={}, datasourceId={}, tableName={}, message={}", agentId,
					datasourceId, tableName, e.getMessage());
			throw new InvalidInputException(e.getMessage());
		}
		catch (Exception e) {
			log.error("Error loading visible columns, agentId={}, datasourceId={}, tableName={}", agentId, datasourceId,
					tableName, e);
			throw new InternalServerException("Load columns failed: %s".formatted(e.getMessage()));
		}
	}

	@DeleteMapping("/{datasourceId}")
	public ApiResponse<?> removeDatasourceFromAgent(@PathVariable Long agentId, @PathVariable Integer datasourceId) {
		try {
			agentDatasourceService.removeDatasourceFromAgent(agentId, datasourceId);
			return ApiResponse.success("Datasource removed");
		}
		catch (Exception e) {
			throw new InternalServerException("Remove failed: %s".formatted(e.getMessage()));
		}
	}

	@PutMapping("/toggle")
	public ApiResponse<AgentDatasource> toggleDatasourceForAgent(@PathVariable Long agentId,
			@RequestBody ToggleDatasourceDTO dto) {
		try {
			Boolean isActive = dto.getIsActive();
			Integer datasourceId = dto.getDatasourceId();
			if (isActive == null || datasourceId == null) {
				throw new InvalidInputException("isActive and datasourceId cannot be null");
			}
			AgentDatasource agentDatasource = agentDatasourceService.toggleDatasourceForAgent(agentId,
					dto.getDatasourceId(), isActive);
			return ApiResponse.success(isActive ? "Datasource enabled" : "Datasource disabled", agentDatasource);
		}
		catch (InvalidInputException e) {
			throw e;
		}
		catch (Exception e) {
			throw new InternalServerException("Operation failed: %s".formatted(e.getMessage()));
		}
	}

}
