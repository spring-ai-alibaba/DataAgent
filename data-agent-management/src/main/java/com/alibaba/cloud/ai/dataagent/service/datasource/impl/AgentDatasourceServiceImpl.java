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
package com.alibaba.cloud.ai.dataagent.service.datasource.impl;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SchemaInitRequest;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasourceColumn;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceColumnsMapper;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceTablesMapper;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Slf4j
@Service
@AllArgsConstructor
public class AgentDatasourceServiceImpl implements AgentDatasourceService {

	private final DatasourceService datasourceService;

	private final SchemaService schemaService;

	private final AgentDatasourceMapper agentDatasourceMapper;

	private final AgentDatasourceTablesMapper tablesMapper;

	private final AgentDatasourceColumnsMapper columnsMapper;

	@Override
	public Boolean initializeSchemaForAgentWithDatasource(Long agentId, Integer datasourceId, List<String> tables) {
		Assert.notNull(agentId, "Agent ID cannot be null");
		Assert.notNull(datasourceId, "Datasource ID cannot be null");
		Assert.notEmpty(tables, "Tables cannot be empty");
		try {
			String agentIdStr = String.valueOf(agentId);
			log.info("Initializing schema for agent: {} with datasource: {}, tables: {}", agentIdStr, datasourceId,
					tables);

			// Get data source information
			Datasource datasource = datasourceService.getDatasourceById(datasourceId);
			if (datasource == null) {
				throw new RuntimeException("Datasource not found with id: " + datasourceId);
			}

			// Create database configuration
			DbConfigBO dbConfig = datasourceService.getDbConfig(datasource);
			AgentDatasource agentDatasource = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
			if (agentDatasource == null) {
				throw new RuntimeException("Agent datasource relation not found with agentId=%s, datasourceId=%s"
					.formatted(agentId, datasourceId));
			}

			// Create SchemaInitRequest
			SchemaInitRequest schemaInitRequest = new SchemaInitRequest();
			schemaInitRequest.setDbConfig(dbConfig);
			schemaInitRequest.setTables(tables);
			schemaInitRequest.setVisibleColumnsByTable(loadSelectedColumns(agentDatasource.getId()));

			log.info("Created SchemaInitRequest for agent: {}, dbConfig: {}, tables: {}", agentIdStr, dbConfig, tables);

			// Call the original initialization method
			return schemaService.schema(datasourceId, schemaInitRequest);

		}
		catch (Exception e) {
			log.error("Failed to initialize schema for agent: {} with datasource: {}", agentId, datasourceId, e);
			throw new RuntimeException("Failed to initialize schema for agent " + agentId + ": " + e.getMessage(), e);
		}
	}

	@Override
	public List<AgentDatasource> getAgentDatasource(Long agentId) {
		Assert.notNull(agentId, "Agent ID cannot be null");
		List<AgentDatasource> adentDatasources = agentDatasourceMapper.selectByAgentIdWithDatasource(agentId);

		for (AgentDatasource agentDatasource : adentDatasources) {
			enrichAgentDatasource(agentDatasource);
		}

		return adentDatasources;
	}

	@Override
	@Transactional
	public AgentDatasource addDatasourceToAgent(Long agentId, Integer datasourceId) {
		// First, disable other data sources for this agent (an agent can only have one
		// enabled data source)
		agentDatasourceMapper.disableAllByAgentId(agentId);

		// Check if an association already exists
		AgentDatasource existing = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);

