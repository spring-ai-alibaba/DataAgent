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
import com.alibaba.cloud.ai.dataagent.service.llm.impls.BlockLlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlockLlmServiceTest {

	@Mock
	private AiModelRegistry registry;

	@Mock
	private ChatClient chatClient;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.CallResponseSpec callResponseSpec;

	private BlockLlmService blockLlmService;

	private ChatResponse mockResponse;

	@BeforeEach
	void setUp() {
		when(registry.getChatClient()).thenReturn(chatClient);
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);

		mockResponse = ChatResponseUtil.createPureResponse("test output");
		when(callResponseSpec.chatResponse()).thenReturn(mockResponse);

		blockLlmService = new BlockLlmService(registry);
	}

	@Test
	void callUser_validPrompt_returnsChatResponse() {
		Flux<ChatResponse> result = blockLlmService.callUser("Hello");

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("test output"))
			.verifyComplete();
	}

	@Test
	void callSystem_validPrompt_returnsSystemResponse() {
		Flux<ChatResponse> result = blockLlmService.callSystem("System prompt");

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("test output"))
			.verifyComplete();
	}

	@Test
	void call_validPrompts_returnsChatResponse() {
		Flux<ChatResponse> result = blockLlmService.call("system", "user");

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("test output"))
			.verifyComplete();
	}

	@Test
	void toStringFlux_validFlux_extractsText() {
		Flux<ChatResponse> responseFlux = Flux.just(ChatResponseUtil.createPureResponse("hello "),
				ChatResponseUtil.createPureResponse("world"));

		Flux<String> result = blockLlmService.toStringFlux(responseFlux);

		StepVerifier.create(result).expectNext("hello ").expectNext("world").verifyComplete();
	}

}
