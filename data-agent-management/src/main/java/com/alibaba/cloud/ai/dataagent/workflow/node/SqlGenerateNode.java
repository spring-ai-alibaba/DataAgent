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

import com.alibaba.cloud.ai.dataagent.connector.config.DbConfig;
import com.alibaba.cloud.ai.dataagent.config.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.common.enums.TextType;
import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.common.util.DatabaseUtil;
import com.alibaba.cloud.ai.dataagent.common.util.PlanProcessUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.common.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.common.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.common.util.StateUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.common.constant.Constant.*;

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
@AllArgsConstructor
public class SqlGenerateNode implements NodeAction {

	private final Nl2SqlService nl2SqlService;

	private final DatabaseUtil databaseUtil;

	private final DataAgentProperties properties;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		// 判断是否达到最大尝试次数
		int count = state.value(SQL_GENERATE_COUNT, 0);
		if (count >= properties.getMaxSqlRetryCount()) {
			log.error("SQL generation failed after {} attempts, giving up", count);
			return Map.of(SQL_GENERATE_OUTPUT, StateGraph.END);
		}

		// 获取当前执行步骤的toolParameters信息，用于注入到SQL生成提示词中
		String executionDescription;
		ExecutionStep.ToolParameters currentStepParams = PlanProcessUtil.getCurrentExecutionStep(state)
			.getToolParameters();
		executionDescription = currentStepParams != null ? currentStepParams.getDescription() : "无";

		// 准备生成SQL
		String displayMessage;
		Flux<String> sqlFlux;
		SqlRetryDto retryDto = StateUtil.getObjectValue(state, SQL_REGENERATE_REASON, SqlRetryDto.class,
				SqlRetryDto.empty());

		if (retryDto.sqlExecuteFail()) {
			displayMessage = "检测到SQL执行异常，开始重新生成SQL...";
			sqlFlux = handleRetryGenerateSql(state, StateUtil.getStringValue(state, SQL_GENERATE_OUTPUT, ""),
					retryDto.reason(), executionDescription);
		}
		else if (retryDto.semanticFail()) {
			displayMessage = "语义一致性校验未通过，开始重新生成SQL...";
			sqlFlux = handleRetryGenerateSql(state, StateUtil.getStringValue(state, SQL_GENERATE_OUTPUT, ""),
					retryDto.reason(), executionDescription);
		}
		else {
			displayMessage = "开始生成SQL...";
			sqlFlux = handleGenerateSql(state, executionDescription);
		}

		// 准备返回结果，同时需要清除一些状态数据
		Map<String, Object> result = new HashMap<>(
				Map.of(SQL_GENERATE_OUTPUT, StateGraph.END, SQL_GENERATE_COUNT, count + 1, SQL_OPTIMIZE_COUNT, 0,
						SQL_OPTIMIZE_BEST_SCORE, 0.0, SQL_REGENERATE_REASON, SqlRetryDto.empty()));

		// Create display flux for user experience only
		StringBuilder sqlCollector = new StringBuilder();
		Flux<ChatResponse> preFlux = Flux.just(ChatResponseUtil.createResponse(displayMessage),
				ChatResponseUtil.createPureResponse(TextType.SQL.getStartSign()));
		Flux<ChatResponse> displayFlux = preFlux
			.concatWith(sqlFlux.doOnNext(sqlCollector::append)
				.map(r -> ChatResponseUtil.createTrimResponse(r, TextType.SQL)))
			.concatWith(Flux.just(ChatResponseUtil.createPureResponse(TextType.SQL.getEndSign()),
					ChatResponseUtil.createResponse("SQL生成完成，准备执行")));

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, v -> {
					String sql = nl2SqlService.sqlTrim(sqlCollector.toString());
					result.put(SQL_GENERATE_OUTPUT, sql);
					result.put(SQL_OPTIMIZE_BEST_SQL, sql);
					return result;
				}, displayFlux);

		return Map.of(SQL_GENERATE_OUTPUT, generator);
	}

	private Flux<String> handleRetryGenerateSql(OverAllState state, String originalSql, String errorMsg,
			String executionDescription) {
		String evidence = StateUtil.getStringValue(state, EVIDENCE);
		SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
		String userQuery = StateUtil.getCanonicalQuery(state);
		String agentIdStr = state.value(AGENT_ID, String.class).orElseThrow(IllegalStateException::new);
		Integer agentId = Integer.parseInt(agentIdStr);
		DbConfig dbConfig = databaseUtil.getAgentDbConfig(agentId);
		String dialect = dbConfig.getDialectType();
		return nl2SqlService.generateSql(evidence, userQuery, schemaDTO, originalSql, errorMsg, dbConfig,
				executionDescription, dialect);
	}

	private Flux<String> handleGenerateSql(OverAllState state, String executionDescription) {
		return handleRetryGenerateSql(state, null, null, executionDescription);
	}

}