		AgentDatasource result;
		if (existing != null) {
			// If it exists, activate the association
			agentDatasourceMapper.enableRelation(agentId, datasourceId);

			// 删除已有的表
			tablesMapper.removeAllTables(existing.getId());
			columnsMapper.removeAllColumns(existing.getId());

			// Query and return the updated association
			result = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		}
		else {
			// If it does not exist, create a new association
			AgentDatasource agentDatasource = new AgentDatasource(agentId, datasourceId);
			agentDatasource.setIsActive(1);
			agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId);
			result = agentDatasource;
		}
		result.setSelectTables(List.of());
		result.setSelectColumns(Map.of());
		return result;
	}

	@Override
	public void removeDatasourceFromAgent(Long agentId, Integer datasourceId) {
		agentDatasourceMapper.removeRelation(agentId, datasourceId);
	}

	@Override
	public AgentDatasource toggleDatasourceForAgent(Long agentId, Integer datasourceId, Boolean isActive) {
		// If enabling data source, first check if there are other enabled data sources
		if (isActive) {
			int activeCount = agentDatasourceMapper.countActiveByAgentIdExcluding(agentId, datasourceId);
			if (activeCount > 0) {
				throw new RuntimeException("同一智能体下只能启用一个数据源，请先禁用其他数据源后再启用此数据源");
			}
		}

		// Update data source status
		int updated = agentDatasourceMapper.updateRelation(agentId, datasourceId, isActive ? 1 : 0);

		if (updated == 0) {
			throw new RuntimeException("未找到相关的数据源关联记录");
		}

		// Return the updated association record
		AgentDatasource agentDatasource = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		enrichAgentDatasource(agentDatasource);
		return agentDatasource;
	}

	@Override
	@Transactional
	public AgentDatasource updateDatasourceTables(Long agentId, Integer datasourceId, List<String> tables) {
		if (agentId == null || datasourceId == null || tables == null) {
			throw new IllegalArgumentException("参数不能为空");
		}
		AgentDatasource datasource = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		if (datasource == null) {
			throw new IllegalArgumentException("未找到对应的数据源关联记录");
		}
		List<String> normalizedTables;
		try {
			TableResolutionIndex datasourceTableIndex = buildTableResolutionIndex(
					datasourceService.getDatasourceTables(datasourceId));
			normalizedTables = sanitizeRequestedTables(tables, datasourceTableIndex);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Failed to validate datasource tables: %s".formatted(ex.getMessage()), ex);
		}
		if (normalizedTables.isEmpty()) {
			tablesMapper.removeAllTables(datasource.getId());
			columnsMapper.removeAllColumns(datasource.getId());
		}
		else {
			tablesMapper.updateAgentDatasourceTables(datasource.getId(), normalizedTables);
			columnsMapper.removeColumnsOutsideTables(datasource.getId(), normalizedTables);
		}
		return refreshAgentDatasource(agentId, datasourceId);
	}

	@Override
	@Transactional
	public AgentDatasource updateDatasourceColumns(Long agentId, Integer datasourceId,
			Map<String, List<String>> columnsByTable)
			throws Exception {
		if (agentId == null || datasourceId == null || columnsByTable == null) {
			throw new IllegalArgumentException("参数不能为空");
		}
		AgentDatasource agentDatasource = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		if (agentDatasource == null) {
			throw new IllegalArgumentException("未找到对应的数据源关联记录");
		}

		TableResolutionIndex allowedTables = loadAllowedTables(agentDatasource, datasourceId);
		Map<String, List<String>> sanitizedColumnsByTable = sanitizeColumnsByTable(datasourceId, columnsByTable,
				allowedTables);

		columnsMapper.removeAllColumns(agentDatasource.getId());
		List<AgentDatasourceColumn> rows = new ArrayList<>();
		sanitizedColumnsByTable.forEach((tableName, columns) -> columns.forEach(columnName -> rows
			.add(new AgentDatasourceColumn(null, agentDatasource.getId(), tableName, columnName, null, null))));
		if (!rows.isEmpty()) {
			columnsMapper.insertColumns(rows);
		}
		return refreshAgentDatasource(agentId, datasourceId);
	}

	@Override
	public List<String> getVisibleTableColumns(Long agentId, Integer datasourceId, String tableName) throws Exception {
		if (agentId == null || datasourceId == null || tableName == null || tableName.isBlank()) {
			throw new IllegalArgumentException("agentId, datasourceId and tableName cannot be blank");
		}
		AgentDatasource agentDatasource = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		if (agentDatasource == null) {
			throw new IllegalArgumentException("鏈壘鍒板搴旂殑鏁版嵁婧愬叧鑱旇褰?");
		}
		TableResolutionIndex allowedTables = loadAllowedTables(agentDatasource, datasourceId);
		String actualTableName = resolveTableName(tableName, allowedTables, false);
		if (actualTableName == null) {
			throw new IllegalArgumentException(
					"Table '%s' does not exist or is not visible in current agent datasource".formatted(tableName));
		}
		return datasourceService.getTableColumns(datasourceId, actualTableName);
	}

	private void enrichAgentDatasource(AgentDatasource agentDatasource) {
		if (agentDatasource == null) {
			return;
		}
		if (agentDatasource.getDatasourceId() != null && agentDatasource.getDatasource() == null) {
			Datasource datasource = datasourceService.getDatasourceById(agentDatasource.getDatasourceId());
			agentDatasource.setDatasource(datasource);
		}
		List<String> tables = tablesMapper.getAgentDatasourceTables(agentDatasource.getId());
		agentDatasource.setSelectTables(Optional.ofNullable(tables).orElse(List.of()));
		agentDatasource.setSelectColumns(loadSelectedColumns(agentDatasource.getId()));
	}

	private AgentDatasource refreshAgentDatasource(Long agentId, Integer datasourceId) {
		AgentDatasource refreshed = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		enrichAgentDatasource(refreshed);
		return refreshed;
	}

	private Map<String, List<String>> loadSelectedColumns(int agentDatasourceId) {
		List<AgentDatasourceColumn> rows = Optional.ofNullable(columnsMapper.getAgentDatasourceColumns(agentDatasourceId))
			.orElse(List.of());
		Map<String, List<String>> columnsByTable = new LinkedHashMap<>();
		for (AgentDatasourceColumn row : rows) {
			if (row == null) {
				continue;
			}
			columnsByTable.computeIfAbsent(row.getTableName(), key -> new ArrayList<>()).add(row.getColumnName());
		}
		columnsByTable.replaceAll((tableName, columns) -> List.copyOf(columns));
		return Map.copyOf(columnsByTable);
	}

	private TableResolutionIndex loadAllowedTables(AgentDatasource agentDatasource, Integer datasourceId) throws Exception {
		List<String> datasourceTables = datasourceService.getDatasourceTables(datasourceId);
		TableResolutionIndex datasourceTableIndex = buildTableResolutionIndex(datasourceTables);
		List<String> selectedTables = Optional.ofNullable(tablesMapper.getAgentDatasourceTables(agentDatasource.getId()))
			.orElse(List.of());
		List<String> visibleTables = selectedTables.isEmpty() ? datasourceTables
				: sanitizeRequestedTables(selectedTables, datasourceTableIndex, true);
		return buildTableResolutionIndex(visibleTables);
	}

	private Map<String, List<String>> sanitizeColumnsByTable(Integer datasourceId, Map<String, List<String>> columnsByTable,
			TableResolutionIndex allowedTables) throws Exception {
		Map<String, List<String>> sanitized = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> entry : columnsByTable.entrySet()) {
			String requestedTableName = entry.getKey();
			String actualTableName = resolveTableName(requestedTableName, allowedTables, false);
			if (actualTableName == null) {
				throw new IllegalArgumentException("字段白名单配置包含当前 agent 不可见的数据表: " + requestedTableName);
			}

			Map<String, String> actualColumns = datasourceService.getTableColumns(datasourceId, actualTableName)
				.stream()
				.collect(LinkedHashMap::new, (map, columnName) -> map.put(normalizeIdentifier(columnName), columnName),
						Map::putAll);
			LinkedHashSet<String> dedupedColumns = new LinkedHashSet<>();
			for (String requestedColumn : Optional.ofNullable(entry.getValue()).orElse(List.of())) {
				String normalizedColumn = normalizeIdentifier(requestedColumn);
				String actualColumnName = actualColumns.get(normalizedColumn);
				if (actualColumnName == null) {
					throw new IllegalArgumentException(
							"表 '%s' 中不存在字段 '%s'，无法保存字段级可见性配置".formatted(actualTableName, requestedColumn));
				}
				dedupedColumns.add(actualColumnName);
			}
			if (!dedupedColumns.isEmpty()) {
				sanitized.put(actualTableName, List.copyOf(dedupedColumns));
			}
		}
		return sanitized;
	}

	private List<String> normalizeTableNames(List<String> tables) {
		return tables.stream()
			.map(String::trim)
			.filter(tableName -> !tableName.isEmpty())
			.collect(Collectors.toCollection(LinkedHashSet::new))
			.stream()
			.toList();
	}

	private List<String> sanitizeRequestedTables(List<String> tables, TableResolutionIndex tableIndex) {
		return sanitizeRequestedTables(tables, tableIndex, false);
	}

	private List<String> sanitizeRequestedTables(List<String> tables, TableResolutionIndex tableIndex,
			boolean allowQualifiedFallback) {
		LinkedHashSet<String> resolvedTables = new LinkedHashSet<>();
		for (String tableName : normalizeTableNames(tables)) {
			String resolvedTableName = resolveTableName(tableName, tableIndex, allowQualifiedFallback);
			if (resolvedTableName == null) {
				throw new IllegalArgumentException(
						"Table '%s' does not exist or is not visible in current datasource".formatted(tableName));
			}
			resolvedTables.add(resolvedTableName);
		}
		return List.copyOf(resolvedTables);
	}

	private TableResolutionIndex buildTableResolutionIndex(List<String> tableNames) {
		return new TableResolutionIndex(indexTableNames(tableNames, false), indexTableNames(tableNames, true));
	}

	private Map<String, List<String>> indexTableNames(List<String> tableNames, boolean leafOnly) {
		Map<String, LinkedHashSet<String>> index = new LinkedHashMap<>();
		for (String tableName : Optional.ofNullable(tableNames).orElse(List.of())) {
			if (tableName == null || tableName.isBlank()) {
				continue;
			}
			String normalizedTableName = leafOnly ? normalizeLeafIdentifier(tableName) : normalizeIdentifier(tableName);
			index.computeIfAbsent(normalizedTableName, key -> new LinkedHashSet<>()).add(tableName);
		}
		Map<String, List<String>> immutableIndex = new LinkedHashMap<>();
		index.forEach((key, value) -> immutableIndex.put(key, List.copyOf(value)));
		return Map.copyOf(immutableIndex);
	}

	private String resolveTableName(String requestedTableName, TableResolutionIndex tableIndex,
			boolean allowQualifiedFallback) {
		String normalizedTableName = normalizeIdentifier(requestedTableName);
		List<String> exactMatches = tableIndex.exactTables().getOrDefault(normalizedTableName, List.of());
		if (exactMatches.size() == 1) {
			return exactMatches.get(0);
		}
		if (exactMatches.size() > 1) {
			throw new IllegalArgumentException(
					"Table '%s' maps to multiple datasource tables: %s".formatted(requestedTableName, exactMatches));
		}
		if (isQualifiedIdentifier(requestedTableName) && !allowQualifiedFallback) {
			return null;
		}
		List<String> leafMatches = tableIndex.leafTables().getOrDefault(normalizeLeafIdentifier(requestedTableName),
				List.of());
		if (leafMatches.size() == 1) {
			return leafMatches.get(0);
		}
		if (leafMatches.size() > 1) {
			throw new IllegalArgumentException("Table '%s' is ambiguous across datasource tables: %s"
				.formatted(requestedTableName, leafMatches));
		}
		return null;
	}

	private boolean isQualifiedIdentifier(String value) {
		return normalizeIdentifier(value).contains(".");
	}

	private String normalizeIdentifier(String value) {
		String normalized = Optional.ofNullable(value).orElse("").trim();
		normalized = stripWrapping(normalized, "`");
		normalized = stripWrapping(normalized, "\"");
		normalized = stripWrapping(normalized, "[", "]");
		return normalized.toLowerCase(Locale.ROOT);
	}

	private String normalizeLeafIdentifier(String value) {
		String normalized = normalizeIdentifier(value);
		if (normalized.contains(".")) {
			return normalized.substring(normalized.lastIndexOf('.') + 1);
		}
		return normalized;
	}

	private String stripWrapping(String value, String wrapper) {
		return stripWrapping(value, wrapper, wrapper);
	}

	private String stripWrapping(String value, String prefix, String suffix) {
		String normalized = value;
		if (normalized.startsWith(prefix)) {
			normalized = normalized.substring(prefix.length());
		}
		if (normalized.endsWith(suffix)) {
			normalized = normalized.substring(0, normalized.length() - suffix.length());
		}
		return normalized;
	}

	private record TableResolutionIndex(Map<String, List<String>> exactTables, Map<String, List<String>> leafTables) {
	}

}
