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

package com.alibaba.cloud.ai.node;

import com.alibaba.cloud.ai.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.pojo.ExecutionStep;
import com.alibaba.cloud.ai.pojo.Plan;
import com.alibaba.cloud.ai.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.util.ChatResponseUtil;
import com.alibaba.cloud.ai.util.StateUtil;
import com.alibaba.cloud.ai.util.StreamingChatGeneratorUtil;
import com.google.common.util.concurrent.AtomicDouble;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.alibaba.cloud.ai.constant.Constant.*;

/**
 * Enhanced SQL generation node that handles SQL query regeneration with advanced
 * optimization features. This node is responsible for: - Multi-round SQL optimization and
 * refinement - Syntax validation and security analysis - Performance optimization and
 * intelligent caching - Handling execution exceptions and semantic consistency failures -
 * Managing retry logic with schema advice - Providing streaming feedback during
 * regeneration process
 *
 * @author zhangshenghang
 */
@Slf4j
@Component
public class SqlGenerateNode implements NodeAction {

	private static final int MAX_OPTIMIZATION_ROUNDS = 3;

	private final Nl2SqlService nl2SqlService;

	private final BeanOutputConverter<Plan> converter;

	public SqlGenerateNode(Nl2SqlService nl2SqlService) {
		this.nl2SqlService = nl2SqlService;
		this.converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
		});
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		log.info("Entering {} node", this.getClass().getSimpleName());

		// Get necessary input parameters
		String plannerNodeOutput = StateUtil.getStringValue(state, PLANNER_NODE_OUTPUT);
		Plan plan = converter.convert(plannerNodeOutput);
		Integer currentStep = StateUtil.getObjectValue(state, PLAN_CURRENT_STEP, Integer.class, 1);

		List<ExecutionStep> executionPlan = Optional.ofNullable(plan).orElseThrow().getExecutionPlan();
		ExecutionStep executionStep = executionPlan.get(currentStep - 1);
		ExecutionStep.ToolParameters toolParameters = executionStep.getToolParameters();

		// Execute business logic first - determine what needs to be regenerated
		Map<String, Object> result = new HashMap<>(Map.of(SQL_GENERATE_OUTPUT, SQL_EXECUTE_NODE));
		String displayMessage;

		Consumer<String> finalSqlConsumer = finalSql -> {
			toolParameters.setSqlQuery(finalSql);
			log.info("[{}] Regenerated SQL: {}", this.getClass().getSimpleName(), finalSql);
			result.put(PLANNER_NODE_OUTPUT, plan.toJsonStr());
		};
		Flux<String> sqlFlux;

		if (StateUtil.hasValue(state, SQL_EXECUTE_NODE_EXCEPTION_OUTPUT)) {
			displayMessage = "检测到SQL执行异常，开始重新生成SQL...";
			sqlFlux = handleSqlExecutionException(state, toolParameters, finalSqlConsumer);
		}
		else if (isSemanticConsistencyFailed(state)) {
			displayMessage = "语义一致性校验未通过，开始重新生成SQL...";
			sqlFlux = handleSemanticConsistencyFailure(state, toolParameters, finalSqlConsumer);
		}
		else {
			throw new IllegalStateException("SQL generation node was called unexpectedly");
		}

		// Create display flux for user experience only
		Flux<ChatResponse> preFlux = Flux.just(ChatResponseUtil.createCustomStatusResponse(displayMessage));
		Flux<ChatResponse> displayFlux = preFlux.concatWith(sqlFlux.map(ChatResponseUtil::createCustomStatusResponse))
			.concatWith(Flux.just(ChatResponseUtil.createCustomStatusResponse("SQL重新生成完成，准备执行")));

		var generator = StreamingChatGeneratorUtil.createStreamingGeneratorWithMessages(this.getClass(), state, v -> {
			log.debug("resultMap: {}", result);
			return result;
		}, displayFlux);

		return Map.of(SQL_GENERATE_OUTPUT, generator);
	}

	/**
	 * Handle SQL execution exception
	 */
	private Flux<String> handleSqlExecutionException(OverAllState state, ExecutionStep.ToolParameters toolParameters,
			Consumer<String> finalSqlConsumer) {
		String sqlException = StateUtil.getStringValue(state, SQL_EXECUTE_NODE_EXCEPTION_OUTPUT);
		log.info("Detected SQL execution exception, starting to regenerate SQL: {}", sqlException);

		List<String> evidenceList = StateUtil.getListValue(state, EVIDENCES);
		SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);

		return regenerateSql(state, toolParameters.toJsonStr(), evidenceList, schemaDTO,
				SQL_EXECUTE_NODE_EXCEPTION_OUTPUT, toolParameters.getSqlQuery(), finalSqlConsumer);
	}

	/**
	 * Handle semantic consistency validation failure
	 */
	private Flux<String> handleSemanticConsistencyFailure(OverAllState state,
			ExecutionStep.ToolParameters toolParameters, Consumer<String> finalSqlConsumer) {
		log.info("Semantic consistency validation failed, starting to regenerate SQL");

		List<String> evidenceList = StateUtil.getListValue(state, EVIDENCES);
		SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);

		return regenerateSql(state, toolParameters.toJsonStr(), evidenceList, schemaDTO,
				SEMANTIC_CONSISTENCY_NODE_RECOMMEND_OUTPUT, toolParameters.getSqlQuery(), finalSqlConsumer);
	}

	/**
	 * Check if semantic consistency validation failed
	 */
	private boolean isSemanticConsistencyFailed(OverAllState state) {
		return !StateUtil.getObjectValue(state, SEMANTIC_CONSISTENCY_NODE_OUTPUT, Boolean.class, true);
	}

	/**
	 * If the first planned execution fails, regenerate SQL using enhanced SQL with
	 * multi-round optimization, security checks, and performance analysis
	 * @param finalSqlConsumer 处理最终生成SQL的消费者，如果失败，则sql为null
	 * @return AI中间输出过程的Flux
	 */
	private Flux<String> regenerateSql(OverAllState state, String input, List<String> evidenceList, SchemaDTO schemaDTO,
			String exceptionOutputKey, String originalSql, Consumer<String> finalSqlConsumer) {
		String exceptionMessage = StateUtil.getStringValue(state, exceptionOutputKey);
		log.info("开始增强SQL生成流程 - 原始SQL: {}, 异常信息: {}", originalSql, exceptionMessage);

		// Multi-round SQL optimization process
		AtomicReference<String> bestSqlRef = new AtomicReference<>(null);
		AtomicDouble bestScoreRef = new AtomicDouble(0.0);
		AtomicInteger roundRef = new AtomicInteger(1);

		// 检查SQL，并评分，为true则直接使用当前SQL
		Function<String, Boolean> checkSqlFunc = (currentSql) -> {
			int round = roundRef.get();
			if (currentSql == null || currentSql.trim().isEmpty()) {
				log.warn("第{}轮SQL生成结果为空，跳过", round);
				return false;
			}

			// Evaluate SQL quality
			SqlQualityScore score = evaluateSqlQuality(currentSql);
			log.info("第{}轮SQL评分: 语法={}, 安全={}, 性能={}, 总分={}", round, score.syntaxScore, score.securityScore,
					score.performanceScore, score.totalScore);

			// Update best SQL
			if (score.totalScore > bestScoreRef.get()) {
				bestSqlRef.set(currentSql);
				bestScoreRef.set(score.totalScore);
				log.info("第{}轮产生了更好的SQL，总分提升到{}", round, score.totalScore);
			}

			// End early if the quality is high enough
			if (score.totalScore >= 0.95) {
				log.info("SQL质量分数达到{}，提前结束优化", score.totalScore);
				return true;
			}
			return false;
		};

		// 最好的SQL消费者，如果失败，则sql为null
		Consumer<String> bestSqlConsumer = (bestSql) -> {
			if (bestSql != null) {
				// Final verification and cleanup
				bestSql = performFinalValidation(bestSql);
				log.info("增强SQL生成完成，最终SQL: {}", bestSql);
			}
			finalSqlConsumer.accept(bestSql);
		};

		Supplier<Flux<String>> reGenerateSupplier = new Supplier<>() {
			@Override
			public Flux<String> get() {
				Flux<String> sqlFlux = nl2SqlService
					.generateOptimizedSql(Optional.ofNullable(bestSqlRef.get()).orElse(originalSql), exceptionMessage,
							roundRef.get())
					.collect(StringBuilder::new, StringBuilder::append)
					.map(StringBuilder::toString)
					.flatMapMany(sql -> Flux.just(nl2SqlService.sqlTrim(sql)).expand(newSql -> {
						if (checkSqlFunc.apply(newSql) || roundRef.getAndIncrement() > MAX_OPTIMIZATION_ROUNDS) {
							String bestSql = bestSqlRef.get();
							bestSqlConsumer.accept(bestSql);
							return Flux.just(bestSql);
						}
						else {
							return this.get();
						}
					}));
				return Flux.just("正在重新生成SQL...\n").concatWith(sqlFlux).concatWith(Flux.just("重新生成SQL完成..."));
			}
		};

		Flux<String> sqlFlux = nl2SqlService.generateSql(evidenceList, input, schemaDTO, originalSql, exceptionMessage);
		Mono<String> sqlMono = sqlFlux.collect(StringBuilder::new, StringBuilder::append).map(StringBuilder::toString);
		return Flux.just("正在生成SQL...\n")
			.concatWith(sqlMono.flatMapMany(sql -> Flux.just(nl2SqlService.sqlTrim(sql)).expand(newSql -> {
				if (checkSqlFunc.apply(newSql) || roundRef.getAndIncrement() > MAX_OPTIMIZATION_ROUNDS) {
					String bestSql = bestSqlRef.get();
					bestSqlConsumer.accept(bestSql);
					return Flux.just(bestSql);
				}
				else {
					return reGenerateSupplier.get();
				}
			})))
			.concatWith(Flux.just("SQL生成完成！\n"));
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
