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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntentRecognitionNodeTest {

	private static final String CHAT_QUERY = "统计上周PV数据";
	private static final String JSON_ANALYSIS = """
			{
				"intent": "data_analysis",
				"confidence": 0.95
			}
			""";

	@Mock
	private LlmService llmService;

	@Mock
	private JsonParseUtil jsonParseUtil;

	private IntentRecognitionNode intentRecognitionNode;

	@BeforeEach
	void setUp() {
		intentRecognitionNode = new IntentRecognitionNode(llmService, jsonParseUtil);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(INTENT_RECOGNITION_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(INPUT_KEY, new ReplaceStrategy());
		state.registerKeyAndStrategy(MULTI_TURN_CONTEXT, new ReplaceStrategy());
		return state;
	}

	@Test
	void simpleDataQuery_returnsDataAnalysisIntent() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(
			INPUT_KEY, CHAT_QUERY,
			MULTI_TURN_CONTEXT, "(无)"
		));

		when(llmService.callUser(any()))
			.thenReturn(Flux.just(
				ChatResponseUtil.createResponse("正在进行意图识别..."),
				ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()),
				ChatResponseUtil.createPureResponse(JSON_ANALYSIS),
				ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
				ChatResponseUtil.createResponse("\n意图识别完成！")
			));

		Map<String, Object> result = intentRecognitionNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(INTENT_RECOGNITION_NODE_OUTPUT));
	}

	@Test
	void simpleChatQuery_returnsChatIntent() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(
			INPUT_KEY, "你好",
			MULTI_TURN_CONTEXT, "(无)"
		));

		when(llmService.callUser(any()))
			.thenReturn(Flux.just(
				ChatResponseUtil.createResponse("正在进行意图识别..."),
				ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()),
				ChatResponseUtil.createPureResponse("""
						{
							"intent": "chat",
							"confidence": 0.98
						}
						"""),
				ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
				ChatResponseUtil.createResponse("\n意图识别完成！")
			));

		Map<String, Object> result = intentRecognitionNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(INTENT_RECOGNITION_NODE_OUTPUT));
	}

	@Test
	void emptyInput_throwsException() throws Exception {
		OverAllState state = createTestState();

		assertThrows(IllegalStateException.class, () -> intentRecognitionNode.apply(state));
	}

	@Test
	void jsonParseFailure_throwsException() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(
			INPUT_KEY, CHAT_QUERY,
			MULTI_TURN_CONTEXT, "(无)"
		));

		when(llmService.callUser(any()))
			.thenReturn(Flux.just(
				ChatResponseUtil.createResponse("正在进行意图识别..."),
				ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()),
				ChatResponseUtil.createPureResponse("invalid json"),
				ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
				ChatResponseUtil.createResponse("\n意图识别完成！")
			));

		assertThrows(RuntimeException.class, () -> intentRecognitionNode.apply(state));
	}

	@Test
	void llmServiceFailure_throwsException() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(
			INPUT_KEY, CHAT_QUERY,
			MULTI_TURN_CONTEXT, "(无)"
		));

		when(llmService.callUser(any()))
			.thenThrow(new RuntimeException("LLM service unavailable"));

		assertThrows(RuntimeException.class, () -> intentRecognitionNode.apply(state));
	}

	@Test
	void multiTurnContext_preservesContextInPrompt() throws Exception {
		OverAllState state = createTestState();
		String context = "user: 查询PV，assistant: 已提供数据";
		state.updateState(Map.of(
			INPUT_KEY, CHAT_QUERY,
			MULTI_TURN_CONTEXT, context
		));

		when(llmService.callUser(any()))
			.thenReturn(Flux.just(
				ChatResponseUtil.createResponse("正在进行意图识别..."),
				ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()),
				ChatResponseUtil.createPureResponse(JSON_ANALYSIS),
				ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
				ChatResponseUtil.createResponse("\n意图识别完成！")
			));

		Map<String, Object> result = intentRecognitionNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(INTENT_RECOGNITION_NODE_OUTPUT));

		verify(llmService).callUser(any());
	}

	@Test
	void longInput_handlesWithoutTruncation() throws Exception {
		OverAllState state = createTestState();
		StringBuilder longInput = new StringBuilder();
		for (int i = 0; i < 1000; i++) {
			longInput.append("a");
		}
		state.updateState(Map.of(
			INPUT_KEY, longInput.toString(),
			MULTI_TURN_CONTEXT, "(无)"
		));

		when(llmService.callUser(any()))
			.thenReturn(Flux.just(
				ChatResponseUtil.createResponse("正在进行意图识别..."),
				ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()),
				ChatResponseUtil.createPureResponse(JSON_ANALYSIS),
				ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
				ChatResponseUtil.createResponse("\n意图识别完成！")
			));

		Map<String, Object> result = intentRecognitionNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(INTENT_RECOGNITION_NODE_OUTPUT));
	}
}
