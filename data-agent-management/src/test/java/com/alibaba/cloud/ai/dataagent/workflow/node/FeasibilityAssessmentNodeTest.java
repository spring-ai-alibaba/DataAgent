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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
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

	@Mock
	private JsonParseUtil jsonParseUtil;

	private FeasibilityAssessmentNode feasibilityAssessmentNode;

	@BeforeEach
	void setUp() {
		feasibilityAssessmentNode = new FeasibilityAssessmentNode(llmService, jsonParseUtil);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(TABLE_RELATION_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(EVIDENCE, new ReplaceStrategy());
		state.registerKeyAndStrategy(MULTI_TURN_CONTEXT, new ReplaceStrategy());
		state.registerKeyAndStrategy(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(FINAL_ANSWER, new ReplaceStrategy());
		return state;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> execute(Map<String, Object> nodeResult) {
		Flux<GraphResponse<StreamingOutput>> generator = (Flux<GraphResponse<StreamingOutput>>) nodeResult
			.get(FEASIBILITY_ASSESSMENT_NODE_OUTPUT);
		List<GraphResponse<StreamingOutput>> responses = generator.collectList().block(Duration.ofSeconds(2));
		assertNotNull(responses);
		return responses.stream()
			.filter(GraphResponse::isDone)
			.findFirst()
			.flatMap(GraphResponse::resultValue)
			.map(value -> (Map<String, Object>) value)
			.orElseThrow();
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

		when(llmService.callUser(anyString(), any())).thenReturn(Flux.just(ChatResponseUtil.createPureResponse(
				"{\"requirementType\":\"DATA_ANALYSIS\",\"language\":\"zh-CN\",\"content\":\"查询用户数量\"}")));

		Map<String, Object> result = feasibilityAssessmentNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(FEASIBILITY_ASSESSMENT_NODE_OUTPUT));
		assertFalse(execute(result).containsKey(FINAL_ANSWER));
		verify(llmService).callUser(anyString(), any());
	}

	@Test
	void apply_infeasibleQuery_returnsGeneratorWithOutput() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("明天天气怎么样");
		state.updateState(
				Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, TABLE_RELATION_OUTPUT, createSimpleSchema(), EVIDENCE, "无相关数据"));

		when(llmService.callUser(anyString(), any())).thenReturn(Flux.just(ChatResponseUtil.createPureResponse(
				"{\"requirementType\":\"FREE_CHAT\",\"language\":\"zh-CN\",\"content\":\"查询与数据库无关\"}")));

		Map<String, Object> result = feasibilityAssessmentNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(FEASIBILITY_ASSESSMENT_NODE_OUTPUT));
		assertEquals("查询与数据库无关", execute(result).get(FINAL_ANSWER));
	}

	@Test
	void apply_withMultiTurnContext_includesContextInPrompt() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询用户订单");
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, TABLE_RELATION_OUTPUT, createSimpleSchema(), EVIDENCE,
				"evidence", MULTI_TURN_CONTEXT, "之前查询了用户列表"));

		when(llmService.callUser(anyString(), any())).thenReturn(Flux.just(ChatResponseUtil.createPureResponse(
				"{\"requirementType\":\"DATA_ANALYSIS\",\"language\":\"zh-CN\",\"content\":\"查询用户订单\"}")));

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

		when(llmService.callUser(anyString(), any())).thenReturn(Flux.just(
				ChatResponseUtil.createPureResponse("{\"requirementType\":\"DATA_ANALYSIS\",\"language\":\"zh-CN\","),
				ChatResponseUtil.createPureResponse("\"content\":\"查询销售额\"}")));

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
