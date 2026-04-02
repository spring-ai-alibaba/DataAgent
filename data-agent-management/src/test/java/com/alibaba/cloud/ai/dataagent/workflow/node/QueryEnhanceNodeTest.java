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

import java.util.Map;

import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
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
class QueryEnhanceNodeTest {

	@Mock
	private LlmService llmService;

	@Mock
	private JsonParseUtil jsonParseUtil;

	private QueryEnhanceNode queryEnhanceNode;

	@BeforeEach
	void setUp() {
		queryEnhanceNode = new QueryEnhanceNode(llmService, jsonParseUtil);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(INPUT_KEY, new ReplaceStrategy());
		state.registerKeyAndStrategy(EVIDENCE, new ReplaceStrategy());
		state.registerKeyAndStrategy(MULTI_TURN_CONTEXT, new ReplaceStrategy());
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		return state;
	}

	@Test
	void apply_validQuery_returnsMapWithGeneratorKey() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, "查询所有用户", EVIDENCE, "用户表包含id和name字段"));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("{\"canonical_query\":\"查询所有用户信息\"}")));

		Map<String, Object> result = queryEnhanceNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(QUERY_ENHANCE_NODE_OUTPUT));
		verify(llmService).callUser(anyString());
	}

	@Test
	void apply_withMultiTurnContext_callsLlmWithContext() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, "查询所有用户", EVIDENCE, "test evidence", MULTI_TURN_CONTEXT, "之前查询了订单表"));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("{\"canonical_query\":\"查询所有用户信息\"}")));

		Map<String, Object> result = queryEnhanceNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(QUERY_ENHANCE_NODE_OUTPUT));
	}

	@Test
	void apply_withoutMultiTurnContext_usesDefault() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, "查询用户信息", EVIDENCE, "evidence data"));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("{\"canonical_query\":\"查询用户信息\"}")));

		Map<String, Object> result = queryEnhanceNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(QUERY_ENHANCE_NODE_OUTPUT));
	}

	@Test
	void apply_llmReturnsMultipleChunks_returnsGeneratorKey() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, "查询用户", EVIDENCE, "evidence"));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("{\"canonical_query\":"),
					ChatResponseUtil.createPureResponse("\"查询所有用户\"}")));

		Map<String, Object> result = queryEnhanceNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(QUERY_ENHANCE_NODE_OUTPUT));
	}

	@Test
	void apply_emptyInput_throwsIllegalStateException() {
		OverAllState state = createTestState();
		state.updateState(Map.of(EVIDENCE, "evidence"));

		assertThrows(IllegalStateException.class, () -> queryEnhanceNode.apply(state));
	}

	@Test
	void apply_resultIsFluxType_generatorNotNull() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, "长查询内容测试", EVIDENCE, "evidence data"));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("response text")));

		Map<String, Object> result = queryEnhanceNode.apply(state);

		assertNotNull(result.get(QUERY_ENHANCE_NODE_OUTPUT));
	}

}
