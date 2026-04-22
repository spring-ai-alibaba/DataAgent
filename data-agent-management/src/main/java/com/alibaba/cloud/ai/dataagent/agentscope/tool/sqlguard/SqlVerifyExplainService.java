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

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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

    public SqlGuardCheckResult explain(SqlGuardCheckRequest request) {
        String query = StringUtils.trimToEmpty(request == null ? null : request.getQuery());
        String sql = StringUtils.trimToEmpty(request == null ? null : request.getSql());
        if (StringUtils.isBlank(query)) {
            throw new IllegalArgumentException("sql_guard.check 需要 query");
        }
        if (StringUtils.isBlank(sql)) {
            throw new IllegalArgumentException("sql_guard.check 需要 sql");
        }

        Statement statement;
        try {
            statement = parseSingleSelectStatement(sql);
        }
        catch (IllegalArgumentException ex) {
            return SqlGuardCheckResult.builder()
                .query(query)
                .sql(sql)
                .isAligned(false)
                .summary("SQL 无法通过语法解析，无法继续做结构和意图一致性校验。")
                .explainedIntent(buildIntentExplanation(analyzeQueryIntent(query)))
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

        QueryIntent intent = analyzeQueryIntent(query);
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

        boolean aligned = problems.stream().noneMatch(problem -> isBlockingSeverity(problem.getSeverity()));
        String summary = aligned ? "SQL 通过了当前规则版意图一致性校验。"
            : "检测到 %d 个可能影响答案正确性的意图一致性问题。".formatted(problems.size());
        if (aligned) {
            fixSuggestions.add("当前规则校验通过；如要进一步提高置信度，可继续核对执行结果与最终答案解释。");
        }
        return SqlGuardCheckResult.builder()
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

    private record QueryIntent(boolean requiresAggregation, boolean requiresGrouping, boolean requiresTimeFilter,
            boolean requiresOrdering, boolean requiresLimit, boolean requiresDistinct, boolean requiresTrend,
            boolean prefersDescending, boolean prefersAscending, Integer expectedLimit) {
    }

    private record SqlShape(List<String> usedTables, List<String> usedMetrics, boolean hasAggregation, boolean hasGroupBy,
            boolean hasOrderBy, boolean hasLimit, boolean hasDistinct, boolean hasTimePredicate, boolean hasTimeBucket,
            boolean orderDescending, boolean orderDirectionKnown, boolean limitValueKnown, Integer limitValue) {
    }

}
