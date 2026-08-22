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
package com.alibaba.cloud.ai.dataagent.workflow.node.sql;

import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeExecution;
import com.alibaba.cloud.ai.dataagent.workflow.node.SqlGenerateNode;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.execute;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqlGenerateNodeTest {

	private static final String TEST_PLAN_JSON = """
			{
			    "thought_process": "根据问题生成SQL",
			    "execution_plan": [
			        {
			            "step": 1,
			            "tool_to_use": "sql_generate_node",
			            "tool_parameters": {
			                "instruction": "SQL生成"
			            }
			        }
			    ]
			}
			""";

	private static final String MULTI_STEP_PLAN_JSON = """
			{
			  "thought_process": "先查用户再查订单",
			  "execution_plan": [
			    {
			      "step": 1,
			      "tool_to_use": "sql_generate_node",
			      "tool_parameters": {"instruction": "查询目标用户"}
			    },
			    {
			      "step": 2,
			      "tool_to_use": "sql_generate_node",
			      "tool_parameters": {"instruction": "根据用户ID查询订单"}
			    }
			  ]
			}
			""";

	private static final Map<String, Object> TEST_QUERY_ENHANCE;

	private static final Map<String, Object> TEST_SCHEMA;

	static {
		Map<String, Object> table = new HashMap<>();
		table.put("name", "users");
		table.put("description", "用户表");
		table.put("column", new ArrayList<>());
		table.put("primaryKeys", new ArrayList<>());

		Map<String, Object> schema = new HashMap<>();
		schema.put("name", "test_schema");
		schema.put("description", "测试schema");
		schema.put("tableCount", 1);
		schema.put("table", new ArrayList<>(List.of(table)));
		schema.put("foreignKeys", new ArrayList<>());

		Map<String, Object> queryEnhance = new HashMap<>();
		queryEnhance.put("canonical_query", "查询所有用户信息");
		queryEnhance.put("expanded_queries", new ArrayList<>(List.of("查询用户", "获取用户列表")));

		TEST_SCHEMA = schema;
		TEST_QUERY_ENHANCE = queryEnhance;
	}

	@Mock
	private Nl2SqlService nl2SqlService;

	@Mock
	private DataAgentProperties properties;

	private SqlGenerateNode sqlGenerateNode;

	@BeforeEach
	void setUp() {
		sqlGenerateNode = new SqlGenerateNode(nl2SqlService, properties);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(SQL_GENERATE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_GENERATE_COUNT, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_REGENERATE_REASON, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLANNER_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_CURRENT_STEP, new ReplaceStrategy());
		state.registerKeyAndStrategy(EVIDENCE, new ReplaceStrategy());
		state.registerKeyAndStrategy(TABLE_RELATION_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(DB_DIALECT_TYPE, new ReplaceStrategy());
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
		return state;
	}

	private void setupBasicState(OverAllState state) {
		state.updateState(Map.of(SQL_GENERATE_COUNT, 0, PLANNER_NODE_OUTPUT, TEST_PLAN_JSON, PLAN_CURRENT_STEP, 1,
				EVIDENCE, "test evidence", DB_DIALECT_TYPE, "mysql", QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE,
				TABLE_RELATION_OUTPUT, TEST_SCHEMA));
	}

	private void stubGeneratedSql(String sql) {
		when(nl2SqlService.generateSql(any())).thenReturn(Flux.just(sql));
		when(nl2SqlService.sqlTrim(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void simpleSelectQuery_validInput_generatesValidSql() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		stubGeneratedSql("SELECT * FROM users");

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);
		assertEquals("SELECT * FROM users", execution.finalResult().get(SQL_GENERATE_OUTPUT));
		assertEquals(1, execution.finalResult().get(SQL_GENERATE_COUNT));
	}

	@Test
	void queryWithWhereClause_validInput_generatesWhereCondition() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		stubGeneratedSql("SELECT * FROM users WHERE age > 18");

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);
		assertEquals("SELECT * FROM users WHERE age > 18", execution.finalResult().get(SQL_GENERATE_OUTPUT));
	}

	@Test
	void queryWithJoin_validInput_generatesJoinClause() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		String sql = "SELECT u.*, o.* FROM users u JOIN orders o ON u.id = o.user_id";
		stubGeneratedSql(sql);

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);
		assertEquals(sql, execution.finalResult().get(SQL_GENERATE_OUTPUT));
	}

	@Test
	void maxRetryCountReached_returnsErrorResponse() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(SQL_GENERATE_COUNT, 10, PLANNER_NODE_OUTPUT, TEST_PLAN_JSON, PLAN_CURRENT_STEP, 1,
				EVIDENCE, "test evidence", DB_DIALECT_TYPE, "mysql", QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE,
				TABLE_RELATION_OUTPUT, TEST_SCHEMA));

		when(properties.getMaxSqlRetryCount()).thenReturn(10);

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);
		assertEquals(StateGraph.END, execution.finalResult().get(SQL_GENERATE_OUTPUT));
		assertEquals(0, execution.finalResult().get(SQL_GENERATE_COUNT));
		assertTrue(execution.streamedText().contains("最大尝试次数：10"));
		verifyNoInteractions(nl2SqlService);
	}

	@Test
	void apply_nl2SqlServiceFailure_throwsException() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		when(nl2SqlService.generateSql(any())).thenThrow(new RuntimeException("NL2SQL service unavailable"));

		assertThrowsExactly(RuntimeException.class, () -> sqlGenerateNode.apply(state));
	}

	@Test
	void apply_missingSchemaState_throwsException() {
		OverAllState state = createTestState();
		state.updateState(Map.of(SQL_GENERATE_COUNT, 0, PLANNER_NODE_OUTPUT, TEST_PLAN_JSON, PLAN_CURRENT_STEP, 1,
				EVIDENCE, "test evidence", DB_DIALECT_TYPE, "mysql", QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE));

		when(properties.getMaxSqlRetryCount()).thenReturn(10);

		IllegalStateException exception = assertThrows(IllegalStateException.class, () -> sqlGenerateNode.apply(state));
		assertEquals("State key not found: " + TABLE_RELATION_OUTPUT, exception.getMessage());
	}

	@Test
	void apply_nullPlannerOutput_throwsException() {
		OverAllState state = createTestState();
		state
			.updateState(Map.of(SQL_GENERATE_COUNT, 0, PLAN_CURRENT_STEP, 1, EVIDENCE, "test evidence", DB_DIALECT_TYPE,
					"mysql", QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE, TABLE_RELATION_OUTPUT, TEST_SCHEMA));

		when(properties.getMaxSqlRetryCount()).thenReturn(10);

		IllegalStateException exception = assertThrows(IllegalStateException.class, () -> sqlGenerateNode.apply(state));
		assertEquals("计划节点输出为空", exception.getMessage());
	}

	@Test
	void apply_withRegenerateReason_includesReasonInPrompt() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(
				Map.of(SQL_REGENERATE_REASON, new SqlRetryDto("SQL execution error: table not found", false, true),
						SQL_GENERATE_OUTPUT, "SELECT * FROM nonexistent"));

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		stubGeneratedSql("SELECT * FROM users");

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);
		assertEquals("SELECT * FROM users", execution.finalResult().get(SQL_GENERATE_OUTPUT));
		ArgumentCaptor<SqlGenerationDTO> requestCaptor = ArgumentCaptor.forClass(SqlGenerationDTO.class);
		verify(nl2SqlService).generateSql(requestCaptor.capture());
		assertEquals("SELECT * FROM nonexistent", requestCaptor.getValue().getSql());
		assertEquals("SQL execution error: table not found", requestCaptor.getValue().getExceptionMessage());
	}

	@Test
	void apply_executionRetryReturnsSameSql_stopsRetryLoop() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(SQL_REGENERATE_REASON, SqlRetryDto.sqlExecute("same database error"),
				SQL_GENERATE_OUTPUT, "SELECT * FROM users;"));

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		when(nl2SqlService.generateSql(any())).thenReturn(Flux.just("SELECT * FROM users"));
		when(nl2SqlService.sqlTrim(any())).thenAnswer(invocation -> invocation.getArgument(0));

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);

		assertEquals(StateGraph.END, execution.finalResult().get(SQL_GENERATE_OUTPUT));
		assertTrue(execution.streamedText().contains("已停止重试"));
	}

	@Test
	void apply_laterStep_includesPreviousSqlResults() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(SQL_GENERATE_COUNT, 0, PLANNER_NODE_OUTPUT, MULTI_STEP_PLAN_JSON, PLAN_CURRENT_STEP, 2,
				EVIDENCE, "test evidence", DB_DIALECT_TYPE, "mysql", QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE,
				TABLE_RELATION_OUTPUT, TEST_SCHEMA, SQL_EXECUTE_NODE_OUTPUT,
				Map.of("step_1", "{\"data\":[{\"passport_id\":\"P100\"}]}")));
		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		when(nl2SqlService.generateSql(any())).thenReturn(Flux.just("SELECT * FROM orders"));

		sqlGenerateNode.apply(state);

		ArgumentCaptor<SqlGenerationDTO> requestCaptor = ArgumentCaptor.forClass(SqlGenerationDTO.class);
		verify(nl2SqlService).generateSql(requestCaptor.capture());
		assertTrue(requestCaptor.getValue().getPreviousStepResults().contains("step_1"));
		assertTrue(requestCaptor.getValue().getPreviousStepResults().contains("P100"));
	}

	@Test
	void apply_semanticFailureReason_includesValidationFeedback() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(SQL_REGENERATE_REASON,
				new SqlRetryDto("Semantic check failed: query intent mismatch", true, false), SQL_GENERATE_OUTPUT,
				"SELECT count(*) FROM orders"));

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		stubGeneratedSql("SELECT sum(amount) FROM orders");

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);
		assertEquals("SELECT sum(amount) FROM orders", execution.finalResult().get(SQL_GENERATE_OUTPUT));
		ArgumentCaptor<SqlGenerationDTO> requestCaptor = ArgumentCaptor.forClass(SqlGenerationDTO.class);
		verify(nl2SqlService).generateSql(requestCaptor.capture());
		assertEquals("SELECT count(*) FROM orders", requestCaptor.getValue().getSql());
		assertEquals("Semantic check failed: query intent mismatch", requestCaptor.getValue().getExceptionMessage());
	}

	@Test
	void apply_emptyEvidenceString_generatesWithoutEvidence() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(SQL_GENERATE_COUNT, 0, PLANNER_NODE_OUTPUT, TEST_PLAN_JSON, PLAN_CURRENT_STEP, 1,
				EVIDENCE, "", DB_DIALECT_TYPE, "mysql", QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE,
				TABLE_RELATION_OUTPUT, TEST_SCHEMA));

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		stubGeneratedSql("SELECT * FROM users");

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);
		assertEquals("SELECT * FROM users", execution.finalResult().get(SQL_GENERATE_OUTPUT));
		ArgumentCaptor<SqlGenerationDTO> requestCaptor = ArgumentCaptor.forClass(SqlGenerationDTO.class);
		verify(nl2SqlService).generateSql(requestCaptor.capture());
		assertEquals("", requestCaptor.getValue().getEvidence());
	}

	@Test
	void nestedQueries_generatesSubquerySyntax() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		String sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders)";
		stubGeneratedSql(sql);

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);
		assertEquals(sql, execution.finalResult().get(SQL_GENERATE_OUTPUT));
	}

	@Test
	void specialCharacters_inColumns_escapesProperly() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		String sql = "SELECT `user-name`, `order#id` FROM `special_table`";
		stubGeneratedSql(sql);

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);
		assertEquals(sql, execution.finalResult().get(SQL_GENERATE_OUTPUT));
	}

	@Test
	void extremelyLongQuery_generatesValidSql() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		StringBuilder longSql = new StringBuilder("SELECT ");
		for (int i = 0; i < 100; i++) {
			if (i > 0)
				longSql.append(", ");
			longSql.append("col_").append(i);
		}
		longSql.append(" FROM large_table WHERE id > 0");

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		stubGeneratedSql(longSql.toString());

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);
		assertEquals(longSql.toString(), execution.finalResult().get(SQL_GENERATE_OUTPUT));
	}

	@Test
	void apply_sqlTrimRemovesMarkdown_returnsCleanSql() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		when(nl2SqlService.generateSql(any())).thenReturn(Flux.just("```sql\nSELECT * FROM users\n```"));
		when(nl2SqlService.sqlTrim(any())).thenReturn("SELECT * FROM users");

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);
		assertEquals("SELECT * FROM users", execution.finalResult().get(SQL_GENERATE_OUTPUT));
		assertTrue(execution.streamedText().contains("SELECT * FROM users"));
	}

	@Test
	void apply_executionRetryGeneratesDifferentSql_storesNewSqlInOutput() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(SQL_REGENERATE_REASON, SqlRetryDto.sqlExecute("table not found"), SQL_GENERATE_OUTPUT,
				"SELECT * FROM nonexistent_table"));

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		stubGeneratedSql("SELECT * FROM existing_table");

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);
		assertEquals("SELECT * FROM existing_table", execution.finalResult().get(SQL_GENERATE_OUTPUT));
	}

	@Test
	void apply_laterStepWithOversizedPreviousResults_truncatesHistory() throws Exception {
		OverAllState state = createTestState();
		String oversizedResult = "x".repeat(20_000);
		state.updateState(Map.of(SQL_GENERATE_COUNT, 0, PLANNER_NODE_OUTPUT, MULTI_STEP_PLAN_JSON, PLAN_CURRENT_STEP, 2,
				EVIDENCE, "test evidence", DB_DIALECT_TYPE, "mysql", QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE,
				TABLE_RELATION_OUTPUT, TEST_SCHEMA, SQL_EXECUTE_NODE_OUTPUT, Map.of("step_1", oversizedResult)));
		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		when(nl2SqlService.generateSql(any())).thenReturn(Flux.just("SELECT * FROM orders"));

		sqlGenerateNode.apply(state);

		ArgumentCaptor<SqlGenerationDTO> requestCaptor = ArgumentCaptor.forClass(SqlGenerationDTO.class);
		verify(nl2SqlService).generateSql(requestCaptor.capture());
		String previousResults = requestCaptor.getValue().getPreviousStepResults();
		assertTrue(previousResults.contains("step_1"));
		assertTrue(previousResults.contains("...(历史结果已截断)"));
		assertTrue(previousResults.length() < 20_000);
	}

	@Test
	void apply_laterStepWithBlankPreviousResults_skipsBlankStep() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(SQL_GENERATE_COUNT, 0, PLANNER_NODE_OUTPUT, MULTI_STEP_PLAN_JSON, PLAN_CURRENT_STEP, 2,
				EVIDENCE, "test evidence", DB_DIALECT_TYPE, "mysql", QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE,
				TABLE_RELATION_OUTPUT, TEST_SCHEMA, SQL_EXECUTE_NODE_OUTPUT, Map.of("step_1", "   ")));
		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		when(nl2SqlService.generateSql(any())).thenReturn(Flux.just("SELECT * FROM orders"));

		sqlGenerateNode.apply(state);

		ArgumentCaptor<SqlGenerationDTO> requestCaptor = ArgumentCaptor.forClass(SqlGenerationDTO.class);
		verify(nl2SqlService).generateSql(requestCaptor.capture());
		assertEquals("无", requestCaptor.getValue().getPreviousStepResults());
	}

	@Test
	void apply_executionRetrySameSqlWithMultipleTrailingSemicolons_stopsRetryLoop() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(SQL_REGENERATE_REASON, SqlRetryDto.sqlExecute("same database error"),
				SQL_GENERATE_OUTPUT, "SELECT * FROM users;;;"));

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		stubGeneratedSql("SELECT * FROM users");

		NodeExecution execution = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);
		assertEquals(StateGraph.END, execution.finalResult().get(SQL_GENERATE_OUTPUT));
	}

}
