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
package com.alibaba.cloud.ai.dataagent.agentscope.tool.datasource;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.util.SqlUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatasourceExplorerService {

	private static final int DEFAULT_LIMIT = 20;

	private static final int MAX_LIMIT = 200;

	private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\blimit\\s+\\d+\\b");

	private static final Pattern TOP_PATTERN = Pattern.compile("(?i)\\bselect\\s+top\\s+\\d+\\b");

	private static final Pattern FETCH_FIRST_PATTERN = Pattern
		.compile("(?i)\\bfetch\\s+first\\s+\\d+\\s+rows\\s+only\\b");

	private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
	};

	private final AgentDatasourceService agentDatasourceService;

	private final DatasourceService datasourceService;

	private final SchemaService schemaService;

	private final AccessorFactory accessorFactory;

	private final ObjectMapper objectMapper;

	public DatasourceExplorerResult execute(String agentId, DatasourceExplorerRequest request) throws Exception {
		if (request == null || request.getAction() == null) {
			throw new IllegalArgumentException("Datasource explorer request.action 不能为空");
		}
		ExplorerContext context = resolveContext(agentId);
		return switch (request.getAction()) {
			case LIST_TABLES -> listTables(context, request);
			case FIND_TABLES -> findTables(context, request);
			case GET_TABLE_SCHEMA -> getTableSchema(context, request);
			case GET_RELATED_TABLES -> getRelatedTables(context, request);
			case PREVIEW_ROWS -> previewRows(context, request);
			case SEARCH -> search(context, request);
		};
	}

	private DatasourceExplorerResult listTables(ExplorerContext context, DatasourceExplorerRequest request) {
		int limit = normalizeLimit(request.getLimit());
		Map<String, Document> tableDocumentMap = loadTableDocumentMap(context, context.visibleTables());
		List<Map<String, Object>> tables = context.visibleTables()
			.stream()
			.sorted(String.CASE_INSENSITIVE_ORDER)
			.map(tableName -> toTableEntry(tableName, tableDocumentMap.get(normalizeTableName(tableName)),
					context.explicitSelectedTables(),
					context.relationsByTable().getOrDefault(normalizeTableName(tableName), List.of())))
			.limit(limit)
			.toList();
		return baseResult(context, DatasourceExplorerAction.LIST_TABLES, tables.size() + " tables available")
			.tables(tables)
			.nextSuggestedActions(List.of("get_table_schema", "preview_rows", "find_tables"))
			.truncated(context.visibleTables().size() > limit)
			.build();
	}

	private DatasourceExplorerResult findTables(ExplorerContext context, DatasourceExplorerRequest request) {
		int limit = normalizeLimit(request.getLimit());
		String query = StringUtils.trimToEmpty(request.getQuery()).toLowerCase(Locale.ROOT);
		Map<String, Document> tableDocumentMap = loadTableDocumentMap(context, context.visibleTables());
		List<Map<String, Object>> matchedTables = context.visibleTables()
			.stream()
			.map(tableName -> toTableEntry(tableName, tableDocumentMap.get(normalizeTableName(tableName)),
					context.explicitSelectedTables(),
					context.relationsByTable().getOrDefault(normalizeTableName(tableName), List.of())))
			.filter(table -> query.isEmpty() || containsQuery(table, query))
			.limit(limit)
			.toList();
		String summary = query.isEmpty() ? "Returned visible tables without query filter"
				: "Matched %d tables for query '%s'".formatted(matchedTables.size(), request.getQuery());
		return baseResult(context, DatasourceExplorerAction.FIND_TABLES, summary).tables(matchedTables)
			.nextSuggestedActions(List.of("get_table_schema", "preview_rows"))
			.truncated(matchedTables.size() >= limit)
			.build();
	}

	private DatasourceExplorerResult getTableSchema(ExplorerContext context, DatasourceExplorerRequest request)
			throws Exception {
		String tableName = requireSingleTableName(request);
		assertVisibleTable(context, tableName);
		List<ColumnInfoBO> columns = context.accessor()
			.showColumns(context.dbConfig(),
					DbQueryParameter.from(context.dbConfig())
						.setSchema(context.dbConfig().getSchema())
						.setTable(tableName));
		Document tableDocument = loadTableDocumentMap(context, List.of(tableName)).get(normalizeTableName(tableName));
		List<Map<String, Object>> columnEntries = columns.stream().map(this::toColumnEntry).toList();
		List<UnifiedRelation> relations = filterRelations(context, tableName);
		List<Map<String, Object>> relationEntries = relations.stream()
			.map(this::toRelationEntry)
			.toList();
		Map<String, Object> tableEntry = toTableEntry(tableName, tableDocument, context.explicitSelectedTables(),
				relations);
		return baseResult(context, DatasourceExplorerAction.GET_TABLE_SCHEMA,
				"Loaded schema for table '%s'".formatted(tableName))
			.tables(List.of(tableEntry))
			.columns(columnEntries)
			.relations(relationEntries)
			.nextSuggestedActions(List.of("preview_rows", "search", "get_related_tables"))
			.build();
	}

	private DatasourceExplorerResult getRelatedTables(ExplorerContext context, DatasourceExplorerRequest request) {
		String tableName = requireSingleTableName(request);
		assertVisibleTable(context, tableName);
		List<UnifiedRelation> relations = filterRelations(context, tableName);
		List<Map<String, Object>> relationEntries = relations.stream().map(this::toRelationEntry).toList();
		Set<String> relatedTables = relations.stream()
			.flatMap(relation -> Arrays.stream(new String[] { relation.sourceTable(), relation.targetTable() }))
			.filter(candidate -> !normalizeTableName(candidate).equals(normalizeTableName(tableName)))
			.filter(candidate -> context.visibleTableNameSet().contains(normalizeTableName(candidate)))
			.collect(Collectors.toCollection(LinkedHashSet::new));
		Map<String, Document> tableDocumentMap = loadTableDocumentMap(context, new ArrayList<>(relatedTables));
		List<Map<String, Object>> tableEntries = relatedTables.stream()
			.map(relatedTable -> toTableEntry(relatedTable, tableDocumentMap.get(normalizeTableName(relatedTable)),
					context.explicitSelectedTables(),
					context.relationsByTable().getOrDefault(normalizeTableName(relatedTable), List.of())))
			.toList();
		return baseResult(context, DatasourceExplorerAction.GET_RELATED_TABLES,
				"Found %d related tables for '%s'".formatted(tableEntries.size(), tableName))
			.tables(tableEntries)
			.relations(relationEntries)
			.nextSuggestedActions(List.of("get_table_schema", "preview_rows"))
			.build();
	}

	private DatasourceExplorerResult previewRows(ExplorerContext context, DatasourceExplorerRequest request)
			throws Exception {
		String tableName = requireSingleTableName(request);
		assertVisibleTable(context, tableName);
		int limit = normalizeLimit(request.getLimit());
		String sql = SqlUtil.buildSelectSql(context.dbConfig().getDialectType(), tableName, "*", limit);
		ResultSetBO resultSet = executeSql(context, sql);
		return baseResult(context, DatasourceExplorerAction.PREVIEW_ROWS,
				"Previewed %d rows from '%s'".formatted(resultSet.getData().size(), tableName))
			.tables(List.of(Map.of("name", tableName)))
			.columns(toColumnHeaders(resultSet))
			.rows(toRows(resultSet))
			.sql(sql)
			.nextSuggestedActions(List.of("get_table_schema", "search"))
			.truncated(resultSet.getData().size() >= limit)
			.build();
	}

	private DatasourceExplorerResult search(ExplorerContext context, DatasourceExplorerRequest request)
			throws Exception {
		String rawSql = StringUtils.trimToNull(request.getSql());
		if (rawSql == null) {
			throw new IllegalArgumentException("search action 必须提供 sql");
		}
		int limit = normalizeLimit(request.getLimit());
		String guardedSql = guardReadonlySql(context, rawSql, limit);
		ResultSetBO resultSet = executeSql(context, guardedSql);
		return baseResult(context, DatasourceExplorerAction.SEARCH,
				"Executed readonly search and returned %d rows".formatted(resultSet.getData().size()))
			.columns(toColumnHeaders(resultSet))
			.rows(toRows(resultSet))
			.sql(guardedSql)
			.nextSuggestedActions(List.of("get_table_schema", "preview_rows", "find_tables"))
			.truncated(resultSet.getData().size() >= limit)
			.build();
	}

	private ExplorerContext resolveContext(String agentId) throws Exception {
		Long numericAgentId = parseAgentId(agentId);
		AgentDatasource agentDatasource = agentDatasourceService.getCurrentAgentDatasource(numericAgentId);
		Datasource datasource = agentDatasource.getDatasource() != null ? agentDatasource.getDatasource()
				: datasourceService.getDatasourceById(agentDatasource.getDatasourceId());
		if (datasource == null) {
			throw new IllegalStateException("Active datasource not found for agent " + agentId);
		}
		DbConfigBO dbConfig = datasourceService.getDbConfig(datasource);
		Accessor accessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		List<String> explicitSelectedTables = agentDatasource.getSelectTables() == null ? List.of()
				: agentDatasource.getSelectTables();
		List<String> visibleTables = explicitSelectedTables.isEmpty()
				? datasourceService.getDatasourceTables(datasource.getId()) : explicitSelectedTables;
		Set<String> visibleTableNameSet = visibleTables.stream()
			.map(this::normalizeTableName)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		List<LogicalRelation> logicalRelations = datasourceService.getLogicalRelations(datasource.getId());
		List<ForeignKeyInfoBO> physicalRelations = loadPhysicalRelations(accessor, dbConfig, visibleTables);
		List<UnifiedRelation> unifiedRelations = buildUnifiedRelations(visibleTableNameSet, physicalRelations,
				logicalRelations == null ? List.of() : logicalRelations);
		return new ExplorerContext(agentDatasource, datasource, dbConfig, accessor, List.copyOf(visibleTables),
				Set.copyOf(visibleTableNameSet), List.copyOf(explicitSelectedTables),
				List.copyOf(unifiedRelations), indexRelationsByTable(unifiedRelations));
	}

	private List<ForeignKeyInfoBO> loadPhysicalRelations(Accessor accessor, DbConfigBO dbConfig, List<String> tables) {
		try {
			List<ForeignKeyInfoBO> foreignKeys = accessor.showForeignKeys(dbConfig,
					DbQueryParameter.from(dbConfig).setSchema(dbConfig.getSchema()).setTables(tables));
			return foreignKeys == null ? List.of() : foreignKeys;
		}
		catch (Exception ex) {
			return List.of();
		}
	}

	private Long parseAgentId(String agentId) {
		if (!StringUtils.isNumeric(agentId)) {
			throw new IllegalArgumentException("Datasource explorer 当前仅支持数值型 agentId");
		}
		return Long.valueOf(agentId);
	}

	private ResultSetBO executeSql(ExplorerContext context, String sql) throws Exception {
		ResultSetBO resultSet = context.accessor()
			.executeSqlAndReturnObject(context.dbConfig(),
					DbQueryParameter.from(context.dbConfig()).setSchema(context.dbConfig().getSchema()).setSql(sql));
		if (resultSet == null) {
			return ResultSetBO.builder().column(List.of()).data(List.of()).errorMsg(null).build();
		}
		if (resultSet.getErrorMsg() != null) {
			throw new IllegalStateException(resultSet.getErrorMsg());
		}
		if (resultSet.getColumn() == null) {
			resultSet.setColumn(List.of());
		}
		if (resultSet.getData() == null) {
			resultSet.setData(List.of());
		}
		return resultSet;
	}

	private String guardReadonlySql(ExplorerContext context, String rawSql, int limit) {
		String compactSql = stripTrailingSemicolons(rawSql);
		if (compactSql.isEmpty()) {
			throw new IllegalArgumentException("SQL 不能为空");
		}
		Statement statement = parseSingleSelectStatement(compactSql);
		Set<String> referencedTables = extractReferencedTables(statement);
		if (!referencedTables.isEmpty()) {
			List<String> forbiddenTables = referencedTables.stream()
				.filter(table -> !context.visibleTableNameSet().contains(normalizeTableName(table)))
				.toList();
			if (!forbiddenTables.isEmpty()) {
				throw new IllegalArgumentException("SQL 引用了当前 Agent 不可见的表: " + forbiddenTables);
			}
		}
		if (hasLimit(compactSql)) {
			return compactSql;
		}
		return wrapLimitSql(context.dbConfig().getDialectType(), compactSql, limit);
	}

	private String stripTrailingSemicolons(String sql) {
		String trimmed = StringUtils.trimToEmpty(sql);
		while (trimmed.endsWith(";")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
		}
		return trimmed;
	}

	private boolean hasLimit(String sql) {
		return LIMIT_PATTERN.matcher(sql).find() || TOP_PATTERN.matcher(sql).find()
				|| FETCH_FIRST_PATTERN.matcher(sql).find();
	}

	private Statement parseSingleSelectStatement(String sql) {
		List<Statement> statements;
		try {
			statements = CCJSqlParserUtil.parseStatements(sql).getStatements();
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("SQL 解析失败，请检查语法后重试", ex);
		}
		if (statements == null || statements.isEmpty()) {
			throw new IllegalArgumentException("SQL 不能为空");
		}
		if (statements.size() > 1) {
			throw new IllegalArgumentException("仅允许执行单条 SELECT / WITH 查询，请勿拼接多条语句");
		}
		Statement statement = statements.get(0);
		if (!(statement instanceof Select)) {
			throw new IllegalArgumentException("只允许执行 SELECT / WITH 查询");
		}
		return statement;
	}

	private Set<String> extractReferencedTables(Statement statement) {
		try {
			return new TablesNamesFinder().getTableList(statement)
				.stream()
				.map(this::normalizeTableName)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("无法从 SQL 中提取引用表", ex);
		}
	}

	private String wrapLimitSql(String dialectType, String sql, int limit) {
		String normalizedDialect = StringUtils.defaultString(dialectType).toLowerCase(Locale.ROOT);
		if (normalizedDialect.contains("sqlserver") || normalizedDialect.contains("sql_server")) {
			return "SELECT TOP %d * FROM (%s) dataagent_safe_limit".formatted(limit, sql);
		}
		if (normalizedDialect.contains("oracle")) {
			return "SELECT * FROM (%s) dataagent_safe_limit FETCH FIRST %d ROWS ONLY".formatted(sql, limit);
		}
		return "SELECT * FROM (%s) dataagent_safe_limit LIMIT %d".formatted(sql, limit);
	}

	private void assertVisibleTable(ExplorerContext context, String tableName) {
		if (!context.visibleTableNameSet().contains(normalizeTableName(tableName))) {
			throw new IllegalArgumentException("Table '%s' is not visible for current agent".formatted(tableName));
		}
	}

	private String requireSingleTableName(DatasourceExplorerRequest request) {
		if (StringUtils.isNotBlank(request.getTableName())) {
			return request.getTableName().trim();
		}
		if (request.getTableNames() != null && request.getTableNames().size() == 1) {
			return StringUtils.trimToEmpty(request.getTableNames().get(0));
		}
		throw new IllegalArgumentException("当前 action 必须提供 tableName");
	}

	private int normalizeLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(limit, MAX_LIMIT);
	}

	private Map<String, Document> loadTableDocumentMap(ExplorerContext context, List<String> tableNames) {
		if (tableNames == null || tableNames.isEmpty()) {
			return Collections.emptyMap();
		}
		try {
			return schemaService.getTableDocuments(context.datasource().getId(), tableNames)
				.stream()
				.collect(Collectors.toMap(doc -> normalizeTableName(String.valueOf(doc.getMetadata().get("name"))),
						doc -> doc, (left, right) -> left, LinkedHashMap::new));
		}
		catch (Exception ex) {
			return Collections.emptyMap();
		}
	}

	private Map<String, Object> toTableEntry(String tableName, Document tableDocument,
			List<String> explicitSelectedTables, List<UnifiedRelation> relations) {
		Map<String, Object> tableEntry = new LinkedHashMap<>();
		tableEntry.put("name", tableName);
		tableEntry.put("selected", explicitSelectedTables.isEmpty() || explicitSelectedTables.stream()
			.anyMatch(candidate -> normalizeTableName(candidate).equals(normalizeTableName(tableName))));
		String unifiedForeignKeys = summarizeRelations(relations);
		if (tableDocument != null) {
			tableEntry.put("schema", tableDocument.getMetadata().getOrDefault("schema", ""));
			tableEntry.put("description", tableDocument.getMetadata().getOrDefault("description", ""));
			tableEntry.put("primaryKeys", tableDocument.getMetadata().getOrDefault("primaryKey", List.of()));
			tableEntry.put("foreignKeys",
					StringUtils.defaultIfBlank(unifiedForeignKeys,
							String.valueOf(tableDocument.getMetadata().getOrDefault("foreignKey", ""))));
		}
		else if (StringUtils.isNotBlank(unifiedForeignKeys)) {
			tableEntry.put("foreignKeys", unifiedForeignKeys);
		}
		return tableEntry;
	}

	private Map<String, Object> toColumnEntry(ColumnInfoBO columnInfo) {
		Map<String, Object> columnEntry = new LinkedHashMap<>();
		columnEntry.put("name", columnInfo.getName());
		columnEntry.put("type", columnInfo.getType());
		columnEntry.put("description", StringUtils.defaultString(columnInfo.getDescription()));
		columnEntry.put("primary", columnInfo.isPrimary());
		columnEntry.put("notnull", columnInfo.isNotnull());
		columnEntry.put("samples", parseSamples(columnInfo.getSamples()));
		return columnEntry;
	}

	private List<String> parseSamples(String samples) {
		if (StringUtils.isBlank(samples)) {
			return List.of();
		}
		try {
			return objectMapper.readValue(samples, STRING_LIST_TYPE);
		}
		catch (Exception ex) {
			return List.of(samples);
		}
	}

	private List<UnifiedRelation> filterRelations(ExplorerContext context, String tableName) {
		return context.relationsByTable().getOrDefault(normalizeTableName(tableName), List.of());
	}

	private Map<String, Object> toRelationEntry(UnifiedRelation relation) {
		Map<String, Object> relationEntry = new LinkedHashMap<>();
		relationEntry.put("sourceTable", relation.sourceTable());
		relationEntry.put("sourceColumn", relation.sourceColumn());
		relationEntry.put("targetTable", relation.targetTable());
		relationEntry.put("targetColumn", relation.targetColumn());
		relationEntry.put("relationType", relation.relationType());
		relationEntry.put("description", relation.description());
		relationEntry.put("sourceType", relation.sourceType());
		relationEntry.put("virtual", relation.virtual());
		relationEntry.put("declaredInDatabase", relation.declaredInDatabase());
		return relationEntry;
	}

	private List<UnifiedRelation> buildUnifiedRelations(Set<String> visibleTableNameSet,
			List<ForeignKeyInfoBO> physicalRelations, List<LogicalRelation> logicalRelations) {
		Map<String, UnifiedRelation> relationMap = new LinkedHashMap<>();
		for (ForeignKeyInfoBO physicalRelation : physicalRelations) {
			UnifiedRelation relation = toUnifiedRelation(physicalRelation);
			if (isVisibleRelation(visibleTableNameSet, relation)) {
				mergeRelation(relationMap, relation);
			}
		}
		for (LogicalRelation logicalRelation : logicalRelations) {
			UnifiedRelation relation = toUnifiedRelation(logicalRelation);
			if (isVisibleRelation(visibleTableNameSet, relation)) {
				mergeRelation(relationMap, relation);
			}
		}
		return relationMap.values()
			.stream()
			.sorted(Comparator.comparing((UnifiedRelation relation) -> normalizeTableName(relation.sourceTable()))
				.thenComparing(relation -> StringUtils.defaultString(relation.sourceColumn()))
				.thenComparing(relation -> normalizeTableName(relation.targetTable()))
				.thenComparing(relation -> StringUtils.defaultString(relation.targetColumn())))
			.toList();
	}

	private UnifiedRelation toUnifiedRelation(ForeignKeyInfoBO relation) {
		return new UnifiedRelation(relation.getTable(), relation.getColumn(), relation.getReferencedTable(),
				relation.getReferencedColumn(), StringUtils.EMPTY, StringUtils.EMPTY, "physical", false, true);
	}

	private UnifiedRelation toUnifiedRelation(LogicalRelation relation) {
		return new UnifiedRelation(relation.getSourceTableName(), relation.getSourceColumnName(),
				relation.getTargetTableName(), relation.getTargetColumnName(),
				StringUtils.defaultString(relation.getRelationType()), StringUtils.defaultString(relation.getDescription()),
				"logical", true, false);
	}

	private boolean isVisibleRelation(Set<String> visibleTableNameSet, UnifiedRelation relation) {
		return visibleTableNameSet.contains(normalizeTableName(relation.sourceTable()))
				&& visibleTableNameSet.contains(normalizeTableName(relation.targetTable()));
	}

	private void mergeRelation(Map<String, UnifiedRelation> relationMap, UnifiedRelation incoming) {
		String relationKey = buildRelationKey(incoming);
		UnifiedRelation existing = relationMap.get(relationKey);
		if (existing == null) {
			relationMap.put(relationKey, incoming);
			return;
		}
		if (existing.declaredInDatabase() && !incoming.declaredInDatabase()) {
			relationMap.put(relationKey, mergeRelation(existing, incoming));
			return;
		}
		if (!existing.declaredInDatabase() && incoming.declaredInDatabase()) {
			relationMap.put(relationKey, mergeRelation(incoming, existing));
			return;
		}
		relationMap.put(relationKey, mergeRelation(existing, incoming));
	}

	private UnifiedRelation mergeRelation(UnifiedRelation preferred, UnifiedRelation supplement) {
		return new UnifiedRelation(preferred.sourceTable(), preferred.sourceColumn(), preferred.targetTable(),
				preferred.targetColumn(), StringUtils.firstNonBlank(preferred.relationType(), supplement.relationType()),
				StringUtils.firstNonBlank(preferred.description(), supplement.description()), preferred.sourceType(),
				preferred.virtual(), preferred.declaredInDatabase());
	}

	private String buildRelationKey(UnifiedRelation relation) {
		return normalizeTableName(relation.sourceTable()) + "|" + StringUtils.defaultString(relation.sourceColumn())
				+ "|" + normalizeTableName(relation.targetTable()) + "|"
				+ StringUtils.defaultString(relation.targetColumn());
	}

	private Map<String, List<UnifiedRelation>> indexRelationsByTable(List<UnifiedRelation> relations) {
		Map<String, List<UnifiedRelation>> relationIndex = new LinkedHashMap<>();
		for (UnifiedRelation relation : relations) {
			relationIndex.computeIfAbsent(normalizeTableName(relation.sourceTable()), key -> new ArrayList<>())
				.add(relation);
			String targetKey = normalizeTableName(relation.targetTable());
			if (!targetKey.equals(normalizeTableName(relation.sourceTable()))) {
				relationIndex.computeIfAbsent(targetKey, key -> new ArrayList<>()).add(relation);
			}
		}
		Map<String, List<UnifiedRelation>> immutableIndex = new LinkedHashMap<>();
		relationIndex.forEach((tableName, tableRelations) -> immutableIndex.put(tableName, List.copyOf(tableRelations)));
		return Map.copyOf(immutableIndex);
	}

	private String summarizeRelations(List<UnifiedRelation> relations) {
		return relations.stream()
			.map(relation -> relation.sourceTable() + "." + relation.sourceColumn() + "=" + relation.targetTable()
					+ "." + relation.targetColumn())
			.distinct()
			.collect(Collectors.joining("、"));
	}

	private List<Map<String, Object>> toColumnHeaders(ResultSetBO resultSet) {
		return resultSet.getColumn().stream().map(column -> Map.<String, Object>of("name", column)).toList();
	}

	private List<Map<String, Object>> toRows(ResultSetBO resultSet) {
		return resultSet.getData().stream().map(row -> {
			Map<String, Object> mappedRow = new LinkedHashMap<>();
			mappedRow.putAll(row);
			return mappedRow;
		}).toList();
	}

	private boolean containsQuery(Map<String, Object> table, String query) {
		return table.values()
			.stream()
			.filter(String.class::isInstance)
			.map(String.class::cast)
			.map(value -> value.toLowerCase(Locale.ROOT))
			.anyMatch(value -> value.contains(query));
	}

	private String normalizeTableName(String tableName) {
		String normalized = StringUtils.trimToEmpty(tableName);
		normalized = StringUtils.removeStart(normalized, "`");
		normalized = StringUtils.removeEnd(normalized, "`");
		normalized = StringUtils.removeStart(normalized, "\"");
		normalized = StringUtils.removeEnd(normalized, "\"");
		normalized = StringUtils.removeStart(normalized, "[");
		normalized = StringUtils.removeEnd(normalized, "]");
		if (normalized.contains(".")) {
			normalized = normalized.substring(normalized.lastIndexOf('.') + 1);
		}
		return normalized.toLowerCase(Locale.ROOT);
	}

	private DatasourceExplorerResult.DatasourceExplorerResultBuilder baseResult(ExplorerContext context,
			DatasourceExplorerAction action, String summary) {
		return DatasourceExplorerResult.builder()
			.datasource(context.datasource().getName())
			.action(action.name())
			.summary(summary);
	}

	private record ExplorerContext(AgentDatasource agentDatasource, Datasource datasource, DbConfigBO dbConfig,
			Accessor accessor, List<String> visibleTables, Set<String> visibleTableNameSet,
			List<String> explicitSelectedTables, List<UnifiedRelation> unifiedRelations,
			Map<String, List<UnifiedRelation>> relationsByTable) {
	}

	private record UnifiedRelation(String sourceTable, String sourceColumn, String targetTable, String targetColumn,
			String relationType, String description, String sourceType, boolean virtual,
			boolean declaredInDatabase) {
	}

}
