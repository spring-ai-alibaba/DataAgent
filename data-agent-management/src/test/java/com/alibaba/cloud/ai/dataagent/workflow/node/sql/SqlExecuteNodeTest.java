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

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.execute;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatResponse;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DisplayStyleBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeExecution;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.SqlExecuteNode;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@ExtendWith(MockitoExtension.class)
class SqlExecuteNodeTest {

	private static final String TEST_PLAN_JSON = """
			{
			    "thought_process": "根据问题生成SQL",
			    "execution_plan": [
			        {
			            "step": 1,
			            "tool_to_use": "sql_execute_node",
			            "tool_parameters": {
			                "instruction": "SQL执行"
			            }
			        }
			    ]
			}
			""";

	private static final Map<String, Object> TEST_QUERY_ENHANCE;

	static {
		Map<String, Object> queryEnhance = new HashMap<>();
		queryEnhance.put("canonical_query", "查询所有用户信息");
		queryEnhance.put("expanded_queries", new ArrayList<>(List.of("查询用户")));
		TEST_QUERY_ENHANCE = queryEnhance;
	}

	@Mock
	private DatabaseUtil databaseUtil;

	@Mock
	private Nl2SqlService nl2SqlService;

	@Mock
	private LlmService llmService;

	@Mock
	private DataAgentProperties properties;

	@Mock
	private Accessor accessor;

	private SqlExecuteNode sqlExecuteNode;

	@BeforeEach
	void setUp() {
		sqlExecuteNode = new SqlExecuteNode(databaseUtil, nl2SqlService, llmService, properties);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(SQL_GENERATE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(AGENT_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(DATASOURCE_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(SCHEMA_FINGERPRINT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLANNER_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_CURRENT_STEP, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_REGENERATE_REASON, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_RESULT_LIST_MEMORY, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_GENERATE_COUNT, new ReplaceStrategy());
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(IS_ONLY_NL2SQL, new ReplaceStrategy());
		return state;
	}

	private void setupBasicState(OverAllState state) {
		state.updateState(Map.of(SQL_GENERATE_OUTPUT, "SELECT * FROM users", AGENT_ID, "1", PLANNER_NODE_OUTPUT,
				TEST_PLAN_JSON, PLAN_CURRENT_STEP, 1, QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE, DATASOURCE_ID, 100,
				SCHEMA_FINGERPRINT, "schema-v1"));
	}

	private void setupBasicMocks() {
		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setSchema("test_schema");
		when(nl2SqlService.sqlTrim(any())).thenAnswer(inv -> inv.getArgument(0));
		when(databaseUtil.getDatasourceDbConfig(100, "schema-v1")).thenReturn(dbConfig);
		when(databaseUtil.getAccessor(dbConfig)).thenReturn(accessor);
	}

	private ResultBO extractResultSetPayload(String streamedText) throws Exception {
		return extractResultSetPayloads(streamedText).get(0);
	}

	private List<ResultBO> extractResultSetPayloads(String streamedText) throws Exception {
		String startSign = TextType.RESULT_SET.getStartSign();
		String endSign = TextType.RESULT_SET.getEndSign();
		List<ResultBO> payloads = new ArrayList<>();
		int offset = 0;
		while (true) {
			int start = streamedText.indexOf(startSign, offset);
			if (start < 0) {
				break;
			}
			start += startSign.length();
			int end = streamedText.indexOf(endSign, start);
			assertTrue(end > start, "RESULT_SET end marker must be emitted");
			payloads.add(JsonUtil.getObjectMapper().readValue(streamedText.substring(start, end), ResultBO.class));
			offset = end + endSign.length();
		}
		assertFalse(payloads.isEmpty(), "RESULT_SET start marker must be emitted");
		return payloads;
	}

	private String streamedText(List<GraphResponse<StreamingOutput>> responses) {
		return responses.stream()
			.filter(response -> !response.isDone() && !response.isError())
			.map(response -> response.getOutput().join().chunk())
			.collect(Collectors.joining());
	}

	@Test
	void validSelectQuery_executesSuccessfully_returnsResults() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setColumn(List.of("id", "name"));
		resultSetBO.setData(List.of(Map.of("id", "1", "name", "Alice")));
		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);

		NodeExecution execution = execute(sqlExecuteNode.apply(state), SQL_EXECUTE_NODE_OUTPUT);
		ResultBO payload = extractResultSetPayload(execution.streamedText());

		assertEquals(resultSetBO, payload.getResultSet());
		assertEquals("table", payload.getDisplayStyle().getType());
		assertEquals(resultSetBO.getData(), execution.finalResult().get(SQL_RESULT_LIST_MEMORY));
		assertEquals(SqlRetryDto.empty(), execution.finalResult().get(SQL_REGENERATE_REASON));
		assertEquals(2, execution.finalResult().get(PLAN_CURRENT_STEP));
		ArgumentCaptor<DbQueryParameter> query = ArgumentCaptor.forClass(DbQueryParameter.class);
		verify(accessor).executeSqlAndReturnObject(any(DbConfigBO.class), query.capture());
		assertEquals("SELECT * FROM users", query.getValue().getSql());
		assertEquals("test_schema", query.getValue().getSchema());
	}

	@Test
	void queryWithMultipleColumns_executesSuccessfully_returnsAllColumns() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(SQL_GENERATE_OUTPUT, "SELECT id, name, age FROM users", AGENT_ID, "1",
				PLANNER_NODE_OUTPUT, TEST_PLAN_JSON, PLAN_CURRENT_STEP, 1, QUERY_ENHANCE_NODE_OUTPUT,
				TEST_QUERY_ENHANCE, DATASOURCE_ID, 100, SCHEMA_FINGERPRINT, "schema-v1"));

