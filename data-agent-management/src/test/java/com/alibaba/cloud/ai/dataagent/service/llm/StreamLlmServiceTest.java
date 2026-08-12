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

import com.alibaba.cloud.ai.dataagent.dto.prompt.FeasibilityAssessmentOutputDTO;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.service.llm.impls.StreamLlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamLlmServiceTest {

	@Mock
	private AiModelRegistry registry;

	@Mock
	private ChatClient chatClient;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.StreamResponseSpec streamResponseSpec;

	@Mock
	private ChatClient.CallResponseSpec callResponseSpec;

	private StreamLlmService streamLlmService;

	private ChatResponse mockResponse;

	@BeforeEach
	void setUp() {
		mockResponse = ChatResponseUtil.createPureResponse("streamed output");
		streamLlmService = new StreamLlmService(registry);
	}

	private void stubPrompt() {
		when(registry.getChatClient()).thenReturn(chatClient);
		when(chatClient.prompt()).thenReturn(requestSpec);
	}

	private void stubStreamingResponse() {
		when(requestSpec.stream()).thenReturn(streamResponseSpec);
		when(streamResponseSpec.chatResponse()).thenReturn(Flux.just(mockResponse));
	}

	private void stubBlockingResponse() {
		when(requestSpec.advisors(any(Advisor[].class))).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.chatResponse()).thenReturn(mockResponse);
	}

	@Test
	void callUser_validPrompt_returnsStreamFlux() {
		stubPrompt();
		when(requestSpec.user("Hello")).thenReturn(requestSpec);
		stubStreamingResponse();

		Flux<ChatResponse> result = streamLlmService.callUser("Hello");

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
	}

	@Test
	void callSystem_validPrompt_returnsStreamFlux() {
		stubPrompt();
		when(requestSpec.system("System prompt")).thenReturn(requestSpec);
		stubStreamingResponse();

		Flux<ChatResponse> result = streamLlmService.callSystem("System prompt");

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
	}

	@Test
	void call_validPrompts_returnsStreamFlux() {
		stubPrompt();
		when(requestSpec.system("system")).thenReturn(requestSpec);
		when(requestSpec.user("user")).thenReturn(requestSpec);
		stubStreamingResponse();

		Flux<ChatResponse> result = streamLlmService.call("system", "user");

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
	}

	@Test
	void callUser_structuredOutput_usesBlockingAdvisorBecauseValidationDoesNotSupportStreaming() {
		stubPrompt();
		when(requestSpec.user("Hello")).thenReturn(requestSpec);
		stubBlockingResponse();

		Flux<ChatResponse> result = streamLlmService.callUser("Hello", FeasibilityAssessmentOutputDTO.class);

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
		verify(requestSpec).advisors(any(Advisor[].class));
		verify(requestSpec).call();
		verify(requestSpec, never()).stream();
	}

	@Test
	void call_structuredOutput_preservesSystemRoleAndUsesValidationAdvisor() {
		stubPrompt();
		when(requestSpec.system("system")).thenReturn(requestSpec);
		when(requestSpec.user("user")).thenReturn(requestSpec);
		stubBlockingResponse();

		Flux<ChatResponse> result = streamLlmService.call("system", "user", FeasibilityAssessmentOutputDTO.class);

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
		verify(requestSpec).system("system");
		verify(requestSpec).user("user");
		verify(requestSpec).advisors(any(Advisor[].class));
		verify(requestSpec).call();
	}

}
