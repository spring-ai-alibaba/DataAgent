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
package com.alibaba.cloud.ai.dataagent.service.code.impls;

import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorService.TaskRequest;
import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorService.TaskResponse;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiSimulationCodeExecutorServiceTest {

	private AiSimulationCodeExecutorService service;

	@Mock
	private LlmService llmService;

	@BeforeEach
	void setUp() {
		service = new AiSimulationCodeExecutorService(llmService);
	}

	@Test
	void runTask_returnsSuccessWithLlmOutput() {
		TaskRequest request = new TaskRequest("print('hello')", "{}", "run it");

		Flux<ChatResponse> chatResponseFlux = Flux.empty();
		when(llmService.call(anyString(), anyString())).thenReturn(chatResponseFlux);
		when(llmService.toStringFlux(chatResponseFlux)).thenReturn(Flux.just("hello"));

		TaskResponse response = service.runTask(request);

		assertTrue(response.isSuccess());
		assertEquals("hello", response.stdOut());
		assertNull(response.stdErr());
		assertNull(response.exceptionMsg());
		verify(llmService).call(anyString(), contains("print('hello')"));
	}

	@Test
	void runTask_concatenatesMultipleFluxElements() {
		TaskRequest request = new TaskRequest("code", "input", "req");

		Flux<ChatResponse> chatResponseFlux = Flux.empty();
		when(llmService.call(anyString(), anyString())).thenReturn(chatResponseFlux);
		when(llmService.toStringFlux(chatResponseFlux)).thenReturn(Flux.just("part1", " part2", " part3"));

		TaskResponse response = service.runTask(request);

		assertTrue(response.isSuccess());
		assertEquals("part1 part2 part3", response.stdOut());
	}

}
