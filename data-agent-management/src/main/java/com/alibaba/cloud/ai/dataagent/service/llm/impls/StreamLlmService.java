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
package com.alibaba.cloud.ai.dataagent.service.llm.impls;

import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.enums.ReasoningEffort;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.REASONING_EFFORT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.THINKING_ENABLED;

@AllArgsConstructor
public class StreamLlmService implements LlmService {

	private final AiModelRegistry registry;

	@Override
	public Flux<ChatResponse> call(String system, String user) {
		return registry.getChatClient().prompt().system(system).user(user).stream().chatResponse();
	}

	@Override
	public Flux<ChatResponse> callSystem(String system) {
		return registry.getChatClient().prompt().system(system).stream().chatResponse();
	}

	@Override
	public Flux<ChatResponse> callUser(String user) {
		return registry.getChatClient().prompt().user(user).stream().chatResponse();
	}

	@Override
	public Flux<ChatResponse> call(String system, String user, OverAllState state) {
		return applyThinkingOptions(registry.getChatClient().prompt(), state).system(system).user(user).stream().chatResponse();
	}

	@Override
	public Flux<ChatResponse> callSystem(String system, OverAllState state) {
		return applyThinkingOptions(registry.getChatClient().prompt(), state).system(system).stream().chatResponse();
	}

	@Override
	public Flux<ChatResponse> callUser(String user, OverAllState state) {
		return applyThinkingOptions(registry.getChatClient().prompt(), state).user(user).stream().chatResponse();
	}

	private ChatClient.ChatClientRequestSpec applyThinkingOptions(ChatClient.ChatClientRequestSpec spec,
			OverAllState state) {
		Boolean enabled = StateUtil.getObjectValue(state, THINKING_ENABLED, Boolean.class, (Boolean) null);
		if (enabled == null) {
			return spec;
		}

		String type = Boolean.TRUE.equals(enabled) ? "enabled" : "disabled";
		OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
			.extraBody(Map.of("thinking", Map.of("type", type)));
		if (Boolean.TRUE.equals(enabled)) {
			String effort = ReasoningEffort
				.fromCode(StateUtil.getStringValue(state, REASONING_EFFORT, ReasoningEffort.HIGH.getCode()))
				.getCode();
			options.reasoningEffort(effort);
		}
		return spec.options(options.build());
	}

}
