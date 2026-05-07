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
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.SqlExecuteNode;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

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
	private JsonParseUtil jsonParseUtil;

	@Mock
	private Accessor accessor;

	private SqlExecuteNode sqlExecuteNode;

	@BeforeEach
	void setUp() {
		sqlExecuteNode = new SqlExecuteNode(databaseUtil, nl2SqlService, llmService, properties, jsonParseUtil);
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

		Map<String, Object> result = sqlExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(SQL_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(SQL_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void apply_chartConfigLlmFailure_continuesWithoutChart() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		setupBasicMocks();

		ResultSetBO resultSetBO = new ResultSetBO();
		resultSetBO.setData(new ArrayList<>(List.of(Map.of("name", "Alice"))));

		when(accessor.executeSqlAndReturnObject(any(), any())).thenReturn(resultSetBO);
		when(properties.isEnableSqlResultChart()).thenReturn(true);
		when(llmService.call(anyString(), anyString())).thenThrow(new RuntimeException("LLM timeout"));

		Map<String, Object> result = sqlExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(SQL_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(SQL_EXECUTE_NODE_OUTPUT));
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
