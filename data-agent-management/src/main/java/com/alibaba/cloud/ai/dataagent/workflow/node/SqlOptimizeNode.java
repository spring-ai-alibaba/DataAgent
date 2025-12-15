/*
 * Copyright 2025 the original author or authors.
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

package com.alibaba.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.dataagent.common.util.*;
import com.alibaba.cloud.ai.dataagent.config.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.common.enums.TextType;
import com.alibaba.cloud.ai.dataagent.connector.config.DbConfig;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.common.constant.Constant.*;

/**
 * 优化SQL生成结果，直到分数满足要求或者达到最大次数
 *
 * @author vlsmb
 */
@Slf4j
@Component
@AllArgsConstructor
public class SqlOptimizeNode implements NodeAction {

	private final Nl2SqlService nl2SqlService;

	private final DataAgentProperties properties;

	private final DatabaseUtil databaseUtil;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		// 获取优化轮次和上次SQL
		int count = state.value(SQL_OPTIMIZE_COUNT, 0);
		String bestSql = StateUtil.getStringValue(state, SQL_OPTIMIZE_BEST_SQL, "");
		if (count >= properties.getMaxSqlOptimizeCount()) {
			log.info("optimize sql count reach max count");
			return Map.of(SQL_OPTIMIZE_FINISHED, true, SQL_GENERATE_OUTPUT, performFinalValidation(bestSql));
		}

		// 获取上次的分数
		double bestScore = StateUtil.getObjectValue(state, SQL_OPTIMIZE_BEST_SCORE, Double.class);
		Map<String, Object> result = new HashMap<>(Map.of(SQL_OPTIMIZE_FINISHED, false, SQL_OPTIMIZE_COUNT, count + 1));
		log.info("optimize sql count: {}, best sql: {}, best score: {}", count, bestSql, bestScore);

		// 获取数据库类型
		String agentIdStr = state.value(AGENT_ID, String.class).orElseThrow(IllegalStateException::new);
		Integer agentId = Integer.parseInt(agentIdStr);
		DbConfig dbConfig = databaseUtil.getAgentDbConfig(agentId);
		String dialect = dbConfig.getDialectType();

		// 生成优化SQL
		StringBuilder sqlCollector = new StringBuilder();
		Flux<String> sqlFlux = nl2SqlService.generateOptimizedSql(bestSql, null, count, dialect)
			.doOnNext(sqlCollector::append);

