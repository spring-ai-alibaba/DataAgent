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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.LateralSubSelect;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.TableFunction;
import net.sf.jsqlparser.statement.select.WithItem;
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

	private static final String HIDDEN_FIELD_INFERENCE_WARNING =
			" Answer strictly from returned columns only. Never infer hidden fields from visible values such as email local parts, IDs, codes, or aliases.";

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
			.map(tableName -> toTableEntry(context, tableName, tableDocumentMap.get(normalizeTableName(tableName)),
					filterRelations(context, tableName)))
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
			.map(tableName -> toTableEntry(context, tableName, tableDocumentMap.get(normalizeTableName(tableName)),
					filterRelations(context, tableName)))
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
		String tableName = resolveVisibleTableName(context, requireSingleTableName(request));
		List<ColumnInfoBO> columns = context.accessor()
			.showColumns(context.dbConfig(),
					DbQueryParameter.from(context.dbConfig())
						.setSchema(context.dbConfig().getSchema())
						.setTable(tableName));
		Document tableDocument = loadTableDocumentMap(context, List.of(tableName)).get(normalizeTableName(tableName));
		Map<String, Document> columnDocumentMap = loadColumnDocumentMap(context, tableName);
		List<Map<String, Object>> columnEntries = applyVisibleColumnFilter(context, tableName, columns).stream()
			.map(column -> toColumnEntry(column, columnDocumentMap.get(normalizeColumnName(column.getName()))))
			.toList();
		List<UnifiedRelation> relations = filterRelations(context, tableName);
		List<Map<String, Object>> relationEntries = relations.stream().map(this::toRelationEntry).toList();
		Map<String, Object> tableEntry = toTableEntry(context, tableName, tableDocument, relations);
		return baseResult(context, DatasourceExplorerAction.GET_TABLE_SCHEMA,
				"Loaded schema for table '%s'".formatted(tableName))
			.tables(List.of(tableEntry))
			.columns(columnEntries)
			.relations(relationEntries)
			.nextSuggestedActions(List.of("preview_rows", "search", "get_related_tables"))
			.build();
	}

	private DatasourceExplorerResult getRelatedTables(ExplorerContext context, DatasourceExplorerRequest request) {
		String tableName = resolveVisibleTableName(context, requireSingleTableName(request));
		List<UnifiedRelation> relations = filterRelations(context, tableName);
		List<Map<String, Object>> relationEntries = relations.stream().map(this::toRelationEntry).toList();
		Set<String> relatedTables = relations.stream()
			.flatMap(relation -> Arrays.stream(new String[] { relation.sourceTable(), relation.targetTable() }))
			.filter(candidate -> !normalizeTableName(candidate).equals(normalizeTableName(tableName)))
			.filter(candidate -> context.visibleTableNameSet().contains(normalizeTableName(candidate)))
			.collect(Collectors.toCollection(LinkedHashSet::new));
		Map<String, Document> tableDocumentMap = loadTableDocumentMap(context, new ArrayList<>(relatedTables));
		List<Map<String, Object>> tableEntries = relatedTables.stream()
			.map(relatedTable -> toTableEntry(context, relatedTable, tableDocumentMap.get(normalizeTableName(relatedTable)),
					filterRelations(context, relatedTable)))
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
		String tableName = resolveVisibleTableName(context, requireSingleTableName(request));
		int limit = normalizeLimit(request.getLimit());
		String sql = SqlUtil.buildSelectSql(context.dbConfig().getDialectType(),
				SqlUtil.quoteIdentifier(context.dbConfig().getDialectType(), tableName),
				resolvePreviewColumnSelection(context, tableName), limit);
		ResultSetBO resultSet = executeSql(context, sql);
		return baseResult(context, DatasourceExplorerAction.PREVIEW_ROWS,
				("Previewed %d rows from '%s'".formatted(resultSet.getData().size(), tableName))
					+ HIDDEN_FIELD_INFERENCE_WARNING)
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
		SqlGuardedQuery guardedQuery = guardReadonlySql(context, rawSql, limit);
		ResultSetBO resultSet = filterResultSet(executeSql(context, guardedQuery.sql()), guardedQuery);
		return baseResult(context, DatasourceExplorerAction.SEARCH,
				("Executed readonly search and returned %d rows".formatted(resultSet.getData().size()))
					+ HIDDEN_FIELD_INFERENCE_WARNING)
			.columns(toColumnHeaders(resultSet))
			.rows(toRows(resultSet))
			.sql(guardedQuery.sql())
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
		Map<String, List<String>> visibleTablesByName = indexTablesByIdentity(visibleTables);
		Map<String, List<String>> visibleTablesByLeafName = indexTablesByLeafName(visibleTables);
		Set<String> visibleTableNameSet = visibleTables.stream()
			.map(this::normalizeTableName)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		Map<String, List<String>> visibleColumnsByTable = buildVisibleColumnsByTable(agentDatasource, visibleTablesByName,
				visibleTablesByLeafName);
		Map<String, Set<String>> visibleColumnNameSetByTable = visibleColumnsByTable.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey,
					entry -> entry.getValue()
						.stream()
						.map(this::normalizeColumnName)
						.collect(Collectors.toCollection(LinkedHashSet::new)),
					(left, right) -> left, LinkedHashMap::new));
		List<LogicalRelation> logicalRelations = datasourceService.getLogicalRelations(datasource.getId());
		List<ForeignKeyInfoBO> physicalRelations = loadPhysicalRelations(accessor, dbConfig, visibleTables);
		List<UnifiedRelation> unifiedRelations = buildUnifiedRelations(visibleTablesByName, visibleTablesByLeafName,
				physicalRelations, logicalRelations == null ? List.of() : logicalRelations);
		return new ExplorerContext(agentDatasource, datasource, dbConfig, accessor, List.copyOf(visibleTables),
				Set.copyOf(visibleTableNameSet), toImmutableListIndex(visibleTablesByName),
				toImmutableListIndex(visibleTablesByLeafName), List.copyOf(explicitSelectedTables),
				Map.copyOf(visibleColumnsByTable), toImmutableSetIndex(visibleColumnNameSetByTable),
				Set.copyOf(visibleColumnsByTable.keySet()), List.copyOf(unifiedRelations),
				indexRelationsByTable(unifiedRelations));
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

	private SqlGuardedQuery guardReadonlySql(ExplorerContext context, String rawSql, int limit) {
		String compactSql = stripTrailingSemicolons(rawSql);
		if (compactSql.isEmpty()) {
			throw new IllegalArgumentException("SQL 不能为空");
		}
		Statement statement = parseSingleSelectStatement(compactSql);
		SqlValidationResult validationResult = new SqlColumnAccessValidator(context).validate((Select) statement);
		String guardedSql = hasLimit(compactSql) ? compactSql
				: wrapLimitSql(context.dbConfig().getDialectType(), compactSql, limit);
		return new SqlGuardedQuery(guardedSql, validationResult.referencedTables(), validationResult.allowedResultHeaders());
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

	private Map<String, Document> loadColumnDocumentMap(ExplorerContext context, String tableName) {
		try {
			return schemaService.getColumnDocumentsByTableName(context.datasource().getId(), List.of(tableName))
				.stream()
				.collect(Collectors.toMap(doc -> normalizeColumnName(String.valueOf(doc.getMetadata().get("name"))),
						doc -> doc, (left, right) -> left, LinkedHashMap::new));
		}
		catch (Exception ex) {
			return Collections.emptyMap();
		}
	}

	private Map<String, Object> toTableEntry(ExplorerContext context, String tableName, Document tableDocument,
			List<UnifiedRelation> relations) {
		Map<String, Object> tableEntry = new LinkedHashMap<>();
		tableEntry.put("name", tableName);
		tableEntry.put("selected", isSelectedTable(context, tableName));
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

	private Map<String, Object> toColumnEntry(ColumnInfoBO columnInfo, Document columnDocument) {
		Map<String, Object> columnEntry = new LinkedHashMap<>();
		columnEntry.put("name", columnInfo.getName());
		columnEntry.put("type", columnInfo.getType());
		columnEntry.put("description", resolveColumnDescription(columnInfo, columnDocument));
		columnEntry.put("primary", columnInfo.isPrimary());
		columnEntry.put("notnull", columnInfo.isNotnull());
		columnEntry.put("samples", resolveColumnSamples(columnInfo, columnDocument));
		return columnEntry;
	}

	private String resolveColumnDescription(ColumnInfoBO columnInfo, Document columnDocument) {
		if (StringUtils.isNotBlank(columnInfo.getDescription())) {
			return columnInfo.getDescription();
		}
		if (columnDocument == null) {
			return StringUtils.EMPTY;
		}
		return String.valueOf(columnDocument.getMetadata().getOrDefault("description", ""));
	}

	private List<String> resolveColumnSamples(ColumnInfoBO columnInfo, Document columnDocument) {
		if (StringUtils.isNotBlank(columnInfo.getSamples())) {
			return parseSamples(columnInfo.getSamples());
		}
		if (columnDocument == null) {
			return List.of();
		}
		return parseSamples(String.valueOf(columnDocument.getMetadata().getOrDefault("samples", "")));
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
		return context.relationsByTable()
			.getOrDefault(normalizeTableName(tableName), List.of())
			.stream()
			.filter(relation -> isRelationVisible(context, relation))
			.toList();
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

	private List<UnifiedRelation> buildUnifiedRelations(Map<String, List<String>> visibleTablesByName,
			Map<String, List<String>> visibleTablesByLeafName,
			List<ForeignKeyInfoBO> physicalRelations, List<LogicalRelation> logicalRelations) {
		Map<String, UnifiedRelation> relationMap = new LinkedHashMap<>();
		for (ForeignKeyInfoBO physicalRelation : physicalRelations) {
			UnifiedRelation relation = canonicalizeRelation(visibleTablesByName, visibleTablesByLeafName,
					toUnifiedRelation(physicalRelation));
			if (relation != null) {
				mergeRelation(relationMap, relation);
			}
		}
		for (LogicalRelation logicalRelation : logicalRelations) {
			UnifiedRelation relation = canonicalizeRelation(visibleTablesByName, visibleTablesByLeafName,
					toUnifiedRelation(logicalRelation));
			if (relation != null) {
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

	private UnifiedRelation canonicalizeRelation(Map<String, List<String>> visibleTablesByName,
			Map<String, List<String>> visibleTablesByLeafName, UnifiedRelation relation) {
		Optional<String> sourceTable = findVisibleTableName(visibleTablesByName, visibleTablesByLeafName,
				relation.sourceTable(), true);
		Optional<String> targetTable = findVisibleTableName(visibleTablesByName, visibleTablesByLeafName,
				relation.targetTable(), true);
		if (sourceTable.isEmpty() || targetTable.isEmpty()) {
			return null;
		}
		return new UnifiedRelation(sourceTable.get(), relation.sourceColumn(), targetTable.get(), relation.targetColumn(),
				relation.relationType(), relation.description(), relation.sourceType(), relation.virtual(),
				relation.declaredInDatabase());
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

	private ResultSetBO filterResultSet(ResultSetBO resultSet, SqlGuardedQuery guardedQuery) {
		Set<String> allowedHeaders = guardedQuery.allowedResultHeaders();
		if (allowedHeaders == null || allowedHeaders.isEmpty()) {
			return resultSet;
		}
		List<String> originalColumns = Optional.ofNullable(resultSet.getColumn()).orElse(List.of());
		List<Integer> keptIndexes = new ArrayList<>();
		List<String> keptColumns = new ArrayList<>();
		for (int index = 0; index < originalColumns.size(); index++) {
			String columnName = originalColumns.get(index);
			if (allowedHeaders.contains(normalizeColumnName(columnName))) {
				keptIndexes.add(index);
				keptColumns.add(columnName);
			}
		}
		if (keptIndexes.size() == originalColumns.size()) {
			return resultSet;
		}
		List<Map<String, String>> filteredRows = Optional.ofNullable(resultSet.getData()).orElse(List.of()).stream().map(row -> {
			Map<String, String> filteredRow = new LinkedHashMap<>();
			for (Integer keptIndex : keptIndexes) {
				String columnName = originalColumns.get(keptIndex);
				filteredRow.put(columnName, row.get(columnName));
			}
			return filteredRow;
		}).toList();
		resultSet.setColumn(keptColumns);
		resultSet.setData(filteredRows);
		return resultSet;
	}

	private boolean containsQuery(Map<String, Object> table, String query) {
		return table.values()
			.stream()
			.filter(String.class::isInstance)
			.map(String.class::cast)
			.map(value -> value.toLowerCase(Locale.ROOT))
			.anyMatch(value -> value.contains(query));
	}

	private List<ColumnInfoBO> applyVisibleColumnFilter(ExplorerContext context, String tableName, List<ColumnInfoBO> columns) {
		return Optional.ofNullable(columns)
			.orElse(List.of())
			.stream()
			.filter(column -> isColumnVisible(context, tableName, column.getName()))
			.toList();
	}

	private String resolvePreviewColumnSelection(ExplorerContext context, String tableName) {
		List<String> visibleColumns = context.visibleColumnsByTable().get(normalizeTableName(tableName));
		if (visibleColumns == null) {
			return "*";
		}
		if (visibleColumns.isEmpty()) {
			throw new IllegalArgumentException("表 '%s' 当前没有可预览字段，请先调整字段级可见性配置".formatted(tableName));
		}
		return visibleColumns.stream()
			.map(columnName -> SqlUtil.quoteIdentifier(context.dbConfig().getDialectType(), columnName))
			.collect(Collectors.joining(", "));
	}

	private boolean isRelationVisible(ExplorerContext context, UnifiedRelation relation) {
		return isColumnVisible(context, relation.sourceTable(), relation.sourceColumn())
				&& isColumnVisible(context, relation.targetTable(), relation.targetColumn());
	}

	private boolean isColumnVisible(ExplorerContext context, String tableName, String columnName) {
		String normalizedTableName = normalizeTableName(tableName);
		if (!context.columnRestrictedTables().contains(normalizedTableName)) {
			return true;
		}
		Set<String> visibleColumns = context.visibleColumnNameSetByTable().get(normalizedTableName);
		return visibleColumns != null && visibleColumns.contains(normalizeColumnName(columnName));
	}

	private Map<String, List<String>> buildVisibleColumnsByTable(AgentDatasource agentDatasource,
			Map<String, List<String>> visibleTablesByName, Map<String, List<String>> visibleTablesByLeafName) {
		Map<String, List<String>> selectedColumns = Optional.ofNullable(agentDatasource.getSelectColumns()).orElse(Map.of());
		Map<String, List<String>> visibleColumnsByTable = new LinkedHashMap<>();
		selectedColumns.forEach((tableName, columns) -> {
			Optional<String> resolvedTableName = findVisibleTableName(visibleTablesByName, visibleTablesByLeafName,
					tableName, true);
			if (resolvedTableName.isEmpty()) {
				return;
			}
			List<String> sanitizedColumns = Optional.ofNullable(columns)
				.orElse(List.of())
				.stream()
				.filter(StringUtils::isNotBlank)
				.map(String::trim)
				.collect(Collectors.toCollection(LinkedHashSet::new))
				.stream()
				.toList();
			if (!sanitizedColumns.isEmpty()) {
				visibleColumnsByTable.put(normalizeTableName(resolvedTableName.get()), List.copyOf(sanitizedColumns));
			}
		});
		return visibleColumnsByTable;
	}

	private Map<String, List<String>> toImmutableListIndex(Map<String, List<String>> source) {
		Map<String, List<String>> immutableIndex = new LinkedHashMap<>();
		source.forEach((key, value) -> immutableIndex.put(key, List.copyOf(value)));
		return Map.copyOf(immutableIndex);
	}

	private Map<String, Set<String>> toImmutableSetIndex(Map<String, Set<String>> source) {
		Map<String, Set<String>> immutableIndex = new LinkedHashMap<>();
		source.forEach((key, value) -> immutableIndex.put(key, Set.copyOf(value)));
		return Map.copyOf(immutableIndex);
	}

	private Map<String, List<String>> indexTablesByIdentity(List<String> tableNames) {
		return indexTables(tableNames, false);
	}

	private Map<String, List<String>> indexTablesByLeafName(List<String> tableNames) {
		return indexTables(tableNames, true);
	}

	private Map<String, List<String>> indexTables(List<String> tableNames, boolean leafOnly) {
		Map<String, LinkedHashSet<String>> index = new LinkedHashMap<>();
		for (String tableName : Optional.ofNullable(tableNames).orElse(List.of())) {
			if (StringUtils.isBlank(tableName)) {
				continue;
			}
			String normalizedKey = leafOnly ? normalizeTableLeafName(tableName) : normalizeTableName(tableName);
			index.computeIfAbsent(normalizedKey, key -> new LinkedHashSet<>()).add(tableName);
		}
		Map<String, List<String>> immutableIndex = new LinkedHashMap<>();
		index.forEach((key, value) -> immutableIndex.put(key, List.copyOf(value)));
		return Map.copyOf(immutableIndex);
	}

	private String resolveVisibleTableName(ExplorerContext context, String tableName) {
		return findVisibleTableName(context.visibleTablesByName(), context.visibleTablesByLeafName(), tableName, false)
			.orElseThrow(() -> buildInvisibleTableException(context, tableName));
	}

	private Optional<String> findVisibleTableName(Map<String, List<String>> visibleTablesByName,
			Map<String, List<String>> visibleTablesByLeafName, String tableName, boolean allowQualifiedFallback) {
		String normalizedTableName = normalizeTableName(tableName);
		List<String> exactMatches = visibleTablesByName.getOrDefault(normalizedTableName, List.of());
		if (exactMatches.size() == 1) {
			return Optional.of(exactMatches.get(0));
		}
		if (exactMatches.size() > 1) {
			throw new IllegalArgumentException(
					"Table '%s' maps to multiple visible tables: %s".formatted(tableName, String.join(", ", exactMatches)));
		}
		if (isQualifiedIdentifier(tableName) && !allowQualifiedFallback) {
			return Optional.empty();
		}
		List<String> leafMatches = visibleTablesByLeafName.getOrDefault(normalizeTableLeafName(tableName), List.of());
		if (leafMatches.size() == 1) {
			return Optional.of(leafMatches.get(0));
		}
		if (leafMatches.size() > 1) {
			throw new IllegalArgumentException("Table '%s' is ambiguous across visible tables: %s"
				.formatted(tableName, String.join(", ", leafMatches)));
		}
		return Optional.empty();
	}

	private IllegalArgumentException buildInvisibleTableException(ExplorerContext context, String tableName) {
		return new IllegalArgumentException("Table '%s' is not visible for current agent. Visible tables: %s"
			.formatted(tableName, String.join(", ", context.visibleTables())));
	}

	private boolean isSelectedTable(ExplorerContext context, String tableName) {
		if (context.explicitSelectedTables().isEmpty()) {
			return true;
		}
		String normalizedTableName = normalizeTableName(tableName);
		return context.explicitSelectedTables()
			.stream()
			.map(this::normalizeTableName)
			.anyMatch(normalizedTableName::equals);
	}

	private boolean isQualifiedIdentifier(String value) {
		return normalizeIdentifier(value).contains(".");
	}

	private String normalizeIdentifier(String value) {
		String normalized = StringUtils.trimToEmpty(value);
		normalized = StringUtils.removeStart(normalized, "`");
		normalized = StringUtils.removeEnd(normalized, "`");
		normalized = StringUtils.removeStart(normalized, "\"");
		normalized = StringUtils.removeEnd(normalized, "\"");
		normalized = StringUtils.removeStart(normalized, "[");
		normalized = StringUtils.removeEnd(normalized, "]");
		return normalized.toLowerCase(Locale.ROOT);
	}

	private String normalizeTableName(String tableName) {
		return normalizeIdentifier(tableName);
	}

	private String normalizeTableLeafName(String tableName) {
		String normalized = normalizeIdentifier(tableName);
		if (normalized.contains(".")) {
			return normalized.substring(normalized.lastIndexOf('.') + 1);
		}
		return normalized;
	}

	private String normalizeColumnName(String columnName) {
		return normalizeTableLeafName(columnName);
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
			Map<String, List<String>> visibleTablesByName, Map<String, List<String>> visibleTablesByLeafName,
			List<String> explicitSelectedTables, Map<String, List<String>> visibleColumnsByTable,
			Map<String, Set<String>> visibleColumnNameSetByTable, Set<String> columnRestrictedTables,
			List<UnifiedRelation> unifiedRelations, Map<String, List<UnifiedRelation>> relationsByTable) {
	}

	private record UnifiedRelation(String sourceTable, String sourceColumn, String targetTable, String targetColumn,
			String relationType, String description, String sourceType, boolean virtual,
			boolean declaredInDatabase) {
	}

	private record SqlGuardedQuery(String sql, Set<String> referencedTables, Set<String> allowedResultHeaders) {
	}

	private record SqlValidationResult(Set<String> referencedTables, Set<String> allowedResultHeaders) {
	}

	private record SourceBinding(String referenceName, String tableName) {

		boolean isBaseTable() {
			return StringUtils.isNotBlank(tableName);
		}
	}

	private record SelectScope(Map<String, SourceBinding> sourcesByReference, List<SourceBinding> baseSources) {

		SourceBinding resolve(String reference) {
			return sourcesByReference.get(reference);
		}
	}

	private final class SqlColumnAccessValidator {

		private final ExplorerContext context;

		private SqlColumnAccessValidator(ExplorerContext context) {
			this.context = context;
		}

		private SqlValidationResult validate(Select select) {
			Set<String> referencedTables = new LinkedHashSet<>();
			validateSelect(select, new LinkedHashSet<>(), referencedTables);
			return new SqlValidationResult(Set.copyOf(referencedTables), extractAllowedResultHeaders(select));
		}

		private void validateSelect(Select select, Set<String> cteNames, Set<String> referencedTables) {
			Set<String> nextCteNames = new LinkedHashSet<>(cteNames);
			if (select.getWithItemsList() != null) {
				for (WithItem withItem : select.getWithItemsList()) {
					if (withItem.getAlias() != null && StringUtils.isNotBlank(withItem.getAlias().getName())) {
						nextCteNames.add(normalizeTableName(withItem.getAlias().getName()));
					}
				}
				for (WithItem withItem : select.getWithItemsList()) {
					validateSelect(withItem.getSelect(), nextCteNames, referencedTables);
				}
			}
			if (select instanceof PlainSelect plainSelect) {
				validatePlainSelect(plainSelect, nextCteNames, referencedTables);
				return;
			}
			if (select instanceof SetOperationList setOperationList) {
				for (Select childSelect : Optional.ofNullable(setOperationList.getSelects()).orElse(List.of())) {
					validateSelect(childSelect, nextCteNames, referencedTables);
				}
				return;
			}
			if (select instanceof ParenthesedSelect parenthesedSelect) {
				validateSelect(parenthesedSelect.getSelect(), nextCteNames, referencedTables);
				return;
			}
			throw new IllegalArgumentException("当前 SQL 包含暂不支持的查询结构: " + select.getClass().getSimpleName());
		}

		private void validatePlainSelect(PlainSelect plainSelect, Set<String> cteNames, Set<String> referencedTables) {
			SelectScope scope = buildScope(plainSelect, cteNames, referencedTables);
			Set<String> selectAliases = extractSelectAliases(plainSelect.getSelectItems());
			for (SelectItem<?> selectItem : Optional.ofNullable(plainSelect.getSelectItems()).orElse(List.of())) {
				validateExpression(selectItem.getExpression(), scope, cteNames, "SELECT", Set.of());
			}
			validateExpression(plainSelect.getWhere(), scope, cteNames, "WHERE", Set.of());
			validateExpression(plainSelect.getHaving(), scope, cteNames, "HAVING", selectAliases);
			validateExpression(plainSelect.getQualify(), scope, cteNames, "QUALIFY", selectAliases);
			if (plainSelect.getGroupBy() != null && plainSelect.getGroupBy().getGroupByExpressions() != null) {
				for (Object groupByExpression : Optional.ofNullable(plainSelect.getGroupBy().getGroupByExpressions().getExpressions())
					.orElse(List.of())) {
					if (groupByExpression instanceof Expression expression) {
						validateExpression(expression, scope, cteNames, "GROUP BY", selectAliases);
					}
				}
			}
			for (OrderByElement orderByElement : Optional.ofNullable(plainSelect.getOrderByElements()).orElse(List.of())) {
				validateExpression(orderByElement.getExpression(), scope, cteNames, "ORDER BY", selectAliases);
			}
		}

		private SelectScope buildScope(PlainSelect plainSelect, Set<String> cteNames, Set<String> referencedTables) {
			Map<String, SourceBinding> sourcesByReference = new LinkedHashMap<>();
			List<SourceBinding> baseSources = new ArrayList<>();
			addFromItemSources(plainSelect.getFromItem(), cteNames, referencedTables, sourcesByReference, baseSources);
			for (Join join : Optional.ofNullable(plainSelect.getJoins()).orElse(List.of())) {
				addFromItemSources(join.getRightItem(), cteNames, referencedTables, sourcesByReference, baseSources);
				if (join.getUsingColumns() != null && !join.getUsingColumns().isEmpty()) {
					throw new IllegalArgumentException("当前 SQL 使用了 JOIN ... USING 语法。字段级可见性校验要求改写成显式 ON alias.column = alias.column");
				}
				for (Expression onExpression : Optional.ofNullable(join.getOnExpressions()).orElse(List.of())) {
					validateExpression(onExpression, new SelectScope(Map.copyOf(sourcesByReference), List.copyOf(baseSources)),
							cteNames, "JOIN ON", Set.of());
				}
			}
			return new SelectScope(Map.copyOf(sourcesByReference), List.copyOf(baseSources));
		}

		private void addFromItemSources(FromItem fromItem, Set<String> cteNames, Set<String> referencedTables,
				Map<String, SourceBinding> sourcesByReference, List<SourceBinding> baseSources) {
			if (fromItem == null) {
				return;
			}
			if (fromItem instanceof Table table) {
				String normalizedTableReference = normalizeTableName(extractTableReference(table));
				String aliasName = table.getAlias() == null ? null : normalizeTableName(table.getAlias().getName());
				if (cteNames.contains(normalizedTableReference)) {
					registerSource(sourcesByReference,
							new SourceBinding(StringUtils.defaultIfBlank(aliasName, normalizedTableReference), null));
					return;
				}
				String resolvedTableName = resolveVisibleTableName(context, extractTableReference(table));
				referencedTables.add(normalizeTableName(resolvedTableName));
				SourceBinding sourceBinding = new SourceBinding(StringUtils.defaultIfBlank(aliasName,
						normalizeTableName(resolvedTableName)), resolvedTableName);
				registerSource(sourcesByReference, sourceBinding);
				registerSource(sourcesByReference,
						new SourceBinding(normalizeTableName(resolvedTableName), resolvedTableName));
				String tableLeafName = normalizeTableLeafName(resolvedTableName);
				if (!tableLeafName.equals(normalizeTableName(resolvedTableName))) {
					registerSource(sourcesByReference, new SourceBinding(tableLeafName, resolvedTableName));
				}
				baseSources.add(sourceBinding);
				return;
			}
			if (fromItem instanceof LateralSubSelect lateralSubSelect) {
				validateSelect(lateralSubSelect.getSelect(), cteNames, referencedTables);
				registerDerivedSource(lateralSubSelect, sourcesByReference);
				return;
			}
			if (fromItem instanceof ParenthesedSelect parenthesedSelect) {
				validateSelect(parenthesedSelect.getSelect(), cteNames, referencedTables);
				registerDerivedSource(parenthesedSelect, sourcesByReference);
				return;
			}
			if (fromItem instanceof ParenthesedFromItem parenthesedFromItem) {
				Map<String, SourceBinding> nestedSources = new LinkedHashMap<>();
				List<SourceBinding> nestedBaseSources = new ArrayList<>();
				addFromItemSources(parenthesedFromItem.getFromItem(), cteNames, referencedTables, nestedSources, nestedBaseSources);
				for (Join join : Optional.ofNullable(parenthesedFromItem.getJoins()).orElse(List.of())) {
					addFromItemSources(join.getRightItem(), cteNames, referencedTables, nestedSources, nestedBaseSources);
					if (join.getUsingColumns() != null && !join.getUsingColumns().isEmpty()) {
						throw new IllegalArgumentException("当前 SQL 使用了 JOIN ... USING 语法。字段级可见性校验要求改写成显式 ON alias.column = alias.column");
					}
					for (Expression onExpression : Optional.ofNullable(join.getOnExpressions()).orElse(List.of())) {
						validateExpression(onExpression,
								new SelectScope(Map.copyOf(nestedSources), List.copyOf(nestedBaseSources)),
								cteNames, "JOIN ON", Set.of());
					}
				}
				if (parenthesedFromItem.getAlias() != null && StringUtils.isNotBlank(parenthesedFromItem.getAlias().getName())) {
					registerDerivedSource(parenthesedFromItem, sourcesByReference);
				}
				else {
					nestedSources.values().forEach(binding -> registerSource(sourcesByReference, binding));
					baseSources.addAll(nestedBaseSources);
				}
				return;
			}
			if (fromItem instanceof TableFunction tableFunction) {
				registerDerivedSource(tableFunction, sourcesByReference);
				return;
			}
			throw new IllegalArgumentException("当前 SQL 包含暂不支持的 FROM 结构: " + fromItem.getClass().getSimpleName());
		}

		private void registerDerivedSource(FromItem fromItem, Map<String, SourceBinding> sourcesByReference) {
			if (fromItem.getAlias() == null || StringUtils.isBlank(fromItem.getAlias().getName())) {
				return;
			}
			registerSource(sourcesByReference, new SourceBinding(normalizeTableName(fromItem.getAlias().getName()), null));
		}

		private void registerSource(Map<String, SourceBinding> sourcesByReference, SourceBinding sourceBinding) {
			SourceBinding existingBinding = sourcesByReference.get(sourceBinding.referenceName());
			if (existingBinding != null && !Objects.equals(existingBinding.tableName(), sourceBinding.tableName())) {
				throw new IllegalArgumentException("Table reference '%s' is ambiguous in current SQL scope; please use aliases"
					.formatted(sourceBinding.referenceName()));
			}
			sourcesByReference.put(sourceBinding.referenceName(), sourceBinding);
		}

		private void validateExpression(Expression expression, SelectScope scope, Set<String> cteNames, String clause,
				Set<String> allowedAliases) {
			if (expression == null) {
				return;
			}
			expression.accept(new ExpressionVisitorAdapter() {
				@Override
				public void visit(Function function) {
					if (function.isAllColumns() && !"count".equalsIgnoreCase(function.getName())) {
						throw new IllegalArgumentException("子句 %s 中检测到 %s(*)。字段级可见性校验禁止使用除 COUNT(*) 外的星号聚合，请显式列出字段"
								.formatted(clause, StringUtils.defaultIfBlank(function.getName(), "function")));
					}
					super.visit(function);
				}

				@Override
				public void visit(AllColumns allColumns) {
					throw new IllegalArgumentException("子句 %s 中检测到 SELECT *。请改成显式列名，避免越权读取隐藏字段".formatted(clause));
				}

				@Override
				public void visit(AllTableColumns allTableColumns) {
					throw new IllegalArgumentException(
							"子句 %s 中检测到 %s.*。请改成显式列名，避免越权读取隐藏字段".formatted(clause, allTableColumns.getTable()));
				}

				@Override
				public void visit(Column column) {
					validateColumnReference(scope, clause, column, allowedAliases);
				}

				@Override
				public void visit(ParenthesedSelect parenthesedSelect) {
					validateSelect(parenthesedSelect.getSelect(), cteNames, new LinkedHashSet<>());
				}

				@Override
				public void visit(Select select) {
					validateSelect(select, cteNames, new LinkedHashSet<>());
				}
			});
		}

		private void validateColumnReference(SelectScope scope, String clause, Column column, Set<String> allowedAliases) {
			String normalizedColumnName = normalizeColumnName(column.getColumnName());
			if (StringUtils.isBlank(normalizedColumnName)) {
				throw new IllegalArgumentException("子句 %s 中存在无法识别的字段引用: %s".formatted(clause, column));
			}
			Table table = column.getTable();
			String tableReference = table == null ? StringUtils.EMPTY : normalizeTableName(extractTableReference(table));
			if (StringUtils.isBlank(tableReference)) {
				if (allowedAliases.contains(normalizedColumnName)) {
					return;
				}
				if (scope.baseSources().isEmpty()) {
					return;
				}
				if (scope.baseSources().size() > 1) {
					throw new IllegalArgumentException(
							"子句 %s 中的字段 '%s' 没有带表前缀。当前 SQL 涉及多张基础表，无法安全判断字段归属，请改成 alias.%s"
								.formatted(clause, column.getColumnName(), column.getColumnName()));
				}
				SourceBinding sourceBinding = scope.baseSources().get(0);
				assertVisibleColumn(sourceBinding.tableName(), column.getColumnName(), clause, column.toString());
				return;
			}
			SourceBinding sourceBinding = scope.resolve(tableReference);
			if (sourceBinding == null) {
				throw new IllegalArgumentException(
						"子句 %s 中引用了未知表/别名 '%s'。请检查 SQL 中的表别名是否和 FROM/JOIN 定义一致".formatted(clause, table.getName()));
			}
			if (!sourceBinding.isBaseTable()) {
				return;
			}
			assertVisibleColumn(sourceBinding.tableName(), column.getColumnName(), clause, column.toString());
		}

		private void assertVisibleColumn(String tableName, String columnName, String clause, String expression) {
			String normalizedTableName = normalizeTableName(tableName);
			if (!context.columnRestrictedTables().contains(normalizedTableName)) {
				return;
			}
			Set<String> visibleColumns = context.visibleColumnNameSetByTable().get(normalizedTableName);
			if (visibleColumns == null || !visibleColumns.contains(normalizeColumnName(columnName))) {
				String visibleColumnSummary = Optional.ofNullable(context.visibleColumnsByTable().get(normalizedTableName))
					.orElse(List.of())
					.stream()
					.collect(Collectors.joining(", "));
				throw new IllegalArgumentException(
						"子句 %s 中的字段引用 '%s' 不被允许。表 '%s' 已启用字段级可见性控制，仅允许字段: [%s]"
							.formatted(clause, expression, tableName, visibleColumnSummary));
			}
		}

		private Set<String> extractSelectAliases(List<SelectItem<?>> selectItems) {
			return Optional.ofNullable(selectItems)
				.orElse(List.of())
				.stream()
				.map(SelectItem::getAlias)
				.filter(alias -> alias != null && StringUtils.isNotBlank(alias.getName()))
				.map(alias -> normalizeColumnName(alias.getName()))
				.collect(Collectors.toCollection(LinkedHashSet::new));
		}

		private Set<String> extractAllowedResultHeaders(Select select) {
			if (!(select instanceof PlainSelect plainSelect)) {
				return null;
			}
			Set<String> headers = new LinkedHashSet<>();
			for (SelectItem<?> selectItem : Optional.ofNullable(plainSelect.getSelectItems()).orElse(List.of())) {
				Expression expression = selectItem.getExpression();
				if (expression instanceof AllColumns || expression instanceof AllTableColumns) {
					return null;
				}
				if (selectItem.getAlias() != null && StringUtils.isNotBlank(selectItem.getAlias().getName())) {
					headers.add(normalizeColumnName(selectItem.getAlias().getName()));
					continue;
				}
				if (expression instanceof Column column) {
					headers.add(normalizeColumnName(column.getColumnName()));
					continue;
				}
				return null;
			}
			return headers;
		}

		private String extractTableReference(Table table) {
			String fullyQualifiedName = table == null ? StringUtils.EMPTY : table.getFullyQualifiedName();
			if (StringUtils.isNotBlank(fullyQualifiedName)) {
				return fullyQualifiedName;
			}
			return table == null ? StringUtils.EMPTY : table.getName();
		}
	}

}
