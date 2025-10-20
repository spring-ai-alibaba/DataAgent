/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.service.llm;

import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

public interface LlmService {

	String call(String prompt);

	String callWithSystemPrompt(String system, String user);

	/**
	 * Stream the response to the user's prompt
	 * @param prompt The user's input prompt
	 * @return Streaming response
	 */
	Flux<ChatResponse> streamCall(String prompt);

	Flux<ChatResponse> streamCallSystem(String system);

	/**
	 * Stream the response to the user's prompt with a system prompt
	 * @param system The system prompt
	 * @param user The user's input
	 * @return Streaming response
	 */
	Flux<ChatResponse> streamCallWithSystemPrompt(String system, String user);

}
