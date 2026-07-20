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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DisplayStyleBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
				TEST_PLAN_JSON, PLAN_CURRENT_STEP, 1, QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE));
	}

	private void setupBasicMocks() {
		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setSchema("test_schema");
		when(nl2SqlService.sqlTrim(any())).thenAnswer(inv -> inv.getArgument(0));
		when(databaseUtil.getAgentDbConfig(1L)).thenReturn(dbConfig);
		when(databaseUtil.getAgentAccessor(1L)).thenReturn(accessor);
	}

	@SuppressWarnings("unchecked")
	private SqlExecution subscribeToExecution(OverAllState state) throws Exception {
		Map<String, Object> result = sqlExecuteNode.apply(state);
		Flux<GraphResponse<StreamingOutput>> generator = (Flux<GraphResponse<StreamingOutput>>) result
			.get(SQL_EXECUTE_NODE_OUTPUT);
		List<GraphResponse<StreamingOutput>> responses = generator.collectList().block(Duration.ofSeconds(2));
		assertNotNull(responses);

		String streamedText = responses.stream()
			.filter(response -> !response.isDone() && !response.isError())
			.map(response -> response.getOutput().join().chunk())
			.collect(Collectors.joining());
		Map<String, Object> finalResult = responses.stream()
			.filter(GraphResponse::isDone)
			.findFirst()
			.flatMap(GraphResponse::resultValue)
			.map(value -> (Map<String, Object>) value)
			.orElseThrow();
		return new SqlExecution(streamedText, finalResult);
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

	private record SqlExecution(String streamedText, Map<String, Object> finalResult) {
	}

	@Test
	void validSelectQuery_executesSuccessfully_returnsResults() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setSchema("test_schema");

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setData(new ArrayList<>());

		when(nl2SqlService.sqlTrim(any())).thenReturn("SELECT * FROM users");
		when(databaseUtil.getAgentDbConfig(1L)).thenReturn(dbConfig);
		when(databaseUtil.getAgentAccessor(1L)).thenReturn(accessor);
		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);

		Map<String, Object> result = sqlExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(SQL_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void queryWithMultipleColumns_executesSuccessfully_returnsAllColumns() throws Exception {
		OverAllState state = createTestState();
		state.updateState(
				Map.of(SQL_GENERATE_OUTPUT, "SELECT id, name, age FROM users", AGENT_ID, "1", PLANNER_NODE_OUTPUT,
						TEST_PLAN_JSON, PLAN_CURRENT_STEP, 1, QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE));

		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setSchema("test_schema");

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setData(new ArrayList<>());

		when(nl2SqlService.sqlTrim(any())).thenReturn("SELECT id, name, age FROM users");
		when(databaseUtil.getAgentDbConfig(1L)).thenReturn(dbConfig);
		when(databaseUtil.getAgentAccessor(1L)).thenReturn(accessor);
		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);

		Map<String, Object> result = sqlExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(SQL_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void apply_sqlExecutionError_setsRetryReason() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();

		when(accessor.executeSqlAndReturnObject(any(), any()))
			.thenThrow(new RuntimeException("Table 'users' doesn't exist"));

		Map<String, Object> result = sqlExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(SQL_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(SQL_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void apply_connectionFailure_throwsException() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(nl2SqlService.sqlTrim(any())).thenAnswer(inv -> inv.getArgument(0));
		when(databaseUtil.getAgentDbConfig(1L)).thenThrow(new RuntimeException("Connection refused"));

		assertThrows(RuntimeException.class, () -> sqlExecuteNode.apply(state));
	}

	@Test
	void apply_missingAgentId_throwsException() {
		OverAllState state = createTestState();
		state.updateState(Map.of(SQL_GENERATE_OUTPUT, "SELECT * FROM users", PLANNER_NODE_OUTPUT, TEST_PLAN_JSON,
				PLAN_CURRENT_STEP, 1));

		when(nl2SqlService.sqlTrim(any())).thenAnswer(inv -> inv.getArgument(0));

		assertThrows(Exception.class, () -> sqlExecuteNode.apply(state));
	}

	@Test
	void apply_emptyResultSet_returnsEmptyResults() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setData(new ArrayList<>());

		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);

		Map<String, Object> result = sqlExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(SQL_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(SQL_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void apply_nullResultSet_handlesGracefully() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setData(null);

		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);

		Map<String, Object> result = sqlExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(SQL_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(SQL_EXECUTE_NODE_OUTPUT));
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

		SqlExecution execution = subscribeToExecution(state);
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

		SqlExecution execution = subscribeToExecution(state);
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
		when(llmService.call(anyString(), anyString(), eq(DisplayStyleBO.class))).thenReturn(Flux.never());
		when(llmService.toStringFlux(any())).thenCallRealMethod();

		Map<String, Object> result = sqlExecuteNode.apply(state);
		Flux<GraphResponse<StreamingOutput>> generator = (Flux<GraphResponse<StreamingOutput>>) result
			.get(SQL_EXECUTE_NODE_OUTPUT);
		GraphResponse<StreamingOutput> response = generator
			.filter(item -> !item.isDone() && !item.isError()
					&& item.getOutput().join().chunk().contains("\"displayStyle\""))
			.next()
			.block(Duration.ofSeconds(1));

		assertNotNull(response);
		ResultBO payload = JsonUtil.getObjectMapper().readValue(response.getOutput().join().chunk(), ResultBO.class);
		assertEquals(resultSetBO, payload.getResultSet());
		assertEquals("table", payload.getDisplayStyle().getType());
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
		when(properties.isEnableSqlResultChart()).thenReturn(true);

		SqlExecution execution = subscribeToExecution(state);
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

		Map<String, Object> result = sqlExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(SQL_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(SQL_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void apply_largeResultSet_truncatesAppropriately() throws Exception {
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

		Map<String, Object> result = sqlExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(SQL_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(SQL_EXECUTE_NODE_OUTPUT));
	}

}
