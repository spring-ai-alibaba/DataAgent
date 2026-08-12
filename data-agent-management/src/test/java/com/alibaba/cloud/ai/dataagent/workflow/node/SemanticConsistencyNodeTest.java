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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.execute;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SemanticConsistencyDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeExecution;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class SemanticConsistencyNodeTest {

	@Mock
	private Nl2SqlService nl2SqlService;

	private SemanticConsistencyNode semanticConsistencyNode;

	@BeforeEach
	void setUp() {
		semanticConsistencyNode = new SemanticConsistencyNode(nl2SqlService);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(EVIDENCE, new ReplaceStrategy());
		state.registerKeyAndStrategy(TABLE_RELATION_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(DB_DIALECT_TYPE, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_GENERATE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(SEMANTIC_CONSISTENCY_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_REGENERATE_REASON, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLANNER_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_CURRENT_STEP, new ReplaceStrategy());
		return state;
	}

	private SchemaDTO createSimpleSchema() {
		SchemaDTO schema = new SchemaDTO();
		schema.setName("test_db");
		schema.setTable(new ArrayList<>());
		schema.setForeignKeys(new ArrayList<>());
		return schema;
	}

	private void setupBasicState(OverAllState state, String sql) {
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询用户");
		String planJson = TestFixtures.createSingleSqlPlanJson();
		state.updateState(Map.of(EVIDENCE, "test evidence", TABLE_RELATION_OUTPUT, createSimpleSchema(),
				DB_DIALECT_TYPE, "mysql", SQL_GENERATE_OUTPUT, sql, QUERY_ENHANCE_NODE_OUTPUT, dto, PLANNER_NODE_OUTPUT,
				planJson, PLAN_CURRENT_STEP, 1));
	}

	@Test
	void apply_validSql_returnsGeneratorWithOutput() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state, "SELECT * FROM users");

		when(nl2SqlService.performSemanticConsistency(any(SemanticConsistencyDTO.class)))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("{\"passed\":true,\"reason\":\"SQL语义一致\"}")));

		NodeExecution execution = execute(semanticConsistencyNode.apply(state), SEMANTIC_CONSISTENCY_NODE_OUTPUT);
		ArgumentCaptor<SemanticConsistencyDTO> requestCaptor = ArgumentCaptor.forClass(SemanticConsistencyDTO.class);
		verify(nl2SqlService).performSemanticConsistency(requestCaptor.capture());

		assertEquals(true, execution.finalResult().get(SEMANTIC_CONSISTENCY_NODE_OUTPUT));
		assertFalse(execution.finalResult().containsKey(SQL_REGENERATE_REASON));
		assertEquals("mysql", requestCaptor.getValue().getDialect());
		assertEquals("SELECT * FROM users", requestCaptor.getValue().getSql());
		assertEquals("Query all users", requestCaptor.getValue().getExecutionDescription());
		assertEquals("查询用户", requestCaptor.getValue().getUserQuery());
		assertEquals("test evidence", requestCaptor.getValue().getEvidence());
		assertTrue(execution.streamedText().contains("开始语义一致性校验"));
		assertTrue(execution.streamedText().contains("语义一致性校验完成"));
	}

	@Test
	void apply_invalidSql_returnsGeneratorWithFailOutput() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state, "SELECT * FROM nonexistent");

		when(nl2SqlService.performSemanticConsistency(any(SemanticConsistencyDTO.class)))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("{\"passed\":false,\"reason\":\"表不存在\"}")));

		NodeExecution execution = execute(semanticConsistencyNode.apply(state), SEMANTIC_CONSISTENCY_NODE_OUTPUT);

		assertEquals(false, execution.finalResult().get(SEMANTIC_CONSISTENCY_NODE_OUTPUT));
		SqlRetryDto retry = (SqlRetryDto) execution.finalResult().get(SQL_REGENERATE_REASON);
		assertEquals(new SqlRetryDto("表不存在", true, false), retry);
	}

	@Test
	void apply_missingEvidence_throwsException() {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询用户");
		state.updateState(Map.of(TABLE_RELATION_OUTPUT, createSimpleSchema(), DB_DIALECT_TYPE, "mysql",
				SQL_GENERATE_OUTPUT, "SELECT 1", QUERY_ENHANCE_NODE_OUTPUT, dto));

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> semanticConsistencyNode.apply(state));
		assertTrue(exception.getMessage().contains(EVIDENCE));
	}

	@Test
	void apply_missingSql_throwsException() {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询用户");
		state.updateState(Map.of(EVIDENCE, "evidence", TABLE_RELATION_OUTPUT, createSimpleSchema(), DB_DIALECT_TYPE,
				"mysql", QUERY_ENHANCE_NODE_OUTPUT, dto));

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> semanticConsistencyNode.apply(state));
		assertTrue(exception.getMessage().contains(SQL_GENERATE_OUTPUT));
	}

	@Test
	void apply_multipleResponseChunks_returnsGenerator() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state, "SELECT id, name FROM users WHERE age > 18");

		when(nl2SqlService.performSemanticConsistency(any(SemanticConsistencyDTO.class)))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("{\"passed\":true,"),
					ChatResponseUtil.createPureResponse("\"reason\":\"SQL查询合理\"}")));

		NodeExecution execution = execute(semanticConsistencyNode.apply(state), SEMANTIC_CONSISTENCY_NODE_OUTPUT);

		assertEquals(true, execution.finalResult().get(SEMANTIC_CONSISTENCY_NODE_OUTPUT));
	}

	@Test
	void apply_withPostgresDialect_passesDialectToDto() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询用户");
		String planJson = TestFixtures.createSingleSqlPlanJson();
		state.updateState(Map.of(EVIDENCE, "evidence", TABLE_RELATION_OUTPUT, createSimpleSchema(), DB_DIALECT_TYPE,
				"postgresql", SQL_GENERATE_OUTPUT, "SELECT * FROM users", QUERY_ENHANCE_NODE_OUTPUT, dto,
				PLANNER_NODE_OUTPUT, planJson, PLAN_CURRENT_STEP, 1));

		when(nl2SqlService.performSemanticConsistency(any(SemanticConsistencyDTO.class)))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("{\"passed\":true,\"reason\":\"通过\"}")));

		NodeExecution execution = execute(semanticConsistencyNode.apply(state), SEMANTIC_CONSISTENCY_NODE_OUTPUT);
		ArgumentCaptor<SemanticConsistencyDTO> requestCaptor = ArgumentCaptor.forClass(SemanticConsistencyDTO.class);
		verify(nl2SqlService).performSemanticConsistency(requestCaptor.capture());

		assertEquals(true, execution.finalResult().get(SEMANTIC_CONSISTENCY_NODE_OUTPUT));
		assertEquals("postgresql", requestCaptor.getValue().getDialect());
		assertEquals("SELECT * FROM users", requestCaptor.getValue().getSql());
	}

	@Test
	void apply_nl2SqlServiceFailure_throwsException() {
		OverAllState state = createTestState();
		setupBasicState(state, "SELECT * FROM users");

		when(nl2SqlService.performSemanticConsistency(any(SemanticConsistencyDTO.class)))
			.thenThrow(new RuntimeException("Service unavailable"));

		RuntimeException exception = assertThrowsExactly(RuntimeException.class,
				() -> semanticConsistencyNode.apply(state));
		assertEquals("Service unavailable", exception.getMessage());
	}

	@Test
	void apply_unresolvedPlaceholder_skipsLlmAndRequestsRegeneration() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state, "SELECT id FROM users WHERE id = ?");

		Map<String, Object> result = execute(semanticConsistencyNode.apply(state), SEMANTIC_CONSISTENCY_NODE_OUTPUT)
			.finalResult();

		assertEquals(false, result.get(SEMANTIC_CONSISTENCY_NODE_OUTPUT));
		SqlRetryDto retry = (SqlRetryDto) result.get(SQL_REGENERATE_REASON);
		assertTrue(retry.semanticFail());
		assertTrue(retry.reason().contains("unresolved '?'"));
		verify(nl2SqlService, never()).performSemanticConsistency(any(SemanticConsistencyDTO.class));
	}

	@Test
	void apply_multipleStatements_skipsLlmAndRequestsRegeneration() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state, "SELECT id FROM users; SELECT name FROM users;");

		Map<String, Object> result = execute(semanticConsistencyNode.apply(state), SEMANTIC_CONSISTENCY_NODE_OUTPUT)
			.finalResult();

		assertEquals(false, result.get(SEMANTIC_CONSISTENCY_NODE_OUTPUT));
		SqlRetryDto retry = (SqlRetryDto) result.get(SQL_REGENERATE_REASON);
		assertTrue(retry.semanticFail());
		assertTrue(retry.reason().contains("2 executable statements"));
		verify(nl2SqlService, never()).performSemanticConsistency(any(SemanticConsistencyDTO.class));
	}

}
