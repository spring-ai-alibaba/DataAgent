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
package com.alibaba.cloud.ai.dataagent.common;

import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import com.alibaba.cloud.ai.dataagent.dto.prompt.IntentRecognitionOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

public final class TestFixtures {

	private static final ObjectMapper OBJECT_MAPPER = JsonUtil.getObjectMapper();

	private TestFixtures() {
	}

	// --- State Factory ---

	public static OverAllState createStateWith(String... keys) {
		OverAllState state = new OverAllState();
		for (String key : keys) {
			state.registerKeyAndStrategy(key, new ReplaceStrategy());
		}
		return state;
	}

	public static OverAllState createStateWith(Map<String, Object> values) {
		OverAllState state = new OverAllState();
		values.keySet().forEach(key -> state.registerKeyAndStrategy(key, new ReplaceStrategy()));
		state.updateState(values);
		return state;
	}

	// --- Schema Fixtures (HashMap-based, matches how OverAllState deserializes) ---

	public static Map<String, Object> createSchemaMap(String name, String... tableNames) {
		List<Map<String, Object>> tables = new ArrayList<>();
		for (String tableName : tableNames) {
			Map<String, Object> table = new HashMap<>();
			table.put("name", tableName);
			table.put("description", tableName + " table");
			table.put("column", new ArrayList<>());
			table.put("primaryKeys", new ArrayList<>());
			tables.add(table);
		}
		Map<String, Object> schema = new HashMap<>();
		schema.put("name", name);
		schema.put("description", "Test schema");
		schema.put("tableCount", tables.size());
		schema.put("table", tables);
		schema.put("foreignKeys", new ArrayList<>());
		return schema;
	}

	// --- QueryEnhance Fixtures ---

	public static Map<String, Object> createQueryEnhanceMap(String query) {
		Map<String, Object> qe = new HashMap<>();
		qe.put("canonical_query", query);
		qe.put("expanded_queries", new ArrayList<>(List.of(query)));
		return qe;
	}

	public static QueryEnhanceOutputDTO createQueryEnhanceDTO(String query) {
		QueryEnhanceOutputDTO dto = new QueryEnhanceOutputDTO();
		dto.setCanonicalQuery(query);
		dto.setExpandedQueries(List.of(query));
		return dto;
	}

	// --- Intent Recognition Fixtures ---

	public static IntentRecognitionOutputDTO createIntentDTO(String classification) {
		IntentRecognitionOutputDTO dto = new IntentRecognitionOutputDTO();
		dto.setClassification(classification);
		return dto;
	}

	// --- Plan Fixtures ---

	public static Plan createPlan(String thoughtProcess, ExecutionStep... steps) {
		Plan plan = new Plan();
		plan.setThoughtProcess(thoughtProcess);
		plan.setExecutionPlan(List.of(steps));
		return plan;
	}

	public static ExecutionStep createSqlStep(int stepNum, String instruction) {
		ExecutionStep step = new ExecutionStep();
		step.setStep(stepNum);
		step.setToolToUse(SQL_GENERATE_NODE);
		ExecutionStep.ToolParameters params = new ExecutionStep.ToolParameters();
		params.setInstruction(instruction);
		step.setToolParameters(params);
		return step;
	}

	public static ExecutionStep createPythonStep(int stepNum, String instruction) {
		ExecutionStep step = new ExecutionStep();
		step.setStep(stepNum);
		step.setToolToUse(PYTHON_GENERATE_NODE);
		ExecutionStep.ToolParameters params = new ExecutionStep.ToolParameters();
		params.setInstruction(instruction);
		step.setToolParameters(params);
		return step;
	}

	public static ExecutionStep createReportStep(int stepNum, String summary) {
		ExecutionStep step = new ExecutionStep();
		step.setStep(stepNum);
		step.setToolToUse(REPORT_GENERATOR_NODE);
		ExecutionStep.ToolParameters params = new ExecutionStep.ToolParameters();
		params.setSummaryAndRecommendations(summary);
		step.setToolParameters(params);
		return step;
	}

	public static String planToJson(Plan plan) {
		try {
			return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(plan);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to serialize plan", e);
		}
	}

	// --- SqlRetryDto Fixtures ---

	public static SqlRetryDto createEmptyRetry() {
		return SqlRetryDto.empty();
	}

	public static SqlRetryDto createSemanticRetry(String reason) {
		return SqlRetryDto.semantic(reason);
	}

	public static SqlRetryDto createSqlExecuteRetry(String reason) {
		return SqlRetryDto.sqlExecute(reason);
	}

	// --- Common Plan JSON ---

	public static String createSingleSqlPlanJson() {
		return planToJson(createPlan("Generate SQL", createSqlStep(1, "Query all users")));
	}

	public static String createMultiStepPlanJson() {
		return planToJson(createPlan("Multi-step analysis",
				createSqlStep(1, "Query user data"),
				createPythonStep(2, "Analyze results"),
				createReportStep(3, "Generate report")));
	}

}
