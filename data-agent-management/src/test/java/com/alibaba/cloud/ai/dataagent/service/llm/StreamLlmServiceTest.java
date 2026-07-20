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
package com.alibaba.cloud.ai.dataagent.service.llm;

import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.service.llm.impls.StreamLlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StreamLlmServiceTest {

	@Mock
	private AiModelRegistry registry;

	@Mock
	private ChatClient chatClient;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.StreamResponseSpec streamResponseSpec;

	private StreamLlmService streamLlmService;

	private ChatResponse mockResponse;

	@BeforeEach
	void setUp() {
		when(registry.getChatClient()).thenReturn(chatClient);
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.options(any(OpenAiChatOptions.class))).thenReturn(requestSpec);
		when(requestSpec.stream()).thenReturn(streamResponseSpec);

		mockResponse = ChatResponseUtil.createPureResponse("streamed output");
		when(streamResponseSpec.chatResponse()).thenReturn(Flux.just(mockResponse));

		streamLlmService = new StreamLlmService(registry);
	}

	@Test
	void callUser_validPrompt_returnsStreamFlux() {
		Flux<ChatResponse> result = streamLlmService.callUser("Hello");

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
	}

	@Test
	void callSystem_validPrompt_returnsStreamFlux() {
		Flux<ChatResponse> result = streamLlmService.callSystem("System prompt");

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
	}

	@Test
	void call_validPrompts_returnsStreamFlux() {
		Flux<ChatResponse> result = streamLlmService.call("system", "user");

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
	}

	@Test
	void callUser_withThinkingState_appliesRuntimeReasoningEffort() {
		OverAllState state = org.mockito.Mockito.mock(OverAllState.class);
		when(state.value("THINKING_ENABLED")).thenReturn(Optional.of(true));
		when(state.value("REASONING_EFFORT")).thenReturn(Optional.of("high"));

		streamLlmService.callUser("Hello", state).blockLast();

		ArgumentCaptor<OpenAiChatOptions> captor = ArgumentCaptor.forClass(OpenAiChatOptions.class);
		verify(requestSpec).options(captor.capture());
		assertEquals("high", captor.getValue().getReasoningEffort());
		assertEquals("enabled", thinkingType(captor.getValue()));
	}

	@Test
	void callUser_withThinkingDisabled_explicitlyDisablesThinking() {
		OverAllState state = org.mockito.Mockito.mock(OverAllState.class);
		when(state.value("THINKING_ENABLED")).thenReturn(Optional.of(false));

		streamLlmService.callUser("Hello", state).blockLast();

		ArgumentCaptor<OpenAiChatOptions> captor = ArgumentCaptor.forClass(OpenAiChatOptions.class);
		verify(requestSpec).options(captor.capture());
		assertNull(captor.getValue().getReasoningEffort());
		assertEquals("disabled", thinkingType(captor.getValue()));
	}

	@SuppressWarnings("unchecked")
	private String thinkingType(OpenAiChatOptions options) {
		Map<String, Object> thinking = (Map<String, Object>) options.getExtraBody().get("thinking");
		return (String) thinking.get("type");
	}

}
