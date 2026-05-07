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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeasibilityAssessmentNodeTest {

	@Mock
	private LlmService llmService;

	private FeasibilityAssessmentNode feasibilityAssessmentNode;

	@BeforeEach
	void setUp() {
		feasibilityAssessmentNode = new FeasibilityAssessmentNode(llmService);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(TABLE_RELATION_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(EVIDENCE, new ReplaceStrategy());
		state.registerKeyAndStrategy(MULTI_TURN_CONTEXT, new ReplaceStrategy());
		state.registerKeyAndStrategy(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, new ReplaceStrategy());
		return state;
	}

	private SchemaDTO createSimpleSchema() {
		SchemaDTO schema = new SchemaDTO();
		schema.setName("test_db");
		schema.setTable(new ArrayList<>());
		schema.setForeignKeys(new ArrayList<>());
		return schema;
	}

	@Test
	void apply_feasibleQuery_returnsGeneratorWithOutput() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询所有用户数量");
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, TABLE_RELATION_OUTPUT, createSimpleSchema(), EVIDENCE,
				"用户表有id, name字段"));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("可行：可以通过SQL查询用户数量")));

		Map<String, Object> result = feasibilityAssessmentNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(FEASIBILITY_ASSESSMENT_NODE_OUTPUT));
		verify(llmService).callUser(anyString());
	}

	@Test
	void apply_infeasibleQuery_returnsGeneratorWithOutput() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("明天天气怎么样");
		state.updateState(
				Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, TABLE_RELATION_OUTPUT, createSimpleSchema(), EVIDENCE, "无相关数据"));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("不可行：该查询与数据库无关")));

		Map<String, Object> result = feasibilityAssessmentNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(FEASIBILITY_ASSESSMENT_NODE_OUTPUT));
	}

	@Test
	void apply_withMultiTurnContext_includesContextInPrompt() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询用户订单");
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, TABLE_RELATION_OUTPUT, createSimpleSchema(), EVIDENCE,
				"evidence", MULTI_TURN_CONTEXT, "之前查询了用户列表"));

		when(llmService.callUser(anyString())).thenReturn(Flux.just(ChatResponseUtil.createPureResponse("可行")));

		Map<String, Object> result = feasibilityAssessmentNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(FEASIBILITY_ASSESSMENT_NODE_OUTPUT));
	}

	@Test
	void apply_llmReturnsMultipleChunks_returnsGenerator() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询销售额");
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, TABLE_RELATION_OUTPUT, createSimpleSchema(), EVIDENCE,
				"sales table exists"));

		when(llmService.callUser(anyString())).thenReturn(Flux.just(ChatResponseUtil.createPureResponse("可行："),
				ChatResponseUtil.createPureResponse("可以查询销售额数据")));

		Map<String, Object> result = feasibilityAssessmentNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(FEASIBILITY_ASSESSMENT_NODE_OUTPUT));
	}

	@Test
	void apply_missingSchema_throwsException() {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询");
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, EVIDENCE, "evidence"));

		assertThrows(IllegalStateException.class, () -> feasibilityAssessmentNode.apply(state));
	}

}
