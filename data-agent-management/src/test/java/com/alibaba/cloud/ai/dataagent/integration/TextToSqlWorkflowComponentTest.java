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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeExecution;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.SqlExecuteNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.SqlGenerateNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.DATASOURCE_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.DB_DIALECT_TYPE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.EVIDENCE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PLAN_CURRENT_STEP;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PLANNER_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.QUERY_ENHANCE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_EXECUTE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_GENERATE_COUNT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_GENERATE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_REGENERATE_REASON;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_RESULT_LIST_MEMORY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_RELATION_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.execute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextToSqlWorkflowComponentTest {

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
	private Accessor accessor;

	private SqlGenerateNode sqlGenerateNode;

	private SqlExecuteNode sqlExecuteNode;

	@BeforeEach
	void setUp() {
		sqlGenerateNode = new SqlGenerateNode(nl2SqlService, properties);
		sqlExecuteNode = new SqlExecuteNode(databaseUtil, nl2SqlService, llmService, properties);
	}

	@Test
	void generatedSqlFlowsIntoExecutionAndPersistsTheExactStepResult() throws Exception {
		OverAllState state = workflowState();
		NodeExecution generation = generateSql(state, "SELECT id, name, email FROM users");

		assertThat(generation.finalResult()).containsEntry(SQL_GENERATE_OUTPUT, "SELECT id, name, email FROM users")
			.containsEntry(SQL_GENERATE_COUNT, 1)
			.containsEntry(SQL_REGENERATE_REASON, SqlRetryDto.empty());
		state.updateState(generation.finalResult());

		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setSchema("test_db");
		when(databaseUtil.getDatasourceDbConfig(100)).thenReturn(dbConfig);
		when(databaseUtil.getAccessor(dbConfig)).thenReturn(accessor);
		List<Map<String, String>> rows = new ArrayList<>();
		rows.add(Map.of("id", "1", "name", "Alice", "email", "alice@test.com"));
		rows.add(Map.of("id", "2", "name", "Bob", "email", "bob@test.com"));
		ResultSetBO resultSet = new ResultSetBO();
		resultSet.setData(rows);
		resultSet.setColumn(List.of("id", "name", "email"));
		when(accessor.executeSqlAndReturnObject(any(DbConfigBO.class), any(DbQueryParameter.class)))
			.thenReturn(resultSet);

		NodeExecution execution = execute(sqlExecuteNode.apply(state), SQL_EXECUTE_NODE_OUTPUT);

		assertThat(execution.finalResult()).containsEntry(SQL_REGENERATE_REASON, SqlRetryDto.empty())
			.containsEntry(SQL_RESULT_LIST_MEMORY, rows)
			.containsEntry(PLAN_CURRENT_STEP, 2)
			.containsEntry(SQL_GENERATE_COUNT, 0);
		@SuppressWarnings("unchecked")
		Map<String, String> storedResults = (Map<String, String>) execution.finalResult().get(SQL_EXECUTE_NODE_OUTPUT);
		ResultSetBO storedResult = JsonUtil.getObjectMapper().readValue(storedResults.get("step_1"), ResultSetBO.class);
		assertThat(storedResult.getData()).containsExactlyElementsOf(rows);
		assertThat(storedResult.getColumn()).containsExactly("id", "name", "email");

		ArgumentCaptor<DbQueryParameter> query = ArgumentCaptor.forClass(DbQueryParameter.class);
		verify(accessor).executeSqlAndReturnObject(any(DbConfigBO.class), query.capture());
		assertThat(query.getValue().getSql()).isEqualTo("SELECT id, name, email FROM users");
		assertThat(query.getValue().getSchema()).isEqualTo("test_db");
	}

	@Test
	void databaseFailureBecomesAConcreteRetryReasonWithoutAdvancingThePlan() throws Exception {
		OverAllState state = workflowState();
		NodeExecution generation = generateSql(state, "SELECT * FROM nonexistent_table");
		state.updateState(generation.finalResult());

		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setSchema("test_db");
		when(databaseUtil.getDatasourceDbConfig(100)).thenReturn(dbConfig);
		when(databaseUtil.getAccessor(dbConfig)).thenReturn(accessor);
		String databaseError = "Table 'nonexistent_table' doesn't exist";
		when(accessor.executeSqlAndReturnObject(any(DbConfigBO.class), any(DbQueryParameter.class)))
			.thenThrow(new RuntimeException(databaseError));

		NodeExecution execution = execute(sqlExecuteNode.apply(state), SQL_EXECUTE_NODE_OUTPUT);

		assertThat(execution.finalResult())
			.containsOnly(Map.entry(SQL_REGENERATE_REASON, SqlRetryDto.sqlExecute(databaseError)));
		assertThat(execution.streamedText()).contains("SQL执行失败", databaseError);
	}

	private NodeExecution generateSql(OverAllState state, String sql) throws Exception {
		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		when(nl2SqlService.generateSql(any(SqlGenerationDTO.class))).thenReturn(Flux.just(sql));
		when(nl2SqlService.sqlTrim(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

		NodeExecution generation = execute(sqlGenerateNode.apply(state), SQL_GENERATE_OUTPUT);

		ArgumentCaptor<SqlGenerationDTO> request = ArgumentCaptor.forClass(SqlGenerationDTO.class);
		verify(nl2SqlService).generateSql(request.capture());
		assertThat(request.getValue().getDialect()).isEqualTo("mysql");
		assertThat(request.getValue().getQuery()).isEqualTo("查询所有用户");
		assertThat(request.getValue().getExecutionDescription()).isEqualTo("Query all users");
		assertThat(request.getValue().getSchemaDTO().getName()).isEqualTo("test_db");
		return generation;
	}

	private OverAllState workflowState() {
		OverAllState state = new OverAllState();
		String[] keys = { SQL_GENERATE_OUTPUT, SQL_GENERATE_COUNT, SQL_REGENERATE_REASON, PLANNER_NODE_OUTPUT,
				PLAN_CURRENT_STEP, EVIDENCE, TABLE_RELATION_OUTPUT, DB_DIALECT_TYPE, QUERY_ENHANCE_NODE_OUTPUT,
				AGENT_ID, DATASOURCE_ID, SQL_EXECUTE_NODE_OUTPUT, SQL_RESULT_LIST_MEMORY };
		for (String key : keys) {
			state.registerKeyAndStrategy(key, new ReplaceStrategy());
		}
		Map<String, Object> schema = TestFixtures.createSchemaMap("test_db", "users", "orders");
		Map<String, Object> queryEnhance = TestFixtures.createQueryEnhanceMap("查询所有用户");
		state.updateState(Map.of(SQL_GENERATE_COUNT, 0, PLANNER_NODE_OUTPUT, TEST_PLAN_JSON, PLAN_CURRENT_STEP, 1,
				EVIDENCE, "用户表包含id, name, email", DB_DIALECT_TYPE, "mysql", QUERY_ENHANCE_NODE_OUTPUT, queryEnhance,
				TABLE_RELATION_OUTPUT, schema, AGENT_ID, "1", DATASOURCE_ID, 100));
		return state;
	}

}
