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

import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeExecution;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.EVIDENCE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.MULTI_TURN_CONTEXT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.QUERY_ENHANCE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryEnhanceNodeTest {

	@Mock
	private LlmService llmService;

	private QueryEnhanceNode queryEnhanceNode;

	@BeforeEach
	void setUp() {
		queryEnhanceNode = new QueryEnhanceNode(llmService, new JsonParseUtil(llmService));
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
	void apply_validQuery_returnsParsedQuery() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, "查询所有用户", EVIDENCE, "用户表包含id和name字段"));
		when(llmService.callUser(anyString())).thenReturn(Flux.just(ChatResponseUtil
			.createPureResponse("{\"canonical_query\":\"查询所有用户信息\",\"expanded_queries\":[\"查询用户\"]}")));

		NodeExecution execution = execute(queryEnhanceNode.apply(state), QUERY_ENHANCE_NODE_OUTPUT);
		QueryEnhanceOutputDTO output = output(execution);

		assertEquals("查询所有用户信息", output.getCanonicalQuery());
		assertEquals(List.of("查询用户"), output.getExpandedQueries());
		verify(llmService).callUser(anyString());
	}

	@Test
	void apply_withMultiTurnContext_includesContextInPrompt() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, "查询所有用户", EVIDENCE, "test evidence", MULTI_TURN_CONTEXT, "之前查询了订单表"));
		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("{\"canonical_query\":\"查询所有用户信息\"}")));

		NodeExecution execution = execute(queryEnhanceNode.apply(state), QUERY_ENHANCE_NODE_OUTPUT);

		assertEquals("查询所有用户信息", output(execution).getCanonicalQuery());
		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(llmService).callUser(prompt.capture());
		assertTrue(prompt.getValue().contains("之前查询了订单表"));
	}

	@Test
	void apply_withoutMultiTurnContext_usesDefaultContext() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, "查询用户信息", EVIDENCE, "evidence data"));
		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("{\"canonical_query\":\"查询用户信息\"}")));

		NodeExecution execution = execute(queryEnhanceNode.apply(state), QUERY_ENHANCE_NODE_OUTPUT);

		assertEquals("查询用户信息", output(execution).getCanonicalQuery());
		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(llmService).callUser(prompt.capture());
		assertTrue(prompt.getValue().contains("(无)"));
	}

	@Test
	void apply_multipleResponseChunks_parsesCombinedJson() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, "查询用户", EVIDENCE, "evidence"));
		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("{\"canonical_query\":"),
					ChatResponseUtil.createPureResponse("\"查询所有用户\"}")));

		NodeExecution execution = execute(queryEnhanceNode.apply(state), QUERY_ENHANCE_NODE_OUTPUT);

		assertEquals("查询所有用户", output(execution).getCanonicalQuery());
	}

	@Test
	void apply_emptyInput_throwsIllegalStateException() {
		OverAllState state = createTestState();
		state.updateState(Map.of(EVIDENCE, "evidence"));

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> queryEnhanceNode.apply(state));
		assertEquals("State key not found: " + INPUT_KEY, exception.getMessage());
	}

	@Test
	void apply_unparseableResponse_returnsEmptyFinalState() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, "长查询内容测试", EVIDENCE, "evidence data"));
		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("response text")));
		when(llmService.toStringFlux(any())).thenCallRealMethod();

		NodeExecution execution = execute(queryEnhanceNode.apply(state), QUERY_ENHANCE_NODE_OUTPUT);

		assertTrue(execution.finalResult().isEmpty());
		verify(llmService, times(4)).callUser(anyString());
	}

	private QueryEnhanceOutputDTO output(NodeExecution execution) {
		return (QueryEnhanceOutputDTO) execution.finalResult().get(QUERY_ENHANCE_NODE_OUTPUT);
	}

}