		// 创建返回Flux
		Flux<ChatResponse> displayFlux = Flux
			.just(ChatResponseUtil.createResponse("正在进行第 " + (count + 1) + " 次优化SQL..."),
					ChatResponseUtil.createPureResponse(TextType.SQL.getStartSign()))
			.concatWith(sqlFlux.map(r -> ChatResponseUtil.createTrimResponse(r, TextType.SQL)))
			.concatWith(Flux.defer(() -> {
				// 计算SQL分数
				String sql = sqlCollector.toString();
				SqlQualityScore score = evaluateSqlQuality(sql);
				return Flux.just(ChatResponseUtil.createPureResponse(TextType.SQL.getEndSign()),
						ChatResponseUtil.createResponse("当前轮次SQL优化完成！分数为：" + score.totalScore));
			}));
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, v -> {
					String sql = sqlCollector.toString();
					SqlQualityScore score = evaluateSqlQuality(sql);
					if (score.totalScore > bestScore) {
						// 替换为新的SQL
						log.info("optimize sql score: {}, sql: {}", score.totalScore, sql);
						result.put(SQL_OPTIMIZE_BEST_SQL, sql);
						result.put(SQL_OPTIMIZE_BEST_SCORE, score.totalScore);
						if (score.totalScore > properties.getSqlScoreThreshold()) {
							// 满足要求，结束
							result.putAll(Map.of(SQL_OPTIMIZE_FINISHED, true, SQL_GENERATE_OUTPUT,
									performFinalValidation(sql)));
						}
					}
					return result;
				}, displayFlux);
		return Map.of(SQL_OPTIMIZE_BEST_SQL, generator);
	}

	/**
	 * Evaluate SQL quality
	 */
	private SqlQualityScore evaluateSqlQuality(String sql) {
		SqlQualityScore score = new SqlQualityScore();

		// Syntax check (40% weight)
		score.syntaxScore = validateSqlSyntax(sql);

		// Security check (30% weight)
		score.securityScore = validateSqlSecurity(sql);

		// Performance check (30% weight)
		score.performanceScore = evaluateSqlPerformance(sql);

		// Calculate total score
		score.totalScore = (score.syntaxScore * 0.4 + score.securityScore * 0.3 + score.performanceScore * 0.3);

		return score;
	}

	/**
	 * Verify SQL syntax
	 */
	private double validateSqlSyntax(String sql) {
		if (sql == null || sql.trim().isEmpty())
			return 0.0;

		double score = 1.0;
		String upperSql = sql.toUpperCase();

		// Basic syntax check
		if (!upperSql.contains("SELECT"))
			score -= 0.3;
		if (!upperSql.contains("FROM"))
			score -= 0.3;

		// Check bracket matching
		long openParens = sql.chars().filter(ch -> ch == '(').count();
		long closeParens = sql.chars().filter(ch -> ch == ')').count();
		if (openParens != closeParens)
			score -= 0.2;

		// Check quote matching
		long singleQuotes = sql.chars().filter(ch -> ch == '\'').count();
		if (singleQuotes % 2 != 0)
			score -= 0.2;

		return Math.max(0.0, score);
	}

	/**
	 * Verify SQL security
	 */
	private double validateSqlSecurity(String sql) {
		if (sql == null)
			return 0.0;

		double score = 1.0;
		String upperSql = sql.toUpperCase();

		// Check for dangerous operations
		String[] dangerousKeywords = { "DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "CREATE", "TRUNCATE" };
		for (String keyword : dangerousKeywords) {
			if (upperSql.contains(keyword)) {
				score -= 0.3;
				log.warn("检测到潜在危险SQL操作: {}", keyword);
			}
		}

		// Check for SQL injection patterns
		String[] injectionPatterns = { "--", "/*", "*/", "UNION", "OR 1=1", "OR '1'='1'" };
		for (String pattern : injectionPatterns) {
			if (upperSql.contains(pattern.toUpperCase())) {
				score -= 0.2;
				log.warn("检测到潜在SQL注入模式: {}", pattern);
			}
		}

		return Math.max(0.0, score);
	}

	/**
	 * Evaluate SQL performance
	 */
	private double evaluateSqlPerformance(String sql) {
		if (sql == null)
			return 0.0;

		double score = 1.0;
		String upperSql = sql.toUpperCase();

		// Check for SELECT *
		if (upperSql.contains("SELECT *")) {
			score -= 0.2;
			log.warn("检测到SELECT *，建议明确指定字段");
		}

		// Check WHERE conditions
		if (!upperSql.contains("WHERE")) {
			score -= 0.3;
			log.warn("查询缺少WHERE条件，可能影响性能");
		}

		return Math.max(0.0, score);
	}

	/**
	 * Final verification and cleanup
	 */
	private String performFinalValidation(String sql) {
		if (sql == null || sql.trim().isEmpty()) {
			throw new IllegalArgumentException("生成的SQL为空");
		}

		sql = nl2SqlService.sqlTrim(sql);
		// Basic cleanup
		sql = sql.trim();
		if (!sql.endsWith(";")) {
			sql += ";";
		}

		// Security check
		if (validateSqlSecurity(sql) < 0.5) {
			log.warn("生成的SQL存在安全风险，但继续执行");
		}

		return sql;
	}

	/**
	 * SQL quality score
	 */
	private static class SqlQualityScore {

		double syntaxScore = 0.0;

		double securityScore = 0.0;

		double performanceScore = 0.0;

		double totalScore = 0.0;

	}

}
