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
package com.alibaba.cloud.ai.dataagent.service.semantic.runtime;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasourceColumn;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticColumn;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticRelation;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticTable;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceColumnsMapper;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceTablesMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticColumnMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticRelationMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticTableMapper;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.schema.TableMetadataService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SemanticContextFactory {

	private final DatasourceService datasourceService;

	private final AccessorFactory accessorFactory;

	private final TableMetadataService tableMetadataService;

	private final AgentDatasourceMapper agentDatasourceMapper;

	private final AgentDatasourceTablesMapper tablesMapper;

	private final AgentDatasourceColumnsMapper columnsMapper;

	private final SemanticTableMapper semanticTableMapper;

	private final SemanticColumnMapper semanticColumnMapper;

	private final SemanticRelationMapper semanticRelationMapper;

	private final SemanticManager semanticManager;

	private final SemanticResolver semanticResolver;

	public SemanticContext create(Long agentId, Integer datasourceId) throws Exception {
		return create(agentId, datasourceId, List.of());
	}

	public SemanticContext create(Long agentId, Integer datasourceId, List<String> requestedTables) throws Exception {
		Datasource datasource = datasourceService.getDatasourceById(datasourceId);
		if (datasource == null) {
			return new SemanticContext(List.of(), Map.of(), Map.of(), Map.of());
		}
		List<String> datasourceTables = datasourceService.getDatasourceTables(datasourceId);
		AgentDatasource agentDatasource = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		List<String> visibleTables = resolveVisibleTables(datasourceTables, agentDatasource);
		List<String> targetTables = requestedTables == null || requestedTables.isEmpty()
				? visibleTables : resolveRequestedTables(requestedTables, visibleTables);
		if (targetTables.isEmpty()) {
			return new SemanticContext(List.of(), Map.of(), Map.of(), Map.of());
		}
		Map<String, List<String>> selectedColumnsByTable = loadSelectedColumns(agentDatasource);
		PhysicalSemanticSnapshot physicalSnapshot = loadPhysicalSnapshot(datasource, targetTables, visibleTables,
				selectedColumnsByTable);
		List<SemanticTable> semanticTables = semanticTableMapper.listActiveByAgentIdAndDatasourceId(agentId, datasourceId);
		List<SemanticColumn> semanticColumns = semanticColumnMapper.selectActiveByAgentIdAndDatasourceId(agentId, datasourceId);
		List<SemanticRelation> semanticRelations = semanticRelationMapper.listActiveByAgentIdAndDatasourceId(agentId,
				datasourceId);
		return buildContext(targetTables, physicalSnapshot, semanticTables, semanticColumns, semanticRelations);
	}

	private SemanticContext buildContext(List<String> targetTables, PhysicalSemanticSnapshot physicalSnapshot,
			List<SemanticTable> semanticTables, List<SemanticColumn> semanticColumns, List<SemanticRelation> semanticRelations) {
		Map<String, SemanticTable> semanticTableByName = semanticTables.stream()
			.collect(Collectors.toMap(table -> semanticManager.normalizeName(table.getTableName()), table -> table,
					(left, right) -> left, LinkedHashMap::new));
		Map<String, Map<String, SemanticColumn>> semanticColumnsByTable = new LinkedHashMap<>();
		for (SemanticColumn semanticColumn : semanticColumns) {
			semanticColumnsByTable
				.computeIfAbsent(semanticManager.normalizeName(semanticColumn.getTableName()), key -> new LinkedHashMap<>())
				.put(semanticManager.normalizeName(semanticColumn.getColumnName()), semanticColumn);
		}
		Map<String, ResolvedSemanticTable> tablesByName = new LinkedHashMap<>();
		List<ResolvedSemanticTable> resolvedTables = new ArrayList<>();
		Map<String, List<ResolvedSemanticColumn>> columnsByTable = new LinkedHashMap<>();
		for (String tableName : targetTables) {
			String normalizedTableName = semanticManager.normalizeName(tableName);
			TableInfoBO physicalTable = physicalSnapshot.tablesByName().get(normalizedTableName);
			SemanticTable semanticTable = semanticTableByName.get(normalizedTableName);
			ResolvedSemanticTable resolvedTable = semanticResolver.resolveTable(physicalTable, semanticTable);
			if (resolvedTable == null || !resolvedTable.visible()) {
				continue;
			}
			resolvedTables.add(resolvedTable);
			tablesByName.put(normalizedTableName, resolvedTable);
			Map<String, ColumnInfoBO> physicalColumns = physicalSnapshot.columnsByTable().getOrDefault(normalizedTableName, Map.of());
			Map<String, SemanticColumn> semanticColumnMap = semanticColumnsByTable.getOrDefault(normalizedTableName, Map.of());
			List<String> mergedColumnNames = semanticManager.mergeColumnNames(new ArrayList<>(physicalColumns.keySet()),
					new ArrayList<>(semanticColumnMap.keySet()));
			List<ResolvedSemanticColumn> resolvedColumns = mergedColumnNames.stream().map(columnKey -> {
				ColumnInfoBO physicalColumn = physicalColumns.get(columnKey);
				SemanticColumn semanticColumn = semanticColumnMap.get(columnKey);
				return semanticResolver.resolveColumn(resolvedTable.tableName(), physicalColumn, semanticColumn);
			}).filter(Objects::nonNull).filter(ResolvedSemanticColumn::visible).toList();
			columnsByTable.put(normalizedTableName, resolvedColumns);
		}
		Map<String, List<ResolvedSemanticRelation>> relationsByTable = buildRelationsIndex(physicalSnapshot.foreignKeys(),
				semanticRelations, new LinkedHashSet<>(tablesByName.keySet()));
		return new SemanticContext(resolvedTables, tablesByName, columnsByTable, relationsByTable);
	}

	private Map<String, List<ResolvedSemanticRelation>> buildRelationsIndex(List<ForeignKeyInfoBO> physicalRelations,
			List<SemanticRelation> semanticRelations, Set<String> visibleTables) {
		Map<String, ResolvedSemanticRelation> merged = new LinkedHashMap<>();
		for (ForeignKeyInfoBO physicalRelation : physicalRelations) {
			ResolvedSemanticRelation relation = semanticResolver.resolvePhysicalRelation(physicalRelation);
			if (isRelationVisible(relation, visibleTables)) {
				merged.put(buildRelationKey(relation), relation);
			}
		}
		for (SemanticRelation semanticRelation : semanticRelations) {
			ResolvedSemanticRelation relation = semanticResolver.resolveSemanticRelation(semanticRelation);
			if (isRelationVisible(relation, visibleTables)) {
				merged.putIfAbsent(buildRelationKey(relation), relation);
			}
		}
		Map<String, List<ResolvedSemanticRelation>> relationsByTable = new LinkedHashMap<>();
		for (ResolvedSemanticRelation relation : merged.values()) {
			String sourceKey = semanticManager.normalizeName(relation.sourceTableName());
			relationsByTable.computeIfAbsent(sourceKey, key -> new ArrayList<>()).add(relation);
			String targetKey = semanticManager.normalizeName(relation.targetTableName());
			if (!targetKey.equals(sourceKey)) {
				relationsByTable.computeIfAbsent(targetKey, key -> new ArrayList<>()).add(relation);
			}
		}
		relationsByTable.replaceAll((tableName, relations) -> List.copyOf(relations));
		return Map.copyOf(relationsByTable);
	}

	private boolean isRelationVisible(ResolvedSemanticRelation relation, Set<String> visibleTables) {
		return visibleTables.contains(semanticManager.normalizeName(relation.sourceTableName()))
				&& visibleTables.contains(semanticManager.normalizeName(relation.targetTableName()));
	}

	private String buildRelationKey(ResolvedSemanticRelation relation) {
		return semanticManager.normalizeName(relation.sourceTableName()) + "|" + relation.sourceColumnSummary() + "|"
				+ semanticManager.normalizeName(relation.targetTableName()) + "|" + relation.targetColumnSummary();
	}

	private PhysicalSemanticSnapshot loadPhysicalSnapshot(Datasource datasource, List<String> targetTables,
			List<String> visibleTables, Map<String, List<String>> selectedColumnsByTable) throws Exception {
		DbConfigBO dbConfig = datasourceService.getDbConfig(datasource);
		Accessor accessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		List<TableInfoBO> physicalTables = accessor.fetchTables(dbConfig,
				DbQueryParameter.from(dbConfig).setSchema(dbConfig.getSchema()).setTables(targetTables));
		List<ForeignKeyInfoBO> foreignKeys = accessor.showForeignKeys(dbConfig,
				DbQueryParameter.from(dbConfig).setSchema(dbConfig.getSchema()).setTables(visibleTables));
		tableMetadataService.batchEnrichTableMetadata(physicalTables, dbConfig, buildForeignKeyMap(foreignKeys));
		semanticManager.applyVisibleColumnRestrictions(physicalTables, selectedColumnsByTable);
		Map<String, TableInfoBO> tablesByName = physicalTables.stream()
			.collect(Collectors.toMap(table -> semanticManager.normalizeName(table.getName()), table -> table,
					(left, right) -> left, LinkedHashMap::new));
		Map<String, Map<String, ColumnInfoBO>> columnsByTable = new LinkedHashMap<>();
		for (TableInfoBO table : physicalTables) {
			Map<String, ColumnInfoBO> columns = Optional.ofNullable(table.getColumns())
				.orElse(List.of())
				.stream()
				.collect(Collectors.toMap(column -> semanticManager.normalizeName(column.getName()), column -> column,
						(left, right) -> left, LinkedHashMap::new));
			columnsByTable.put(semanticManager.normalizeName(table.getName()), columns);
		}
		return new PhysicalSemanticSnapshot(Map.copyOf(tablesByName), Map.copyOf(columnsByTable), List.copyOf(foreignKeys));
	}

	private Map<String, List<String>> buildForeignKeyMap(List<ForeignKeyInfoBO> foreignKeys) {
		Map<String, List<String>> map = new LinkedHashMap<>();
		for (ForeignKeyInfoBO fk : Optional.ofNullable(foreignKeys).orElse(List.of())) {
			String key = fk.getTable() + "." + fk.getColumn() + "=" + fk.getReferencedTable() + "." + fk.getReferencedColumn();
			map.computeIfAbsent(fk.getTable(), ignored -> new ArrayList<>()).add(key);
			map.computeIfAbsent(fk.getReferencedTable(), ignored -> new ArrayList<>()).add(key);
		}
		return map;
	}

	private Map<String, List<String>> loadSelectedColumns(AgentDatasource agentDatasource) {
		if (agentDatasource == null) {
			return Map.of();
		}
		List<AgentDatasourceColumn> rows = Optional.ofNullable(columnsMapper.getAgentDatasourceColumns(agentDatasource.getId()))
			.orElse(List.of());
		Map<String, List<String>> columnsByTable = new LinkedHashMap<>();
		for (AgentDatasourceColumn row : rows) {
			columnsByTable.computeIfAbsent(row.getTableName(), key -> new ArrayList<>()).add(row.getColumnName());
		}
		columnsByTable.replaceAll((tableName, columns) -> List.copyOf(columns));
		return Map.copyOf(columnsByTable);
	}

	private List<String> resolveVisibleTables(List<String> datasourceTables, AgentDatasource agentDatasource) {
		if (agentDatasource == null) {
			return List.copyOf(datasourceTables);
		}
		List<String> selectedTables = Optional.ofNullable(tablesMapper.getAgentDatasourceTables(agentDatasource.getId()))
			.orElse(List.of());
		return selectedTables.isEmpty() ? List.copyOf(datasourceTables)
				: resolveRequestedTables(selectedTables, datasourceTables);
	}

	private List<String> resolveRequestedTables(Collection<String> requestedTables, List<String> visibleTables) {
		TableIndex index = buildTableIndex(visibleTables);
		List<String> resolved = new ArrayList<>();
		Set<String> normalizedResolved = new LinkedHashSet<>();
		for (String requestedTable : Optional.ofNullable(requestedTables).orElse(List.of())) {
			String actualTable = resolveTableName(requestedTable, index);
			if (actualTable == null) {
				continue;
			}
			if (normalizedResolved.add(semanticManager.normalizeName(actualTable))) {
				resolved.add(actualTable);
			}
		}
		return resolved;
	}

	private String resolveTableName(String requestedTable, TableIndex index) {
		String normalized = semanticManager.normalizeName(requestedTable);
		if (StringUtils.isBlank(normalized)) {
			return null;
		}
		List<String> exactMatches = index.byName().getOrDefault(normalized, List.of());
		if (!exactMatches.isEmpty()) {
			return exactMatches.get(0);
		}
		List<String> leafMatches = index.byLeafName().getOrDefault(semanticManager.normalizeLeafName(requestedTable), List.of());
		return leafMatches.isEmpty() ? null : leafMatches.get(0);
	}

	private TableIndex buildTableIndex(List<String> tables) {
		Map<String, List<String>> byName = new LinkedHashMap<>();
		Map<String, List<String>> byLeafName = new LinkedHashMap<>();
		for (String table : Optional.ofNullable(tables).orElse(List.of())) {
			byName.computeIfAbsent(semanticManager.normalizeName(table), key -> new ArrayList<>()).add(table);
			byLeafName.computeIfAbsent(semanticManager.normalizeLeafName(table), key -> new ArrayList<>()).add(table);
		}
		byName.replaceAll((key, value) -> List.copyOf(value));
		byLeafName.replaceAll((key, value) -> List.copyOf(value));
		return new TableIndex(Map.copyOf(byName), Map.copyOf(byLeafName));
	}

	private record TableIndex(Map<String, List<String>> byName, Map<String, List<String>> byLeafName) {
	}

	private record PhysicalSemanticSnapshot(Map<String, TableInfoBO> tablesByName,
			Map<String, Map<String, ColumnInfoBO>> columnsByTable, List<ForeignKeyInfoBO> foreignKeys) {
	}

}
