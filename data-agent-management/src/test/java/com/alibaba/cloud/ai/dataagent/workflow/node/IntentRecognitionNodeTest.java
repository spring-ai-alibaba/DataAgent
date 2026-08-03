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
import static com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.executeForError;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.dto.prompt.IntentRecognitionOutputDTO;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeErrorExecution;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeExecution;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class IntentRecognitionNodeTest {

	private static final String CHAT_QUERY = "统计上周PV数据";

	private static final String JSON_ANALYSIS = """
			{
				"classification": "《可能的数据分析请求》",
				"response": ""
			}
			""";

	@Mock
	private LlmService llmService;

	private IntentRecognitionNode intentRecognitionNode;

	@BeforeEach
	void setUp() {
		intentRecognitionNode = new IntentRecognitionNode(llmService);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(INTENT_RECOGNITION_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(INPUT_KEY, new ReplaceStrategy());
		state.registerKeyAndStrategy(MULTI_TURN_CONTEXT, new ReplaceStrategy());
		state.registerKeyAndStrategy(FINAL_ANSWER, new ReplaceStrategy());
		return state;
	}

	@Test
	void simpleDataQuery_returnsDataAnalysisIntent() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, CHAT_QUERY, MULTI_TURN_CONTEXT, "(无)"));

		when(llmService.callUser(anyString(), any()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(JSON_ANALYSIS)));

		NodeExecution execution = execute(intentRecognitionNode.apply(state), INTENT_RECOGNITION_NODE_OUTPUT);
		IntentRecognitionOutputDTO output = output(execution);

		assertEquals("《可能的数据分析请求》", output.getClassification());
		assertEquals("", output.getResponse());
		assertFalse(execution.finalResult().containsKey(FINAL_ANSWER));
		assertTrue(execution.streamedText().contains("正在进行意图识别"));
		assertTrue(execution.streamedText().contains("意图识别完成"));
	}

	@Test
	void simpleChatQuery_returnsChatIntent() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, "你好", MULTI_TURN_CONTEXT, "(无)"));

		when(llmService.callUser(anyString(), any())).thenReturn(Flux.just(ChatResponseUtil.createPureResponse("""
				{
					"classification": "《闲聊或无关指令》",
					"response": "你好！我可以帮你分析已连接的数据。"
				}
				""")));

		NodeExecution execution = execute(intentRecognitionNode.apply(state), INTENT_RECOGNITION_NODE_OUTPUT);
		IntentRecognitionOutputDTO output = output(execution);

		assertEquals("《闲聊或无关指令》", output.getClassification());
		assertEquals("你好！我可以帮你分析已连接的数据。", output.getResponse());
		assertEquals("你好！我可以帮你分析已连接的数据。", execution.finalResult().get(FINAL_ANSWER));
	}

	@Test
	void emptyInput_throwsException() throws Exception {
		OverAllState state = createTestState();

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> intentRecognitionNode.apply(state));
		assertTrue(exception.getMessage().contains(INPUT_KEY));
	}

	@Test
	void jsonParseFailure_emitsErrorInsteadOfFalseSuccess() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, CHAT_QUERY, MULTI_TURN_CONTEXT, "(无)"));

		when(llmService.callUser(anyString(), any()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("invalid json")));

		NodeErrorExecution execution = executeForError(intentRecognitionNode.apply(state),
				INTENT_RECOGNITION_NODE_OUTPUT);

		assertNotNull(execution.error());
		assertTrue(execution.streamedText().contains("invalid json"));
	}

	@Test
	void llmServiceFailure_throwsException() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(INPUT_KEY, CHAT_QUERY, MULTI_TURN_CONTEXT, "(无)"));

		when(llmService.callUser(anyString(), any())).thenThrow(new RuntimeException("LLM service unavailable"));

		RuntimeException exception = assertThrowsExactly(RuntimeException.class,
				() -> intentRecognitionNode.apply(state));
		assertEquals("LLM service unavailable", exception.getMessage());
	}

	@Test
	void multiTurnContext_preservesContextInPrompt() throws Exception {
		OverAllState state = createTestState();
		String context = "user: 查询PV，assistant: 已提供数据";
		state.updateState(Map.of(INPUT_KEY, CHAT_QUERY, MULTI_TURN_CONTEXT, context));

		when(llmService.callUser(anyString(), any()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(JSON_ANALYSIS)));

		NodeExecution execution = execute(intentRecognitionNode.apply(state), INTENT_RECOGNITION_NODE_OUTPUT);
		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
		verify(llmService).callUser(promptCaptor.capture(), eq(IntentRecognitionOutputDTO.class));

		assertEquals("《可能的数据分析请求》", output(execution).getClassification());
		assertTrue(promptCaptor.getValue().contains(context));
		assertTrue(promptCaptor.getValue().contains(CHAT_QUERY));
	}

	@Test
	void longInput_handlesWithoutTruncation() throws Exception {
		OverAllState state = createTestState();
		StringBuilder longInput = new StringBuilder();
		for (int i = 0; i < 1000; i++) {
			longInput.append("a");
		}
		state.updateState(Map.of(INPUT_KEY, longInput.toString(), MULTI_TURN_CONTEXT, "(无)"));

		when(llmService.callUser(anyString(), any()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(JSON_ANALYSIS)));

		NodeExecution execution = execute(intentRecognitionNode.apply(state), INTENT_RECOGNITION_NODE_OUTPUT);
		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
		verify(llmService).callUser(promptCaptor.capture(), eq(IntentRecognitionOutputDTO.class));

		assertEquals("《可能的数据分析请求》", output(execution).getClassification());
		assertTrue(promptCaptor.getValue().contains(longInput));
	}

	private IntentRecognitionOutputDTO output(NodeExecution execution) {
		Object output = execution.finalResult().get(INTENT_RECOGNITION_NODE_OUTPUT);
		assertInstanceOf(IntentRecognitionOutputDTO.class, output);
		return (IntentRecognitionOutputDTO) output;
	}

}