		setupBasicMocks();

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setColumn(List.of("id", "name", "age"));
		resultSetBO.setData(List.of(Map.of("id", "7", "name", "Bob", "age", "42")));
		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);

		NodeExecution execution = execute(sqlExecuteNode.apply(state), SQL_EXECUTE_NODE_OUTPUT);
		ResultSetBO actual = extractResultSetPayload(execution.streamedText()).getResultSet();

		assertEquals(List.of("id", "name", "age"), actual.getColumn());
		assertEquals(resultSetBO.getData(), actual.getData());
	}

	@Test
	void apply_sqlExecutionError_setsRetryReason() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();

		when(accessor.executeSqlAndReturnObject(any(), any()))
			.thenThrow(new RuntimeException("Table 'users' doesn't exist"));

		NodeExecution execution = execute(sqlExecuteNode.apply(state), SQL_EXECUTE_NODE_OUTPUT);

		assertEquals(SqlRetryDto.sqlExecute("Table 'users' doesn't exist"),
				execution.finalResult().get(SQL_REGENERATE_REASON));
		assertTrue(execution.streamedText().contains("SQL执行失败: Table 'users' doesn't exist"));
		assertFalse(execution.finalResult().containsKey(PLAN_CURRENT_STEP));
	}

	@Test
	void apply_connectionFailure_throwsException() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(nl2SqlService.sqlTrim(any())).thenAnswer(inv -> inv.getArgument(0));
		when(databaseUtil.getDatasourceDbConfig(100, "schema-v1"))
			.thenThrow(new RuntimeException("Connection refused"));

		assertThrowsExactly(RuntimeException.class, () -> sqlExecuteNode.apply(state));
	}

	@Test
	void apply_missingAgentId_throwsException() {
		OverAllState state = createTestState();
		state.updateState(Map.of(SQL_GENERATE_OUTPUT, "SELECT * FROM users", PLANNER_NODE_OUTPUT, TEST_PLAN_JSON,
				PLAN_CURRENT_STEP, 1));

		when(nl2SqlService.sqlTrim(any())).thenAnswer(inv -> inv.getArgument(0));

		IllegalStateException exception = assertThrowsExactly(IllegalStateException.class,
				() -> sqlExecuteNode.apply(state));
		assertEquals("State key not found: agentId", exception.getMessage());
	}

	@Test
	void apply_blankAgentId_throwsExplicitException() {
		OverAllState state = createTestState();
		state.updateState(Map.of(SQL_GENERATE_OUTPUT, "SELECT * FROM users", PLANNER_NODE_OUTPUT, TEST_PLAN_JSON,
				PLAN_CURRENT_STEP, 1, AGENT_ID, ""));

		when(nl2SqlService.sqlTrim(any())).thenAnswer(inv -> inv.getArgument(0));

		IllegalStateException exception = assertThrowsExactly(IllegalStateException.class,
				() -> sqlExecuteNode.apply(state));
		assertEquals("Agent ID cannot be empty.", exception.getMessage());
	}

	@Test
	void apply_emptyResultSet_returnsEmptyResults() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setData(new ArrayList<>());

		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);

		NodeExecution execution = execute(sqlExecuteNode.apply(state), SQL_EXECUTE_NODE_OUTPUT);
		ResultBO payload = extractResultSetPayload(execution.streamedText());

		assertEquals(resultSetBO, payload.getResultSet());
		assertEquals(List.of(), execution.finalResult().get(SQL_RESULT_LIST_MEMORY));
		assertEquals(SqlRetryDto.empty(), execution.finalResult().get(SQL_REGENERATE_REASON));
	}

	@Test
	void apply_multipleSqlSteps_keepsCurrentMemoryAndStepHistorySeparate() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();
		List<Map<String, String>> previousRows = List.of(Map.of("department", "sales"));
		String twoStepPlan = """
				{
				  "thought_process": "查询两个部门",
				  "execution_plan": [
				    {
				      "step": 1,
				      "tool_to_use": "sql_execute_node",
				      "tool_parameters": {"instruction": "查询销售部门"}
				    },
				    {
				      "step": 2,
				      "tool_to_use": "sql_execute_node",
				      "tool_parameters": {"instruction": "查询工程部门"}
				    }
				  ]
				}
				""";
		state.updateState(Map.of(SQL_RESULT_LIST_MEMORY, previousRows, SQL_EXECUTE_NODE_OUTPUT,
				Map.of("step_1", "{\"data\":[{\"department\":\"sales\"}]}"), PLAN_CURRENT_STEP, 2, PLANNER_NODE_OUTPUT,
				twoStepPlan));

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setData(List.of(Map.of("department", "engineering")));
		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);

		NodeExecution execution = execute(sqlExecuteNode.apply(state), SQL_EXECUTE_NODE_OUTPUT);

		assertEquals(List.of(Map.of("department", "engineering")), execution.finalResult().get(SQL_RESULT_LIST_MEMORY));
		Map<String, String> stepResults = (Map<String, String>) execution.finalResult().get(SQL_EXECUTE_NODE_OUTPUT);
		assertEquals(2, stepResults.size());
		assertTrue(stepResults.get("step_1").contains("sales"));
		assertTrue(stepResults.get("step_2").contains("engineering"));
	}

	@Test
	void apply_nullResultSet_handlesGracefully() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setData(null);

		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);

		NodeExecution execution = execute(sqlExecuteNode.apply(state), SQL_EXECUTE_NODE_OUTPUT);
		ResultBO payload = extractResultSetPayload(execution.streamedText());

		assertNull(payload.getResultSet().getData());
		assertTrue(execution.finalResult().containsKey(SQL_RESULT_LIST_MEMORY));
		assertNull(execution.finalResult().get(SQL_RESULT_LIST_MEMORY));
		assertEquals(SqlRetryDto.empty(), execution.finalResult().get(SQL_REGENERATE_REASON));
	}

	@Test
	void apply_withChartConfigEnabled_generatesChartConfig() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setData(new ArrayList<>(List.of(Map.of("name", "Alice", "age", "30"))));

		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);
		when(properties.isEnableSqlResultChart()).thenReturn(true);
		when(properties.getEnrichSqlResultTimeout()).thenReturn(1000L);
		when(llmService.call(anyString(), anyString(), eq(DisplayStyleBO.class)))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("{\"type\":\"bar\"}")));
		when(llmService.toStringFlux(any())).thenCallRealMethod();

		NodeExecution execution = execute(sqlExecuteNode.apply(state), SQL_EXECUTE_NODE_OUTPUT);
		List<ResultBO> payloads = extractResultSetPayloads(execution.streamedText());

		assertEquals(resultSetBO, payloads.get(0).getResultSet());
		assertEquals("table", payloads.get(0).getDisplayStyle().getType());
		assertEquals("bar", payloads.get(payloads.size() - 1).getDisplayStyle().getType());
		assertEquals(SqlRetryDto.empty(), execution.finalResult().get(SQL_REGENERATE_REASON));
	}

	@Test
	void apply_chartConfigTimeout_fallsBackToTableAndKeepsQueryResult() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setData(new ArrayList<>(List.of(Map.of("name", "Alice"))));

		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);
		when(properties.isEnableSqlResultChart()).thenReturn(true);
		when(properties.getEnrichSqlResultTimeout()).thenReturn(1L);
		when(llmService.call(anyString(), anyString(), eq(DisplayStyleBO.class))).thenReturn(Flux.never());
		when(llmService.toStringFlux(any())).thenCallRealMethod();

		NodeExecution execution = execute(sqlExecuteNode.apply(state), SQL_EXECUTE_NODE_OUTPUT);
		ResultBO payload = extractResultSetPayload(execution.streamedText());

		assertEquals(resultSetBO, payload.getResultSet());
		assertEquals("table", payload.getDisplayStyle().getType());
		assertEquals(SqlRetryDto.empty(), execution.finalResult().get(SQL_REGENERATE_REASON));
		assertFalse(execution.streamedText().contains("SQL执行失败"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void apply_slowChartGeneration_emitsTableResultBeforeChartCompletes() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setData(new ArrayList<>(List.of(Map.of("name", "Alice"))));
		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);
		when(properties.isEnableSqlResultChart()).thenReturn(true);
		when(properties.getEnrichSqlResultTimeout()).thenReturn(5000L);
		CountDownLatch chartSubscribed = new CountDownLatch(1);
		CountDownLatch completed = new CountDownLatch(1);
		Sinks.One<ChatResponse> chartResponse = Sinks.one();
		when(llmService.call(anyString(), anyString(), eq(DisplayStyleBO.class))).thenReturn(Flux.defer(() -> {
			chartSubscribed.countDown();
			return chartResponse.asMono().flux();
		}));
		when(llmService.toStringFlux(any())).thenCallRealMethod();

		Map<String, Object> result = sqlExecuteNode.apply(state);
		Flux<GraphResponse<StreamingOutput>> generator = (Flux<GraphResponse<StreamingOutput>>) result
			.get(SQL_EXECUTE_NODE_OUTPUT);
		List<GraphResponse<StreamingOutput>> responses = new CopyOnWriteArrayList<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		generator.subscribe(responses::add, failure::set, completed::countDown);

		assertTrue(chartSubscribed.await(1, TimeUnit.SECONDS), "chart generation was not subscribed");
		List<ResultBO> pendingPayloads = extractResultSetPayloads(streamedText(responses));
		assertEquals(1, pendingPayloads.size());
		assertEquals(resultSetBO, pendingPayloads.get(0).getResultSet());
		assertEquals("table", pendingPayloads.get(0).getDisplayStyle().getType());
		assertEquals(1L, completed.getCount(), "stream must still wait for the chart response");

		chartResponse.tryEmitValue(ChatResponseUtil.createPureResponse("{\"type\":\"bar\"}"));
		assertTrue(completed.await(1, TimeUnit.SECONDS), "stream did not complete after chart response");
		assertNull(failure.get());
		List<ResultBO> completedPayloads = extractResultSetPayloads(streamedText(responses));
		assertEquals(List.of("table", "bar"),
				completedPayloads.stream().map(payload -> payload.getDisplayStyle().getType()).toList());
	}

	@Test
	void apply_nl2sqlOnly_skipsChartLlmAndKeepsQueryResult() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(IS_ONLY_NL2SQL, true));
		setupBasicMocks();

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setColumn(List.of("name"));
		resultSetBO.setData(new ArrayList<>(List.of(Map.of("name", "Alice"))));

		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);

		NodeExecution execution = execute(sqlExecuteNode.apply(state), SQL_EXECUTE_NODE_OUTPUT);
		ResultBO payload = extractResultSetPayload(execution.streamedText());

		assertEquals(resultSetBO, payload.getResultSet());
		assertEquals("table", payload.getDisplayStyle().getType());
		assertEquals(SqlRetryDto.empty(), execution.finalResult().get(SQL_REGENERATE_REASON));
		verifyNoInteractions(llmService);
	}

	@Test
	void apply_nullValuesInColumns_handlesCorrectly() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();

		List<Map<String, String>> dataWithNulls = new ArrayList<>();
		Map<String, String> row = new HashMap<>();
		row.put("id", "1");
		row.put("name", null);
		row.put("email", "test@example.com");
		dataWithNulls.add(row);

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setData(dataWithNulls);

		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);

		NodeExecution execution = execute(sqlExecuteNode.apply(state), SQL_EXECUTE_NODE_OUTPUT);
		ResultSetBO actual = extractResultSetPayload(execution.streamedText()).getResultSet();

		assertEquals(dataWithNulls, actual.getData());
		assertNull(actual.getData().get(0).get("name"));
		assertEquals(dataWithNulls, execution.finalResult().get(SQL_RESULT_LIST_MEMORY));
	}

	@Test
	void apply_largeResultSet_preservesAllRows() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();

		List<Map<String, String>> largeData = new ArrayList<>();
		for (int i = 0; i < 1000; i++) {
			largeData.add(Map.of("id", String.valueOf(i), "name", "user_" + i));
		}

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setData(largeData);

		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);

		NodeExecution execution = execute(sqlExecuteNode.apply(state), SQL_EXECUTE_NODE_OUTPUT);
		ResultSetBO actual = extractResultSetPayload(execution.streamedText()).getResultSet();

		assertEquals(1000, actual.getData().size());
		assertEquals(Map.of("id", "0", "name", "user_0"), actual.getData().get(0));
		assertEquals(Map.of("id", "999", "name", "user_999"), actual.getData().get(999));
		assertEquals(largeData, execution.finalResult().get(SQL_RESULT_LIST_MEMORY));
	}

}
