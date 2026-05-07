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
package com.alibaba.cloud.ai.dataagent.integration;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
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
import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.SqlExecuteNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.SqlGenerateNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TextToSqlWorkflowIntegrationTest {

	private static final String TEST_PLAN_JSON = TestFixtures.createSingleSqlPlanJson();

	@Mock
	private Nl2SqlService nl2SqlService;

	@Mock
	private DataAgentProperties properties;

	@Mock
	private DatabaseUtil databaseUtil;

	@Mock
	private LlmService llmService;

	@Mock
	private JsonParseUtil jsonParseUtil;

	@Mock
	private Accessor accessor;

	private SqlGenerateNode sqlGenerateNode;

	private SqlExecuteNode sqlExecuteNode;

	@BeforeEach
	void setUp() {
		sqlGenerateNode = new SqlGenerateNode(nl2SqlService, properties);
		sqlExecuteNode = new SqlExecuteNode(databaseUtil, nl2SqlService, llmService, properties, jsonParseUtil);

		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		when(properties.isEnableSqlResultChart()).thenReturn(false);
	}

	private OverAllState createWorkflowState() {
		OverAllState state = new OverAllState();
		String[] keys = { SQL_GENERATE_OUTPUT, SQL_GENERATE_COUNT, SQL_REGENERATE_REASON, PLANNER_NODE_OUTPUT,
				PLAN_CURRENT_STEP, EVIDENCE, TABLE_RELATION_OUTPUT, DB_DIALECT_TYPE, QUERY_ENHANCE_NODE_OUTPUT,
				AGENT_ID, SQL_EXECUTE_NODE_OUTPUT, SQL_RESULT_LIST_MEMORY, TRACE_THREAD_ID };
		for (String key : keys) {
			state.registerKeyAndStrategy(key, new ReplaceStrategy());
		}
		return state;
	}

	private void setupWorkflowState(OverAllState state) {
		Map<String, Object> schema = TestFixtures.createSchemaMap("test_db", "users", "orders");
		Map<String, Object> queryEnhance = TestFixtures.createQueryEnhanceMap("查询所有用户");

		state.updateState(Map.of(SQL_GENERATE_COUNT, 0, PLANNER_NODE_OUTPUT, TEST_PLAN_JSON, PLAN_CURRENT_STEP, 1,
				EVIDENCE, "用户表包含id, name, email", DB_DIALECT_TYPE, "mysql", QUERY_ENHANCE_NODE_OUTPUT, queryEnhance,
				TABLE_RELATION_OUTPUT, schema, AGENT_ID, "1"));
	}

	@Test
	void endToEnd_sqlQuery_generatesExecutesValidates_returnsResults() throws Exception {
		OverAllState state = createWorkflowState();
		setupWorkflowState(state);

		when(nl2SqlService.generateSql(any())).thenReturn(Flux.just("SELECT * FROM users"));
		when(nl2SqlService.sqlTrim(anyString())).thenAnswer(inv -> inv.getArgument(0));

		Map<String, Object> generateResult = sqlGenerateNode.apply(state);
		assertNotNull(generateResult);
		assertTrue(generateResult.containsKey(SQL_GENERATE_OUTPUT));

		state.updateState(Map.of(SQL_GENERATE_OUTPUT, "SELECT * FROM users"));

		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setSchema("test_db");
		when(databaseUtil.getAgentDbConfig(1L)).thenReturn(dbConfig);
		when(databaseUtil.getAgentAccessor(1L)).thenReturn(accessor);

		ResultSetBO resultSetBO = new ResultSetBO();
		List<Map<String, String>> data = new ArrayList<>();
		data.add(Map.of("id", "1", "name", "Alice", "email", "alice@test.com"));
		data.add(Map.of("id", "2", "name", "Bob", "email", "bob@test.com"));
		resultSetBO.setData(data);
		resultSetBO.setColumn(List.of("id", "name", "email"));

		when(accessor.executeSqlAndReturnObject(any(DbConfigBO.class), any())).thenReturn(resultSetBO);

		Map<String, Object> executeResult = sqlExecuteNode.apply(state);
		assertNotNull(executeResult);
		assertTrue(executeResult.containsKey(SQL_EXECUTE_NODE_OUTPUT));

		verify(nl2SqlService).generateSql(any());
	}

	@Test
	void errorInStep_returnsPartialResults_withErrorDetails() throws Exception {
		OverAllState state = createWorkflowState();
		setupWorkflowState(state);

		when(nl2SqlService.generateSql(any())).thenReturn(Flux.just("SELECT * FROM nonexistent_table"));
		when(nl2SqlService.sqlTrim(anyString())).thenAnswer(inv -> inv.getArgument(0));

		Map<String, Object> generateResult = sqlGenerateNode.apply(state);
		assertNotNull(generateResult);
		assertTrue(generateResult.containsKey(SQL_GENERATE_OUTPUT));

		state.updateState(Map.of(SQL_GENERATE_OUTPUT, "SELECT * FROM nonexistent_table"));

		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setSchema("test_db");
		when(databaseUtil.getAgentDbConfig(1L)).thenReturn(dbConfig);
		when(databaseUtil.getAgentAccessor(1L)).thenReturn(accessor);
		when(accessor.executeSqlAndReturnObject(any(DbConfigBO.class), any()))
			.thenThrow(new RuntimeException("Table 'nonexistent_table' doesn't exist"));

		Map<String, Object> executeResult = sqlExecuteNode.apply(state);
		assertNotNull(executeResult);
		assertTrue(executeResult.containsKey(SQL_EXECUTE_NODE_OUTPUT));
	}

}
