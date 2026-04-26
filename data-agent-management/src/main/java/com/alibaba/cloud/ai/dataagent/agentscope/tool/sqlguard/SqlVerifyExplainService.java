/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.agentscope.tool.sqlguard;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.util.SqlUtil;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class SqlVerifyExplainService {

	private static final String ACTION_SQL_VERIFY = "SQL_VERIFY";

	private static final String ACTION_DATA_PROFILE = "DATA_PROFILE";

	private static final int DEFAULT_PROFILE_LIMIT = 5;

	private static final int MAX_PROFILE_LIMIT = 20;

	private static final int DEFAULT_PROFILE_COLUMN_COUNT = 3;

    private static final Pattern AGGREGATE_PATTERN = Pattern
        .compile("(?i)\\b(count|sum|avg|average|min|max)\\s*\\(([^)]*)\\)\\s*(?:as\\s+([a-zA-Z0-9_]+))?");

    private static final Pattern GROUP_BY_PATTERN = Pattern.compile("(?is)\\bgroup\\s+by\\b");

    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("(?is)\\border\\s+by\\b");

    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?is)\\blimit\\s+\\d+\\b");

    private static final Pattern TOP_PATTERN = Pattern.compile("(?is)\\bselect\\s+top\\s+\\d+\\b");

    private static final Pattern FETCH_FIRST_PATTERN = Pattern
        .compile("(?is)\\bfetch\\s+first\\s+\\d+\\s+rows\\s+only\\b");

    private static final Pattern DISTINCT_PATTERN = Pattern.compile("(?is)\\bselect\\s+distinct\\b|count\\s*\\(\\s*distinct\\b");

    private static final Pattern DATE_LITERAL_PATTERN = Pattern
        .compile("\\b\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\b|\\b\\d{6,8}\\b");

    private static final Pattern TIME_FUNCTION_PATTERN = Pattern.compile(
        "(?is)\\b(current_date|current_timestamp|now\\s*\\(|curdate\\s*\\(|date\\s*\\(|date_trunc\\s*\\(|strftime\\s*\\(|to_date\\s*\\(|to_timestamp\\s*\\(|datediff\\s*\\(|dateadd\\s*\\(|timestampdiff\\s*\\(|interval\\b)");

    private static final Pattern WHERE_PATTERN = Pattern.compile("(?is)\\bwhere\\b");

    private static final Pattern DESC_PATTERN = Pattern.compile("(?is)\\border\\s+by\\b.+?\\bdesc\\b");

    private static final Pattern ASC_PATTERN = Pattern.compile("(?is)\\border\\s+by\\b.+?\\basc\\b");

    private static final Pattern TOP_N_QUERY_PATTERN = Pattern
        .compile("(?i)(?:\\btop\\s*(\\d+)\\b|前\\s*(\\d+)\\s*(?:个|名|条)?|排名前\\s*(\\d+)\\s*(?:个|名)?)");

    private static final Pattern SQL_LIMIT_VALUE_PATTERN = Pattern.compile("(?is)\\blimit\\s+(\\d+)\\b");

    private static final Pattern SQL_TOP_VALUE_PATTERN = Pattern.compile("(?is)\\bselect\\s+top\\s+(\\d+)\\b");

    private static final Pattern SQL_FETCH_FIRST_VALUE_PATTERN = Pattern
        .compile("(?is)\\bfetch\\s+first\\s+(\\d+)\\s+rows\\s+only\\b");

    private static final Pattern STATUS_COLUMN_PATTERN = Pattern
        .compile("(?is)\\b(status|order_status|payment_status|trade_status|state)\\b");

    private static final Pattern NEGATIVE_STATUS_OPERATOR_PATTERN = Pattern
        .compile("(?is)(<>|!=|not\\s+in\\s*\\(|not\\s+like\\b)");

	private final AgentDatasourceService agentDatasourceService;

	private final DatasourceService datasourceService;

	private final AccessorFactory accessorFactory;

	public SqlVerifyExplainService(AgentDatasourceService agentDatasourceService, DatasourceService datasourceService,
			AccessorFactory accessorFactory) {
		this.agentDatasourceService = agentDatasourceService;
		this.datasourceService = datasourceService;
		this.accessorFactory = accessorFactory;
	}

    public SqlGuardCheckResult explain(SqlGuardCheckRequest request) {
        String query = StringUtils.trimToEmpty(request == null ? null : request.getQuery());
        String sql = StringUtils.trimToEmpty(request == null ? null : request.getSql());
        String humanFeedbackContent = StringUtils.trimToEmpty(request == null ? null : request.getHumanFeedbackContent());
        if (StringUtils.isBlank(query)) {
            throw new IllegalArgumentException("sql_guard.check 需要 query");
        }
        if (StringUtils.isBlank(sql)) {
            throw new IllegalArgumentException("sql_guard.check 需要 sql");
        }

        String effectiveIntentSource = mergeIntentSource(query, humanFeedbackContent);
        QueryIntent intent = analyzeQueryIntent(effectiveIntentSource);
        HumanFeedbackConstraint feedbackConstraint = analyzeHumanFeedbackConstraint(humanFeedbackContent);

        Statement statement;
        try {
            statement = parseSingleSelectStatement(sql);
        }
        catch (IllegalArgumentException ex) {
            return SqlGuardCheckResult.builder()
                .action(ACTION_SQL_VERIFY)
                .query(query)
                .sql(sql)
                .isAligned(false)
                .summary("SQL 无法通过语法解析，无法继续做结构和意图一致性校验。")
                .explainedIntent(buildIntentExplanation(intent))
                .problems(List.of(SqlGuardProblem.builder()
                    .code("SQL_PARSE_ERROR")
                    .title("SQL 语法解析失败")
                    .severity("high")
                    .message("SQL 无法解析，当前结果不能视为已校验通过。")
                    .why("校验器必须先把 SQL 解析成合法的 SELECT / WITH 语法树，才能继续检查聚合、分组、排序和时间窗口。")
                    .expected("输入应为单条可解析的只读 SELECT / WITH 查询。")
                    .actual("当前 SQL 在语法层面未通过解析。")
                    .evidence(ex.getMessage())
                    .repairHint("先修复括号、关键字顺序、逗号、别名或多语句拼接问题，再重新调用 sql_guard.check。")
                    .build()))
                .fixSuggestions(List.of("先修复 SQL 语法错误，再重新调用 sql_guard.check。"))
                .ruleChecks(List.of(SqlGuardRuleCheck.builder()
                    .code("SQL_PARSE")
                    .title("SQL 语法解析")
                    .status("FAILED")
                    .detail("当前 SQL 未通过语法解析，后续结构规则无法继续执行。")
                    .evidence(ex.getMessage())
                    .build()))
                .build();
        }

        SqlShape shape = analyzeSqlShape(statement, sql, request);
        List<SqlGuardProblem> problems = new ArrayList<>();
        Set<String> fixSuggestions = new LinkedHashSet<>();
        List<SqlGuardRuleCheck> ruleChecks = new ArrayList<>();

        evaluateAggregationRule(query, sql, intent, shape, problems, fixSuggestions, ruleChecks);
        evaluateGroupingRule(query, sql, intent, shape, problems, fixSuggestions, ruleChecks);
        evaluateTimeFilterRule(query, sql, intent, shape, problems, fixSuggestions, ruleChecks);
        evaluateTimeBucketRule(sql, intent, shape, problems, fixSuggestions, ruleChecks);
        evaluateTimeOrderRule(query, sql, intent, shape, problems, fixSuggestions, ruleChecks);
        evaluateOrderingRule(query, sql, intent, shape, problems, fixSuggestions, ruleChecks);
        evaluateLimitRule(query, sql, intent, shape, problems, fixSuggestions, ruleChecks);
        evaluateDistinctRule(query, sql, intent, shape, problems, fixSuggestions, ruleChecks);
        evaluateOrderDirectionRule(sql, intent, shape, problems, fixSuggestions, ruleChecks);
        evaluateHumanFeedbackRule(query, sql, humanFeedbackContent, feedbackConstraint, problems, fixSuggestions,
            ruleChecks);

        boolean aligned = problems.stream().noneMatch(problem -> isBlockingSeverity(problem.getSeverity()));
        String summary = aligned ? "SQL 通过了当前规则版意图一致性校验。"
            : "检测到 %d 个可能影响答案正确性的意图一致性问题。".formatted(problems.size());
        if (aligned) {
            fixSuggestions.add("当前规则校验通过；如要进一步提高置信度，可继续核对执行结果与最终答案解释。");
        }
        return SqlGuardCheckResult.builder()
            .action(ACTION_SQL_VERIFY)
            .query(query)
            .sql(sql)
            .isAligned(aligned)
            .summary(summary)
            .explainedIntent(buildIntentExplanation(intent))
            .problems(problems)
            .fixSuggestions(List.copyOf(fixSuggestions))
            .usedTables(shape.usedTables())
            .usedMetrics(shape.usedMetrics())
            .ruleChecks(ruleChecks)
            .build();
    }

	public SqlGuardCheckResult inspectProfile(String agentId, SqlGuardCheckRequest request) {
		String tableName = StringUtils.trimToEmpty(request == null ? null : request.getTableName());
		if (StringUtils.isBlank(tableName)) {
			throw new IllegalArgumentException("sql_guard.check with action=DATA_PROFILE requires tableName");
		}
		ProfileContext context = resolveProfileContext(agentId);
		String actualTableName = resolveVisibleTableName(context, tableName);
		List<ColumnInfoBO> availableColumns = loadTableColumns(context, actualTableName);
		List<ColumnInfoBO> visibleColumns = applyVisibleColumnRestrictions(context, actualTableName, availableColumns);
		if (visibleColumns.isEmpty()) {
			throw new IllegalArgumentException(
					"Table '%s' has no visible columns for current agent".formatted(actualTableName));
		}
		List<ColumnInfoBO> columnsToInspect = resolveColumnsToInspect(request, actualTableName, visibleColumns);
		int sampleLimit = normalizeProfileLimit(request == null ? null : request.getLimit());
		long totalRows = querySingleLong(context, "SELECT COUNT(*) AS total_rows FROM " + quoteTable(context, actualTableName),
				"total_rows");
		List<Map<String, Object>> columnProfiles = columnsToInspect.stream()
			.map(column -> buildColumnProfile(context, actualTableName, column, totalRows, sampleLimit))
			.toList();
		String summary = "Profiled %d columns from '%s' using visible columns only."
			.formatted(columnProfiles.size(), actualTableName);
		return SqlGuardCheckResult.builder()
			.action(ACTION_DATA_PROFILE)
			.query(request == null ? null : request.getQuery())
			.tableName(actualTableName)
			.summary(summary)
			.totalRows(totalRows)
			.inspectedColumnCount(columnProfiles.size())
			.usedTables(List.of(actualTableName))
			.columnProfiles(columnProfiles)
			.fixSuggestions(List.of(
					"Use categorical fields with concentrated topValues as filters or GROUP BY candidates.",
					"Use numeric/date fields with min/max ranges as metric, trend, or time-window candidates."))
			.build();
	}

	private ProfileContext resolveProfileContext(String agentId) {
		if (!StringUtils.isNumeric(agentId)) {
			throw new IllegalArgumentException("sql_guard.check DATA_PROFILE only supports numeric agentId");
		}
		Long numericAgentId = Long.valueOf(agentId);
		AgentDatasource agentDatasource = agentDatasourceService.getCurrentAgentDatasource(numericAgentId);
		Datasource datasource = agentDatasource.getDatasource() != null ? agentDatasource.getDatasource()
				: datasourceService.getDatasourceById(agentDatasource.getDatasourceId());
		if (datasource == null) {
			throw new IllegalStateException("Active datasource not found for agent " + agentId);
		}
		DbConfigBO dbConfig = datasourceService.getDbConfig(datasource);
		Accessor accessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		List<String> explicitSelectedTables = Optional.ofNullable(agentDatasource.getSelectTables()).orElse(List.of());
		List<String> visibleTables;
		try {
			visibleTables = explicitSelectedTables.isEmpty() ? datasourceService.getDatasourceTables(datasource.getId())
					: explicitSelectedTables;
		}
		catch (Exception ex) {
			throw new IllegalStateException(
					"Failed to load visible tables for datasource %s: %s".formatted(datasource.getId(), ex.getMessage()),
					ex);
		}
		Map<String, List<String>> visibleTablesByName = indexTables(visibleTables, false);
		Map<String, List<String>> visibleTablesByLeafName = indexTables(visibleTables, true);
		Map<String, List<String>> visibleColumnsByTable = buildVisibleColumnsByTable(agentDatasource, visibleTablesByName,
				visibleTablesByLeafName);
		Map<String, Set<String>> visibleColumnNameSetByTable = new LinkedHashMap<>();
		visibleColumnsByTable.forEach((key, value) -> visibleColumnNameSetByTable.put(key,
				value.stream()
					.map(this::normalizeColumnName)
					.collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll)));
		return new ProfileContext(agentDatasource, datasource, dbConfig, accessor, List.copyOf(visibleTables),
				Map.copyOf(visibleTablesByName), Map.copyOf(visibleTablesByLeafName), Map.copyOf(visibleColumnsByTable),
				Map.copyOf(visibleColumnNameSetByTable), Set.copyOf(visibleColumnsByTable.keySet()));
	}

	private List<ColumnInfoBO> loadTableColumns(ProfileContext context, String tableName) {
		try {
			return Optional.ofNullable(context.accessor()
				.showColumns(context.dbConfig(),
						DbQueryParameter.from(context.dbConfig()).setSchema(context.dbConfig().getSchema()).setTable(tableName)))
				.orElse(List.of());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to load columns for table '%s': %s".formatted(tableName, ex.getMessage()),
					ex);
		}
	}

	private List<ColumnInfoBO> applyVisibleColumnRestrictions(ProfileContext context, String tableName,
			List<ColumnInfoBO> columns) {
		return Optional.ofNullable(columns)
			.orElse(List.of())
			.stream()
			.filter(column -> isColumnVisible(context, tableName, column.getName()))
			.toList();
	}

	private List<ColumnInfoBO> resolveColumnsToInspect(SqlGuardCheckRequest request, String tableName,
			List<ColumnInfoBO> visibleColumns) {
		Map<String, ColumnInfoBO> columnsByName = new LinkedHashMap<>();
		for (ColumnInfoBO column : visibleColumns) {
			columnsByName.put(normalizeColumnName(column.getName()), column);
		}
		List<String> requestedColumns = Optional.ofNullable(request == null ? null : request.getColumnNames())
			.orElse(List.of())
			.stream()
			.filter(StringUtils::isNotBlank)
			.map(String::trim)
			.toList();
		if (requestedColumns.isEmpty()) {
			return visibleColumns.stream().limit(DEFAULT_PROFILE_COLUMN_COUNT).toList();
		}
		List<ColumnInfoBO> resolvedColumns = new ArrayList<>();
		for (String requestedColumn : requestedColumns) {
			ColumnInfoBO column = columnsByName.get(normalizeColumnName(requestedColumn));
			if (column == null) {
				throw new IllegalArgumentException(
						"Column '%s' is not visible in table '%s' for current agent".formatted(requestedColumn, tableName));
			}
			resolvedColumns.add(column);
		}
		return resolvedColumns;
	}

	private Map<String, Object> buildColumnProfile(ProfileContext context, String tableName, ColumnInfoBO column,
			long totalRows, int sampleLimit) {
		String quotedTable = quoteTable(context, tableName);
		String quotedColumn = SqlUtil.quoteIdentifier(context.dbConfig().getDialectType(), column.getName());
		long nullCount = querySingleLong(context,
				"SELECT COUNT(*) AS null_rows FROM %s WHERE %s IS NULL".formatted(quotedTable, quotedColumn), "null_rows");
		Double nullRatio = totalRows <= 0 ? 0D : roundRatio((double) nullCount / (double) totalRows);
		Long distinctCount = null;
		if (supportsDistinctCount(column)) {
			distinctCount = querySingleLong(context, "SELECT COUNT(DISTINCT %s) AS distinct_count FROM %s"
				.formatted(quotedColumn, quotedTable), "distinct_count");
		}
		List<Map<String, Object>> topValues = supportsGroupedTopValues(column)
				? queryTopValues(context, quotedTable, quotedColumn, sampleLimit) : List.of();
		List<String> sampleValues = querySampleValues(context, quotedTable, quotedColumn, sampleLimit, supportsDistinctCount(column));
		String minValue = supportsMinMax(column)
				? querySingleValue(context,
						"SELECT MIN(%s) AS min_value FROM %s".formatted(quotedColumn, quotedTable), "min_value")
				: null;
		String maxValue = supportsMinMax(column)
				? querySingleValue(context,
						"SELECT MAX(%s) AS max_value FROM %s".formatted(quotedColumn, quotedTable), "max_value")
				: null;
		Map<String, Object> profile = new LinkedHashMap<>();
		profile.put("columnName", column.getName());
		profile.put("dataType", column.getType());
		profile.put("notNull", column.isNotnull());
		profile.put("nullCount", nullCount);
		profile.put("nullRatio", nullRatio);
		profile.put("distinctCount", distinctCount);
		profile.put("sampleValues", sampleValues);
		profile.put("topValues", topValues);
		profile.put("min", minValue);
		profile.put("max", maxValue);
		profile.put("profileHints", buildProfileHints(column, nullRatio, distinctCount, totalRows, topValues));
		return profile;
	}

	private List<Map<String, Object>> queryTopValues(ProfileContext context, String quotedTable, String quotedColumn,
			int sampleLimit) {
		String sql = applyLimit("""
				SELECT %s AS profile_value, COUNT(*) AS profile_count
				FROM %s
				WHERE %s IS NOT NULL
				GROUP BY %s
				ORDER BY profile_count DESC
				""".formatted(quotedColumn, quotedTable, quotedColumn, quotedColumn), context.dbConfig().getDialectType(),
				sampleLimit);
		ResultSetBO resultSet = executeSql(context, sql);
		List<Map<String, Object>> values = new ArrayList<>();
		for (Map<String, String> row : Optional.ofNullable(resultSet.getData()).orElse(List.of())) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("value", row.get("profile_value"));
			entry.put("count", parseLong(row.get("profile_count")));
			values.add(entry);
		}
		return values;
	}

	private List<String> querySampleValues(ProfileContext context, String quotedTable, String quotedColumn, int sampleLimit,
			boolean distinctPreferred) {
		String selectClause = distinctPreferred ? "SELECT DISTINCT %s AS sample_value".formatted(quotedColumn)
				: "SELECT %s AS sample_value".formatted(quotedColumn);
		String sql = applyLimit("""
				%s
				FROM %s
				WHERE %s IS NOT NULL
				ORDER BY %s
				""".formatted(selectClause, quotedTable, quotedColumn, quotedColumn), context.dbConfig().getDialectType(),
				sampleLimit);
		ResultSetBO resultSet = executeSql(context, sql);
		return Optional.ofNullable(resultSet.getData())
			.orElse(List.of())
			.stream()
			.map(row -> row.get("sample_value"))
			.filter(StringUtils::isNotBlank)
			.toList();
	}

	private List<String> buildProfileHints(ColumnInfoBO column, Double nullRatio, Long distinctCount, long totalRows,
			List<Map<String, Object>> topValues) {
		List<String> hints = new ArrayList<>();
		if (Boolean.TRUE.equals(isLikelyCategorical(column, distinctCount, totalRows, topValues))) {
			hints.add("Likely categorical field; suitable for filter or GROUP BY.");
		}
		if (supportsMinMax(column)) {
			hints.add("Likely ordered field; suitable for range filter, metric, or trend axis.");
		}
		if (nullRatio != null && nullRatio >= 0.5D) {
			hints.add("High null ratio; be careful when using it as a hard filter.");
		}
		if (hints.isEmpty()) {
			hints.add("Inspect samples and top values before deciding whether to use it in SQL.");
		}
		return hints;
	}

	private Boolean isLikelyCategorical(ColumnInfoBO column, Long distinctCount, long totalRows,
			List<Map<String, Object>> topValues) {
		if (!supportsGroupedTopValues(column)) {
			return false;
		}
		if (distinctCount != null && distinctCount > 0 && distinctCount <= 20) {
			return true;
		}
		if (totalRows > 0 && distinctCount != null && distinctCount <= Math.max(10, totalRows / 10)) {
			return true;
		}
		return !topValues.isEmpty() && topValues.size() <= 10;
	}

	private long querySingleLong(ProfileContext context, String sql, String columnName) {
		return parseLong(querySingleValue(context, sql, columnName));
	}

	private String querySingleValue(ProfileContext context, String sql, String columnName) {
		ResultSetBO resultSet = executeSql(context, sql);
		List<Map<String, String>> rows = Optional.ofNullable(resultSet.getData()).orElse(List.of());
		if (rows.isEmpty()) {
			return null;
		}
		Map<String, String> row = rows.get(0);
		if (row.containsKey(columnName)) {
			return row.get(columnName);
		}
		return row.values().stream().findFirst().orElse(null);
	}

	private ResultSetBO executeSql(ProfileContext context, String sql) {
		try {
			ResultSetBO resultSet = context.accessor().executeSqlAndReturnObject(context.dbConfig(),
					DbQueryParameter.from(context.dbConfig()).setSchema(context.dbConfig().getSchema()).setSql(sql));
			if (resultSet == null) {
				return ResultSetBO.builder().column(List.of()).data(List.of()).build();
			}
			if (StringUtils.isNotBlank(resultSet.getErrorMsg())) {
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
		catch (Exception ex) {
			throw new IllegalStateException("Failed to execute profile SQL: " + ex.getMessage(), ex);
		}
	}

	private int normalizeProfileLimit(Integer requestedLimit) {
		if (requestedLimit == null || requestedLimit <= 0) {
			return DEFAULT_PROFILE_LIMIT;
		}
		return Math.min(requestedLimit, MAX_PROFILE_LIMIT);
	}

	private boolean supportsDistinctCount(ColumnInfoBO column) {
		String normalizedType = normalizeType(column);
		return !containsAny(normalizedType, "blob", "clob", "text", "ntext", "image", "json", "xml", "bytea");
	}

	private boolean supportsGroupedTopValues(ColumnInfoBO column) {
		String normalizedType = normalizeType(column);
		return !containsAny(normalizedType, "blob", "clob", "ntext", "image", "bytea");
	}

	private boolean supportsMinMax(ColumnInfoBO column) {
		String normalizedType = normalizeType(column);
		return containsAny(normalizedType, "int", "number", "numeric", "decimal", "double", "float", "real", "date",
				"time", "year", "timestamp");
	}

	private String normalizeType(ColumnInfoBO column) {
		return StringUtils.defaultString(column == null ? null : column.getType()).toLowerCase(Locale.ROOT);
	}

	private String applyLimit(String sql, String dialectType, int limit) {
		String trimmed = StringUtils.trimToEmpty(sql);
		if (trimmed.isEmpty()) {
			return trimmed;
		}
		String normalizedDialect = StringUtils.defaultString(dialectType).toLowerCase(Locale.ROOT);
		if (normalizedDialect.contains("sqlserver") || normalizedDialect.contains("sql_server")) {
			if (trimmed.matches("(?is)^select\\s+distinct\\b.*")) {
				return trimmed.replaceFirst("(?is)^select\\s+distinct\\b", "SELECT DISTINCT TOP %d".formatted(limit));
			}
			return trimmed.replaceFirst("(?is)^select\\b", "SELECT TOP %d".formatted(limit));
		}
		if (normalizedDialect.contains("oracle")) {
			return trimmed + " FETCH FIRST " + limit + " ROWS ONLY";
		}
		return trimmed + " LIMIT " + limit;
	}

	private String quoteTable(ProfileContext context, String tableName) {
		return SqlUtil.quoteIdentifier(context.dbConfig().getDialectType(), tableName);
	}

	private double roundRatio(double value) {
		return Math.round(value * 10000D) / 10000D;
	}

	private long parseLong(String value) {
		if (StringUtils.isBlank(value)) {
			return 0L;
		}
		try {
			return Long.parseLong(value.trim());
		}
		catch (NumberFormatException ex) {
			try {
				return Math.round(Double.parseDouble(value.trim()));
			}
			catch (NumberFormatException ignored) {
				return 0L;
			}
		}
	}

	private String resolveVisibleTableName(ProfileContext context, String tableName) {
		return findVisibleTableName(context.visibleTablesByName(), context.visibleTablesByLeafName(), tableName, false)
			.orElseThrow(() -> new IllegalArgumentException(
					"Table '%s' is not visible for current agent. Visible tables: %s".formatted(tableName,
							String.join(", ", context.visibleTables()))));
	}

	private Optional<String> findVisibleTableName(Map<String, List<String>> visibleTablesByName,
			Map<String, List<String>> visibleTablesByLeafName, String tableName, boolean allowQualifiedFallback) {
		String normalizedTableName = normalizeIdentifier(tableName);
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
				.distinct()
				.toList();
			if (!sanitizedColumns.isEmpty()) {
				visibleColumnsByTable.put(normalizeTableName(resolvedTableName.get()), sanitizedColumns);
			}
		});
		return visibleColumnsByTable;
	}

	private Map<String, List<String>> indexTables(List<String> tableNames, boolean leafOnly) {
		Map<String, List<String>> index = new LinkedHashMap<>();
		for (String tableName : Optional.ofNullable(tableNames).orElse(List.of())) {
			if (StringUtils.isBlank(tableName)) {
				continue;
			}
			String key = leafOnly ? normalizeTableLeafName(tableName) : normalizeTableName(tableName);
			index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(tableName);
		}
		return index;
	}

	private boolean isColumnVisible(ProfileContext context, String tableName, String columnName) {
		String normalizedTableName = normalizeTableName(tableName);
		if (!context.columnRestrictedTables().contains(normalizedTableName)) {
			return true;
		}
		Set<String> visibleColumns = context.visibleColumnNameSetByTable().get(normalizedTableName);
		return visibleColumns != null && visibleColumns.contains(normalizeColumnName(columnName));
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
		int lastDot = normalized.lastIndexOf('.');
		return lastDot >= 0 ? normalized.substring(lastDot + 1) : normalized;
	}

	private String normalizeColumnName(String columnName) {
		return normalizeTableLeafName(columnName);
	}

    private void evaluateAggregationRule(String query, String sql, QueryIntent intent, SqlShape shape,
            List<SqlGuardProblem> problems, Set<String> fixSuggestions, List<SqlGuardRuleCheck> ruleChecks) {
        if (!intent.requiresAggregation()) {
            return;
        }
        if (!shape.hasAggregation()) {
            addProblem(problems, fixSuggestions, "MISSING_AGGREGATION", "缺少聚合指标", "high",
                "问题看起来要求聚合指标，但 SQL 更像明细查询。",
                "用户问题带有数量、金额、总数、平均值等聚合口径，但 SQL 没有检测到 count/sum/avg/min/max 等聚合函数。",
                "SELECT 中应包含与题目口径匹配的聚合表达式。",
                "当前 SQL 未检测到聚合函数。",
                "query=" + query + "; sql=" + sql,
                "把 count/sum/avg/min/max 等聚合逻辑补齐到 SELECT 中。");
            recordRuleCheck(ruleChecks, "AGGREGATION_REQUIRED", "聚合指标校验", "FAILED",
                "问题要求聚合指标，但 SQL 未检测到聚合函数。", "query=" + query + "; sql=" + sql);
            return;
        }
        recordRuleCheck(ruleChecks, "AGGREGATION_REQUIRED", "聚合指标校验", "PASSED",
            "问题要求聚合指标，SQL 已检测到聚合函数。", "usedMetrics=" + shape.usedMetrics());
    }

    private void evaluateGroupingRule(String query, String sql, QueryIntent intent, SqlShape shape,
            List<SqlGuardProblem> problems, Set<String> fixSuggestions, List<SqlGuardRuleCheck> ruleChecks) {
        if (!intent.requiresGrouping()) {
            return;
        }
        if (!shape.hasGroupBy()) {
            addProblem(problems, fixSuggestions, "MISSING_GROUP_BY", "缺少 GROUP BY", "high",
                "问题要求按维度拆分，但 SQL 缺少 GROUP BY。",
                "用户问题包含按地区、按用户、各品类、每月等拆分意图；没有 GROUP BY 时，要么结果被压成总计，要么数据库直接报错。",
                "SQL 应按题目中的维度列做 GROUP BY。",
                "当前 SQL 未检测到 GROUP BY。",
                "query=" + query + "; sql=" + sql,
                "把用户要求的维度列加入 GROUP BY，并检查 SELECT 中的非聚合列。");
            recordRuleCheck(ruleChecks, "GROUP_BY_REQUIRED", "分组维度校验", "FAILED",
                "问题要求分维度拆分，但 SQL 未检测到 GROUP BY。", "query=" + query + "; sql=" + sql);
            return;
        }
        recordRuleCheck(ruleChecks, "GROUP_BY_REQUIRED", "分组维度校验", "PASSED",
            "问题要求分维度拆分，SQL 已检测到 GROUP BY。", "sql=" + sql);
    }

    private void evaluateTimeFilterRule(String query, String sql, QueryIntent intent, SqlShape shape,
            List<SqlGuardProblem> problems, Set<String> fixSuggestions, List<SqlGuardRuleCheck> ruleChecks) {
        if (!intent.requiresTimeFilter()) {
            return;
        }
        if (!shape.hasTimePredicate()) {
            addProblem(problems, fixSuggestions, "MISSING_TIME_FILTER", "缺少时间过滤", "high",
                "问题包含明确时间窗口，但 SQL 没有可靠的时间过滤信号。",
                "题目提到了今天、本月、最近30天、某年某月等时间范围，但 SQL 没有看到明确时间过滤，结果很可能回成全量数据。",
                "WHERE 中应包含与题目对应的时间范围约束。",
                "当前 SQL 未检测到可靠的时间过滤表达式。",
                "query=" + query + "; sql=" + sql,
                "在 WHERE 中补齐精确时间范围，不要按默认全量数据查询。");
            recordRuleCheck(ruleChecks, "TIME_FILTER_REQUIRED", "时间窗口校验", "FAILED",
                "问题包含明确时间窗口，但 SQL 未检测到可靠时间过滤。", "query=" + query + "; sql=" + sql);
            return;
        }
        recordRuleCheck(ruleChecks, "TIME_FILTER_REQUIRED", "时间窗口校验", "PASSED",
            "问题包含明确时间窗口，SQL 已检测到时间过滤。", "sql=" + sql);
    }

    private void evaluateTimeBucketRule(String sql, QueryIntent intent, SqlShape shape, List<SqlGuardProblem> problems,
            Set<String> fixSuggestions, List<SqlGuardRuleCheck> ruleChecks) {
        if (!intent.requiresTrend()) {
            return;
        }
        if (!shape.hasTimeBucket()) {
            addProblem(problems, fixSuggestions, "MISSING_TIME_BUCKET", "缺少时间分桶", "high",
                "趋势类问题通常需要时间分桶，但 SQL 没看到明确的时间粒度表达。",
                "趋势分析需要先按天、周、月、年等粒度汇总；没有时间分桶，返回的往往只是总数，不是趋势。",
                "SQL 应包含 DATE/DATE_TRUNC/DATE_FORMAT 等时间分桶表达式，并按同粒度分组。",
                "当前 SQL 未检测到明确的时间分桶表达式。",
                "sql=" + sql,
                "用 DATE/DATE_TRUNC/DATE_FORMAT 等时间分桶表达式，并对同一时间粒度做 GROUP BY。");
            recordRuleCheck(ruleChecks, "TIME_BUCKET_REQUIRED", "趋势时间粒度校验", "FAILED",
                "趋势问题需要时间分桶，但 SQL 未检测到时间粒度表达。", "sql=" + sql);
            return;
        }
        recordRuleCheck(ruleChecks, "TIME_BUCKET_REQUIRED", "趋势时间粒度校验", "PASSED",
            "趋势问题所需的时间分桶表达已检测到。", "sql=" + sql);
    }

    private void evaluateTimeOrderRule(String query, String sql, QueryIntent intent, SqlShape shape,
            List<SqlGuardProblem> problems, Set<String> fixSuggestions, List<SqlGuardRuleCheck> ruleChecks) {
        if (!intent.requiresTrend()) {
            return;
        }
        if (!shape.hasOrderBy()) {
            addProblem(problems, fixSuggestions, "MISSING_TIME_ORDER", "缺少时间排序", "medium",
                "趋势类问题通常需要按时间排序，但 SQL 缺少 ORDER BY。",
                "趋势结果如果不按时间排序，输出顺序可能是乱的，后续回答和可视化都容易误导。",
                "趋势 SQL 应按时间字段或时间分桶字段排序。",
                "当前 SQL 未检测到 ORDER BY。",
                "query=" + query + "; sql=" + sql,
                "按时间字段或时间分桶字段补齐 ORDER BY。");
            recordRuleCheck(ruleChecks, "TIME_ORDER_REQUIRED", "趋势时间排序校验", "FAILED",
                "趋势问题需要时间排序，但 SQL 未检测到 ORDER BY。", "sql=" + sql);
            return;
        }
        recordRuleCheck(ruleChecks, "TIME_ORDER_REQUIRED", "趋势时间排序校验", "PASSED",
            "趋势问题所需的时间排序已检测到。", "sql=" + sql);
    }

    private void evaluateOrderingRule(String query, String sql, QueryIntent intent, SqlShape shape,
            List<SqlGuardProblem> problems, Set<String> fixSuggestions, List<SqlGuardRuleCheck> ruleChecks) {
        if (!intent.requiresOrdering()) {
            return;
        }
        if (!shape.hasOrderBy()) {
            addProblem(problems, fixSuggestions, "MISSING_ORDER_BY", "缺少排序", "medium",
                "问题要求排序或排名，但 SQL 缺少 ORDER BY。",
                "题目要求最高、最低、TopN、排名等比较关系；没有 ORDER BY 时，即使有限制行数，返回的也不一定是目标对象。",
                "SQL 应明确按目标指标排序。",
                "当前 SQL 未检测到 ORDER BY。",
                "query=" + query + "; sql=" + sql,
                "根据问题要求补齐 ORDER BY，并明确升序还是降序。");
            recordRuleCheck(ruleChecks, "ORDER_REQUIRED", "排序要求校验", "FAILED",
                "问题包含排序或排名诉求，但 SQL 未检测到 ORDER BY。", "query=" + query + "; sql=" + sql);
            return;
        }
        recordRuleCheck(ruleChecks, "ORDER_REQUIRED", "排序要求校验", "PASSED",
            "问题包含排序或排名诉求，SQL 已检测到 ORDER BY。", "sql=" + sql);
    }

    private void evaluateLimitRule(String query, String sql, QueryIntent intent, SqlShape shape,
            List<SqlGuardProblem> problems, Set<String> fixSuggestions, List<SqlGuardRuleCheck> ruleChecks) {
        if (!intent.requiresLimit()) {
            return;
        }
        if (!shape.hasLimit()) {
            addProblem(problems, fixSuggestions, "MISSING_LIMIT", "缺少返回行数限制", "medium",
                "问题要求 TopN / 前N / 单个最值对象，但 SQL 没有限制返回行数。",
                "题目明确只需要前几名或唯一最值对象；如果不限制行数，结果会混入多余记录。",
                "SQL 应通过 LIMIT / TOP / FETCH FIRST 控制返回行数。",
                "当前 SQL 未检测到返回行数限制。",
                "query=" + query + "; sql=" + sql,
                "补齐 LIMIT / TOP / FETCH FIRST，避免把全量结果当成 TopN。");
            recordRuleCheck(ruleChecks, "LIMIT_REQUIRED", "TopN 行数限制校验", "FAILED",
                "问题要求限制返回行数，但 SQL 未检测到 LIMIT/TOP/FETCH FIRST。", "query=" + query + "; sql=" + sql);
            return;
        }
        if (shape.limitValueKnown() && intent.expectedLimit() != null && !intent.expectedLimit().equals(shape.limitValue())) {
            String mismatchCode = shape.limitValue() > intent.expectedLimit() ? "LIMIT_TOO_LARGE" : "LIMIT_TOO_SMALL";
            addProblem(problems, fixSuggestions, mismatchCode, "TopN 数量不匹配", "medium",
                "问题要求的返回条数与 SQL 实际限制条数不一致。",
                "题目明确给了 TopN 数量或单个最值对象，限制条数不一致会直接改变结果口径。",
                "返回条数应与题目要求一致。",
                "题目期望 " + intent.expectedLimit() + " 条，但 SQL 当前限制为 " + shape.limitValue() + " 条。",
                "query=" + query + "; sql=" + sql,
                "把 LIMIT/TOP/FETCH FIRST 改成与题目一致的条数。");
            recordRuleCheck(ruleChecks, "LIMIT_MATCH", "TopN 数量匹配校验", "FAILED",
                "题目期望 " + intent.expectedLimit() + " 条，但 SQL 实际限制为 " + shape.limitValue() + " 条。",
                "query=" + query + "; sql=" + sql);
            return;
        }
        recordRuleCheck(ruleChecks, "LIMIT_MATCH", "TopN 数量匹配校验", "PASSED",
            intent.expectedLimit() == null ? "题目要求限制返回行数，SQL 已检测到限制。"
                : "题目期望 " + intent.expectedLimit() + " 条，SQL 返回条数限制一致。",
            "sql=" + sql);
    }

    private void evaluateDistinctRule(String query, String sql, QueryIntent intent, SqlShape shape,
            List<SqlGuardProblem> problems, Set<String> fixSuggestions, List<SqlGuardRuleCheck> ruleChecks) {
        if (!intent.requiresDistinct()) {
            return;
        }
        if (!shape.hasDistinct()) {
            addProblem(problems, fixSuggestions, "MISSING_DISTINCT", "缺少 DISTINCT 去重", "high",
                "问题要求去重口径，但 SQL 没看到 DISTINCT。",
                "题目要求独立用户、去重人数、唯一值等口径；不去重会重复计算。",
                "SQL 应使用 SELECT DISTINCT 或 COUNT(DISTINCT ...)。",
                "当前 SQL 未检测到 DISTINCT。",
                "query=" + query + "; sql=" + sql,
                "把口径改成 SELECT DISTINCT 或 COUNT(DISTINCT ...)。");
            recordRuleCheck(ruleChecks, "DISTINCT_REQUIRED", "去重口径校验", "FAILED",
                "问题要求去重口径，但 SQL 未检测到 DISTINCT。", "query=" + query + "; sql=" + sql);
            return;
        }
        recordRuleCheck(ruleChecks, "DISTINCT_REQUIRED", "去重口径校验", "PASSED",
            "问题要求去重口径，SQL 已检测到 DISTINCT。", "sql=" + sql);
    }

    private void evaluateOrderDirectionRule(String sql, QueryIntent intent, SqlShape shape,
            List<SqlGuardProblem> problems, Set<String> fixSuggestions, List<SqlGuardRuleCheck> ruleChecks) {
        if (!shape.hasOrderBy()) {
            return;
        }
        if (intent.prefersDescending() && shape.orderDirectionKnown() && !shape.orderDescending()) {
            addProblem(problems, fixSuggestions, "ORDER_DIRECTION_MISMATCH", "排序方向不匹配", "high",
                "问题要求最高 / Top / 最多，但 SQL 排序方向不像降序。",
                "题目要的是最大值或靠前排名，若排序方向写成 ASC，返回的会是最小值或反向结果。",
                "这类问题通常应按目标指标 DESC 排序。",
                "当前 SQL 的排序方向与题目诉求不一致。",
                "sql=" + sql,
                "把排序方向改成 DESC，并确认排序指标是否正确。");
            recordRuleCheck(ruleChecks, "ORDER_DIRECTION", "排序方向校验", "FAILED",
                "题目要求高到低/最多/Top，但 SQL 当前排序方向不是 DESC。", "sql=" + sql);
            return;
        }
        if (intent.prefersAscending() && shape.orderDirectionKnown() && shape.orderDescending()) {
            addProblem(problems, fixSuggestions, "ORDER_DIRECTION_MISMATCH", "排序方向不匹配", "high",
                "问题要求最低 / 最少 / 最小，但 SQL 排序方向不像升序。",
                "题目要的是最小值或最低排名，若排序方向写成 DESC，返回的会是最大值或反向结果。",
                "这类问题通常应按目标指标 ASC 排序。",
                "当前 SQL 的排序方向与题目诉求不一致。",
                "sql=" + sql,
                "把排序方向改成 ASC，并确认排序指标是否正确。");
            recordRuleCheck(ruleChecks, "ORDER_DIRECTION", "排序方向校验", "FAILED",
                "题目要求低到高/最少/最小，但 SQL 当前排序方向不是 ASC。", "sql=" + sql);
            return;
        }
        if ((intent.prefersDescending() || intent.prefersAscending()) && !shape.orderDirectionKnown()) {
            addProblem(problems, fixSuggestions, "ORDER_DIRECTION_AMBIGUOUS", "排序方向不明确", "medium",
                "问题对排序方向有明确诉求，但 SQL 的 ORDER BY 没有写明 ASC / DESC。",
                "有些数据库默认升序，但不应该依赖默认行为承载业务口径，否则最值问题很容易答反。",
                "ORDER BY 应显式写明 ASC 或 DESC。",
                "当前 SQL 虽然有 ORDER BY，但没有检测到明确排序方向。",
                "sql=" + sql,
                "显式补上 ASC 或 DESC，不要依赖数据库默认排序方向。");
            recordRuleCheck(ruleChecks, "ORDER_DIRECTION_EXPLICIT", "显式排序方向校验", "FAILED",
                "题目存在明确最值方向，但 SQL 的 ORDER BY 未显式写出 ASC/DESC。", "sql=" + sql);
            return;
        }
        if (intent.prefersDescending() || intent.prefersAscending()) {
            recordRuleCheck(ruleChecks, "ORDER_DIRECTION_EXPLICIT", "显式排序方向校验", "PASSED",
                "ORDER BY 已显式声明排序方向。", "sql=" + sql);
        }
    }

    private void evaluateHumanFeedbackRule(String query, String sql, String humanFeedbackContent,
            HumanFeedbackConstraint feedbackConstraint, List<SqlGuardProblem> problems, Set<String> fixSuggestions,
            List<SqlGuardRuleCheck> ruleChecks) {
        if (!feedbackConstraint.hasConstraints()) {
            return;
        }
        String normalizedSql = StringUtils.trimToEmpty(sql).toLowerCase(Locale.ROOT);
        List<String> feedbackProblems = new ArrayList<>();

        if (!feedbackConstraint.requiredStatusTokens().isEmpty()) {
            boolean matchedRequiredStatus = feedbackConstraint.requiredStatusTokens()
                .stream()
                .anyMatch(token -> sqlContainsStatusToken(normalizedSql, token));
            if (!matchedRequiredStatus) {
                feedbackProblems.add("未看到与人工反馈一致的状态过滤条件");
                addProblem(problems, fixSuggestions, "MISSING_CONFIRMED_STATUS_FILTER", "缺少人工反馈确认的状态过滤", "high",
                    "用户已经通过人工反馈明确了状态口径，但 SQL 里没有体现该约束。",
                    "人工反馈属于已确认条件；如果 SQL 没落实这些条件，最终结果会与用户确认的口径不一致。",
                    "SQL 应显式体现用户确认过的状态范围或订单口径。",
                    "当前 SQL 未检测到与人工反馈一致的状态条件。",
                    "query=" + query + "; feedback=" + humanFeedbackContent + "; sql=" + sql,
                    "把人工反馈里确认过的状态过滤条件补进 WHERE，例如只统计 completed / paid 等已确认状态。");
            }
        }

        if (!feedbackConstraint.excludedStatusTokens().isEmpty()) {
            boolean mentionsExcludedStatus = feedbackConstraint.excludedStatusTokens()
                .stream()
                .anyMatch(token -> sqlContainsStatusToken(normalizedSql, token));
            boolean hasStatusPredicate = STATUS_COLUMN_PATTERN.matcher(normalizedSql).find()
                || feedbackConstraint.requiredStatusTokens().stream().anyMatch(token -> sqlContainsStatusToken(normalizedSql, token));
            boolean hasNegativeStatusPredicate = NEGATIVE_STATUS_OPERATOR_PATTERN.matcher(normalizedSql).find();
            if (!hasStatusPredicate && !mentionsExcludedStatus) {
                feedbackProblems.add("未看到用于落实人工反馈排除条件的状态过滤");
                addProblem(problems, fixSuggestions, "MISSING_CONFIRMED_STATUS_EXCLUSION", "缺少人工反馈确认的排除条件", "high",
                    "用户已经通过人工反馈确认要排除某些状态，但 SQL 里没有看到对应的过滤条件。",
                    "像“不含退款”“排除取消单”这类反馈会直接改变统计口径；如果 SQL 不落实，结果会偏大或口径错误。",
                    "SQL 应显式体现这些排除条件，或通过更窄的已确认状态集合覆盖它们。",
                    "当前 SQL 未检测到相关状态过滤。",
                    "query=" + query + "; feedback=" + humanFeedbackContent + "; sql=" + sql,
                    "把人工反馈里确认的排除条件补进 WHERE，例如排除 refund / cancelled 等状态。");
            }
            else if (mentionsExcludedStatus && !hasNegativeStatusPredicate
                    && feedbackConstraint.requiredStatusTokens().isEmpty()) {
                feedbackProblems.add("SQL 提到了应排除的状态，但没有看到明确排除写法");
                addProblem(problems, fixSuggestions, "CONFIRMED_STATUS_EXCLUSION_MISMATCH", "人工反馈排除条件未落实", "high",
                    "人工反馈要求排除某些状态，但 SQL 里虽然出现了这些状态词，却没有看到明确的排除写法。",
                    "如果只是把 refund / cancelled 放进正向条件里，结果会和用户确认的口径相反。",
                    "这些状态应通过 <> / != / NOT IN 等方式排除，或通过更窄的正向状态集间接排除。",
                    "当前 SQL 提到了应排除的状态，但没有检测到明确排除条件。",
                    "query=" + query + "; feedback=" + humanFeedbackContent + "; sql=" + sql,
                    "把这些状态改成显式排除条件，或改成更精确的正向状态集合。");
            }
        }

        if (feedbackProblems.isEmpty()) {
            recordRuleCheck(ruleChecks, "CONFIRMED_FEEDBACK_CONSTRAINTS", "人工反馈一致性校验", "PASSED",
                "SQL 已体现当前人工反馈中的显式状态口径约束。", "feedback=" + humanFeedbackContent);
            return;
        }
        recordRuleCheck(ruleChecks, "CONFIRMED_FEEDBACK_CONSTRAINTS", "人工反馈一致性校验", "FAILED",
            String.join("；", feedbackProblems), "feedback=" + humanFeedbackContent + "; sql=" + sql);
    }

    private void addProblem(List<SqlGuardProblem> problems, Set<String> fixSuggestions, String code, String title,
            String severity, String message, String why, String expected, String actual, String evidence,
            String fixSuggestion) {
        problems.add(SqlGuardProblem.builder()
            .code(code)
            .title(title)
            .severity(severity)
            .message(message)
            .why(why)
            .expected(expected)
            .actual(actual)
            .evidence(evidence)
            .repairHint(fixSuggestion)
            .build());
        fixSuggestions.add(fixSuggestion);
    }

    private void recordRuleCheck(List<SqlGuardRuleCheck> ruleChecks, String code, String title, String status,
            String detail, String evidence) {
        ruleChecks.add(SqlGuardRuleCheck.builder()
            .code(code)
            .title(title)
            .status(status)
            .detail(detail)
            .evidence(evidence)
            .build());
    }

    private boolean isBlockingSeverity(String severity) {
        return "high".equalsIgnoreCase(severity) || "medium".equalsIgnoreCase(severity);
    }

    private String mergeIntentSource(String query, String humanFeedbackContent) {
        if (StringUtils.isBlank(humanFeedbackContent)) {
            return query;
        }
        return query + "\n" + humanFeedbackContent;
    }

    private HumanFeedbackConstraint analyzeHumanFeedbackConstraint(String humanFeedbackContent) {
        if (StringUtils.isBlank(humanFeedbackContent)) {
            return HumanFeedbackConstraint.empty();
        }
        String feedback = humanFeedbackContent.trim();
        String normalizedFeedback = feedback.toLowerCase(Locale.ROOT);
        Set<String> requiredStatusTokens = new LinkedHashSet<>();
        Set<String> excludedStatusTokens = new LinkedHashSet<>();

        collectRequiredStatusTokens(feedback, normalizedFeedback, requiredStatusTokens);
        collectExcludedStatusTokens(feedback, normalizedFeedback, excludedStatusTokens);
        return new HumanFeedbackConstraint(feedback, Set.copyOf(requiredStatusTokens), Set.copyOf(excludedStatusTokens));
    }

    private void collectRequiredStatusTokens(String feedback, String normalizedFeedback, Set<String> target) {
        if (containsAny(feedback, "已完成", "完成订单") || containsAny(normalizedFeedback, "completed", "complete")) {
            target.add("completed");
        }
        if (containsAny(feedback, "已支付", "支付成功") || containsAny(normalizedFeedback, "paid", "payment_success")) {
            target.add("paid");
        }
        if (containsAny(feedback, "待支付", "未支付", "待处理") || containsAny(normalizedFeedback, "pending", "unpaid")) {
            target.add("pending");
        }
        if (containsAny(feedback, "已取消", "取消单") || containsAny(normalizedFeedback, "cancelled", "canceled")) {
            target.add("cancelled");
        }
        if (containsAny(feedback, "退款", "已退款") || containsAny(normalizedFeedback, "refund", "refunded")) {
            if (!containsAny(feedback, "不含退款", "不包含退款", "排除退款", "剔除退款")
                    && !containsAny(normalizedFeedback, "exclude refund", "without refund", "not refunded")) {
                target.add("refund");
            }
        }
    }

    private void collectExcludedStatusTokens(String feedback, String normalizedFeedback, Set<String> target) {
        if (containsAny(feedback, "不含退款", "不包含退款", "排除退款", "剔除退款", "不看退款")
                || containsAny(normalizedFeedback, "exclude refund", "without refund", "not refunded")) {
            target.add("refund");
        }
        if (containsAny(feedback, "不含取消", "不包含取消", "排除取消", "剔除取消", "不看取消")
                || containsAny(normalizedFeedback, "exclude cancel", "without cancel", "not cancelled", "not canceled")) {
            target.add("cancelled");
        }
    }

    private boolean sqlContainsStatusToken(String normalizedSql, String token) {
        return switch (token) {
            case "completed" -> containsAny(normalizedSql, "'completed'", "\"completed\"", " completed ", "'complete'",
                    "已完成", "'success'", "\"success\"");
            case "paid" -> containsAny(normalizedSql, "'paid'", "\"paid\"", " paid ", "已支付", "payment_success");
            case "pending" -> containsAny(normalizedSql, "'pending'", "\"pending\"", " pending ", "待支付", "未支付",
                    "'unpaid'");
            case "cancelled" -> containsAny(normalizedSql, "'cancelled'", "\"cancelled\"", "'canceled'",
                    "\"canceled\"", " cancelled ", " canceled ", "已取消", "取消");
            case "refund" -> containsAny(normalizedSql, "'refund'", "\"refund\"", "'refunded'", "\"refunded\"",
                    " refund ", " refunded ", "退款");
            default -> containsAny(normalizedSql, token);
        };
    }

    private QueryIntent analyzeQueryIntent(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        boolean requiresTrend = containsAny(query, "趋势", "走势图", "按天", "按周", "按月", "按年", "daily", "weekly", "monthly",
            "trend", "over time", "环比", "同比");
        boolean requiresGrouping = requiresTrend
            || containsAny(query, "按", "按照", "每个", "各", "分组", "group by", "维度", "分类", "分城市", "分地区", "分品类", "分渠道");
        boolean requiresTimeFilter = containsAny(query, "今天", "昨日", "昨天", "本周", "上周", "本月", "上月", "本季度", "上季度", "今年",
            "去年", "近", "最近", "最近的", "latest", "recent", "last ", "past ", "today", "yesterday", "this month",
            "this year", "202", "2024", "2025", "2026", "Q1", "Q2", "Q3", "Q4");
        boolean requiresOrdering = requiresTrend
            || containsAny(normalized, "top ", "rank", "ranking", "highest", "lowest", "best", "worst", "most", "least")
            || containsAny(query, "排名", "排行", "最高", "最低", "最多", "最少", "前", "后");
        boolean prefersDescending = containsAny(normalized, "top ", "highest", "best", "most", "largest")
            || containsAny(query, "最高", "最多", "最大", "前");
        boolean prefersAscending = containsAny(normalized, "lowest", "least", "smallest", "worst")
            || containsAny(query, "最低", "最少", "最小");
        boolean explicitDescendingDirection = containsAny(normalized, " desc", "descending")
            || containsAny(query, "降序", "从高到低");
        boolean explicitAscendingDirection = containsAny(normalized, " asc", "ascending")
            || containsAny(query, "升序", "从低到高");
        requiresOrdering = requiresOrdering || explicitDescendingDirection || explicitAscendingDirection;
        prefersDescending = prefersDescending || explicitDescendingDirection;
        prefersAscending = prefersAscending || explicitAscendingDirection;
        Integer expectedLimit = extractExpectedLimit(query, prefersDescending, prefersAscending);
        boolean requiresLimit = expectedLimit != null;
        boolean requiresDistinct = containsAny(normalized, "distinct", "deduplicate", "unique", "uv")
            || containsAny(query, "去重", "独立用户", "唯一");
        boolean requiresAggregation = requiresTrend || requiresDistinct
            || containsAny(normalized, "count", "sum", "avg", "average", "total", "amount", "sales", "revenue")
            || containsAny(query, "数量", "总数", "总额", "金额", "销量", "销售额", "订单数", "人数", "平均", "占比", "比例", "贡献", "多少");
        return new QueryIntent(requiresAggregation, requiresGrouping, requiresTimeFilter, requiresOrdering, requiresLimit,
            requiresDistinct, requiresTrend, prefersDescending, prefersAscending, expectedLimit);
    }

    private SqlShape analyzeSqlShape(Statement statement, String sql, SqlGuardCheckRequest request) {
        String normalizedSql = StringUtils.trimToEmpty(sql).toLowerCase(Locale.ROOT);
        Set<String> knownTimeColumns = extractKnownTimeColumns(request);
        List<String> usedTables = extractReferencedTables(statement);
        List<String> usedMetrics = extractUsedMetrics(sql);
        boolean hasAggregation = AGGREGATE_PATTERN.matcher(sql).find();
        boolean hasGroupBy = GROUP_BY_PATTERN.matcher(normalizedSql).find();
        boolean hasOrderBy = ORDER_BY_PATTERN.matcher(normalizedSql).find();
        boolean hasLimit = LIMIT_PATTERN.matcher(normalizedSql).find() || TOP_PATTERN.matcher(normalizedSql).find()
            || FETCH_FIRST_PATTERN.matcher(normalizedSql).find();
        boolean hasDistinct = DISTINCT_PATTERN.matcher(normalizedSql).find();
        boolean hasTimePredicate = detectTimePredicate(normalizedSql, knownTimeColumns);
        boolean hasTimeBucket = detectTimeBucket(normalizedSql, knownTimeColumns);
        boolean orderDescending = DESC_PATTERN.matcher(normalizedSql).find();
        boolean orderDirectionKnown = DESC_PATTERN.matcher(normalizedSql).find() || ASC_PATTERN.matcher(normalizedSql).find();
        Integer limitValue = extractSqlLimitValue(normalizedSql);
        boolean limitValueKnown = limitValue != null;
        return new SqlShape(List.copyOf(usedTables), List.copyOf(usedMetrics), hasAggregation, hasGroupBy, hasOrderBy,
            hasLimit, hasDistinct, hasTimePredicate, hasTimeBucket, orderDescending, orderDirectionKnown,
            limitValueKnown, limitValue);
    }

    private Integer extractSqlLimitValue(String normalizedSql) {
        Integer limitValue = extractFirstInt(SQL_LIMIT_VALUE_PATTERN, normalizedSql);
        if (limitValue != null) {
            return limitValue;
        }
        limitValue = extractFirstInt(SQL_TOP_VALUE_PATTERN, normalizedSql);
        if (limitValue != null) {
            return limitValue;
        }
        return extractFirstInt(SQL_FETCH_FIRST_VALUE_PATTERN, normalizedSql);
    }

    private boolean detectTimePredicate(String normalizedSql, Set<String> knownTimeColumns) {
        if (!WHERE_PATTERN.matcher(normalizedSql).find()) {
            return false;
        }
        if (DATE_LITERAL_PATTERN.matcher(normalizedSql).find() || TIME_FUNCTION_PATTERN.matcher(normalizedSql).find()) {
            return true;
        }
        if (containsAny(normalizedSql, " created_at ", " create_time ", " updated_at ", " update_time ", " order_date ",
            " biz_date ", " stat_date ", " date ", " time ", " month ", " year ", " day ")) {
            return true;
        }
        return knownTimeColumns.stream().anyMatch(column -> normalizedSql.contains(column.toLowerCase(Locale.ROOT)));
    }

    private boolean detectTimeBucket(String normalizedSql, Set<String> knownTimeColumns) {
        if (!GROUP_BY_PATTERN.matcher(normalizedSql).find()) {
            return false;
        }
        if (containsAny(normalizedSql, "date_trunc(", "date(", "strftime(", "to_date(", "extract(", "year(", "month(", "day(")) {
            return true;
        }
        if (containsAny(normalizedSql, " by day", " by month", " by week", " by year")) {
            return true;
        }
        return knownTimeColumns.stream().anyMatch(column -> normalizedSql.contains(column.toLowerCase(Locale.ROOT)));
    }

    private Set<String> extractKnownTimeColumns(SqlGuardCheckRequest request) {
        Set<String> columns = new LinkedHashSet<>();
        if (request == null) {
            return columns;
        }
        collectTimeColumns(request.getTableSchemas(), columns);
        collectTimeColumns(request.getSemanticHits(), columns);
        collectTimeColumns(request.getBusinessKnowledgeHits(), columns);
        return columns;
    }

    private void collectTimeColumns(JsonNode node, Set<String> columns) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String value = node.asText();
            if (isLikelyTimeColumn(value)) {
                columns.add(value.toLowerCase(Locale.ROOT));
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectTimeColumns(item, columns);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode value = entry.getValue();
            if (value != null && value.isTextual()
                && ("name".equalsIgnoreCase(fieldName) || "columnName".equalsIgnoreCase(fieldName)
                    || "fieldName".equalsIgnoreCase(fieldName) || "column".equalsIgnoreCase(fieldName))
                && isLikelyTimeColumn(value.asText())) {
                columns.add(value.asText().toLowerCase(Locale.ROOT));
            }
            collectTimeColumns(value, columns);
        });
    }

    private boolean isLikelyTimeColumn(String value) {
        String normalized = StringUtils.trimToEmpty(value).toLowerCase(Locale.ROOT);
        return containsAny(normalized, "date", "time", "day", "week", "month", "year", "created", "updated", "dt",
            "biz_date", "stat_date", "order_date");
    }

    private List<String> extractReferencedTables(Statement statement) {
        try {
            return new TablesNamesFinder().getTableList(statement)
                .stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
        }
        catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> extractUsedMetrics(String sql) {
        Set<String> metrics = new LinkedHashSet<>();
        Matcher matcher = AGGREGATE_PATTERN.matcher(StringUtils.defaultString(sql));
        while (matcher.find()) {
            String alias = matcher.group(3);
            if (StringUtils.isNotBlank(alias)) {
                metrics.add(alias.trim());
                continue;
            }
            String functionName = Objects.toString(matcher.group(1), "").toUpperCase(Locale.ROOT);
            String argument = Objects.toString(matcher.group(2), "").trim();
            metrics.add(functionName + "(" + argument + ")");
        }
        return List.copyOf(metrics);
    }

    private Statement parseSingleSelectStatement(String sql) {
        String normalizedSql = stripTrailingSemicolons(sql);
        if (normalizedSql.isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        try {
            List<Statement> statements = CCJSqlParserUtil.parseStatements(normalizedSql).getStatements();
            if (statements == null || statements.isEmpty()) {
                throw new IllegalArgumentException("SQL 不能为空");
            }
            if (statements.size() > 1) {
                throw new IllegalArgumentException("仅支持单条 SELECT / WITH 查询");
            }
            Statement statement = statements.get(0);
            if (!(statement instanceof Select)) {
                throw new IllegalArgumentException("sql_guard.check 仅校验 SELECT / WITH 查询");
            }
            return statement;
        }
        catch (IllegalArgumentException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("SQL 解析失败，请检查语法后重试", ex);
        }
    }

    private String stripTrailingSemicolons(String sql) {
        String trimmed = StringUtils.trimToEmpty(sql);
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String buildIntentExplanation(QueryIntent intent) {
        List<String> fragments = new ArrayList<>();
        if (intent.requiresAggregation()) {
            fragments.add("问题包含聚合指标诉求");
        }
        if (intent.requiresGrouping()) {
            fragments.add("问题包含按维度拆分诉求");
        }
        if (intent.requiresTimeFilter()) {
            fragments.add("问题包含明确时间窗口");
        }
        if (intent.requiresTrend()) {
            fragments.add("问题包含趋势或时间序列分析");
        }
        if (intent.requiresOrdering()) {
            fragments.add("问题包含排序或排名要求");
        }
        if (intent.requiresLimit()) {
            fragments.add("问题包含 TopN / Top1 行数限制");
        }
        if (intent.requiresDistinct()) {
            fragments.add("问题包含去重口径");
        }
        if (fragments.isEmpty()) {
            return "当前规则没有识别到强约束口径，主要执行基础 SQL 结构检查。";
        }
        return String.join("；", fragments) + "。";
    }

    private boolean containsAny(String value, String... needles) {
        if (value == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private Integer extractExpectedLimit(String query, boolean prefersDescending, boolean prefersAscending) {
        String safeQuery = StringUtils.defaultString(query);
        Matcher matcher = TOP_N_QUERY_PATTERN.matcher(safeQuery);
        if (matcher.find()) {
            for (int index = 1; index <= matcher.groupCount(); index++) {
                String group = matcher.group(index);
                if (StringUtils.isNotBlank(group)) {
                    try {
                        return Integer.parseInt(group.trim());
                    }
                    catch (NumberFormatException ex) {
                        return null;
                    }
                }
            }
        }
        boolean asksSingleExtreme = containsAny(safeQuery, "最多", "最高", "最低", "最少", "第一", "首位", "top1",
            "top 1", "highest", "lowest", "most", "least", "first");
        if (asksSingleExtreme && (prefersDescending || prefersAscending)) {
            return 1;
        }
        boolean asksSingleTarget = containsAny(safeQuery, "哪个", "哪位", "哪一个", "谁");
        if (asksSingleTarget && (prefersDescending || prefersAscending)) {
            return 1;
        }
        return null;
    }

    private Integer extractFirstInt(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(StringUtils.defaultString(value));
        if (!matcher.find()) {
            return null;
        }
        String group = matcher.group(1);
        if (StringUtils.isBlank(group)) {
            return null;
        }
        try {
            return Integer.parseInt(group.trim());
        }
        catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ProfileContext(AgentDatasource agentDatasource, Datasource datasource, DbConfigBO dbConfig,
        Accessor accessor, List<String> visibleTables, Map<String, List<String>> visibleTablesByName,
        Map<String, List<String>> visibleTablesByLeafName, Map<String, List<String>> visibleColumnsByTable,
        Map<String, Set<String>> visibleColumnNameSetByTable, Set<String> columnRestrictedTables) {
    }

    private record QueryIntent(boolean requiresAggregation, boolean requiresGrouping, boolean requiresTimeFilter,
        boolean requiresOrdering, boolean requiresLimit, boolean requiresDistinct, boolean requiresTrend,
        boolean prefersDescending, boolean prefersAscending, Integer expectedLimit) {
    }

    private record HumanFeedbackConstraint(String feedbackContent, Set<String> requiredStatusTokens,
        Set<String> excludedStatusTokens) {

        private static HumanFeedbackConstraint empty() {
            return new HumanFeedbackConstraint("", Set.of(), Set.of());
        }

        private boolean hasConstraints() {
            return StringUtils.isNotBlank(feedbackContent)
                && (!requiredStatusTokens.isEmpty() || !excludedStatusTokens.isEmpty());
        }
    }

    private record SqlShape(List<String> usedTables, List<String> usedMetrics, boolean hasAggregation, boolean hasGroupBy,
        boolean hasOrderBy, boolean hasLimit, boolean hasDistinct, boolean hasTimePredicate, boolean hasTimeBucket,
        boolean orderDescending, boolean orderDirectionKnown, boolean limitValueKnown, Integer limitValue) {
    }

}
