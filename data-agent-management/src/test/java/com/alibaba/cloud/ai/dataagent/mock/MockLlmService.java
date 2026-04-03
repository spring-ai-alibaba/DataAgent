/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.mock;

import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reusable mock LlmService for integration tests. Allows registering predefined
 * responses keyed by a substring of the system or user prompt.
 */
public class MockLlmService implements LlmService {

	private final Map<String, String> responseMap = new ConcurrentHashMap<>();

	private String defaultResponse = "mock response";

	public MockLlmService() {
	}

	public MockLlmService withDefault(String response) {
		this.defaultResponse = response;
		return this;
	}

	public MockLlmService when(String promptContains, String response) {
		responseMap.put(promptContains, response);
		return this;
	}

	private String findResponse(String prompt) {
		if (prompt == null) {
			return defaultResponse;
		}
		return responseMap.entrySet()
			.stream()
			.filter(e -> prompt.contains(e.getKey()))
			.map(Map.Entry::getValue)
			.findFirst()
			.orElse(defaultResponse);
	}

	@Override
	public Flux<ChatResponse> call(String system, String user) {
		String response = findResponse(system + " " + user);
		return Flux.just(ChatResponseUtil.createPureResponse(response));
	}

	@Override
	public Flux<ChatResponse> callSystem(String system) {
		String response = findResponse(system);
		return Flux.just(ChatResponseUtil.createPureResponse(response));
	}

	@Override
	public Flux<ChatResponse> callUser(String user) {
		String response = findResponse(user);
		return Flux.just(ChatResponseUtil.createPureResponse(response));
	}

}
