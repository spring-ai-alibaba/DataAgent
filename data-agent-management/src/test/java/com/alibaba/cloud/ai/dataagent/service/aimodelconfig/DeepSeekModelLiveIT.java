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
package com.alibaba.cloud.ai.dataagent.service.aimodelconfig;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("live")
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class DeepSeekModelLiveIT {

	private static final String EXPECTED_CHAT_RESPONSE = "DATAAGENT_LIVE_OK";

	private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

	private static final String DEFAULT_COMPLETIONS_PATH = "/chat/completions";

	private final DynamicModelFactory modelFactory = new DynamicModelFactory();

	@Test
	void streamingChat_callsDeepSeekAndReturnsContentWithUsage() {
		ChatModel chatModel = modelFactory.createChatModel(ModelConfigDTO.builder()
			.provider("deepseek")
			.apiKey(requiredApiKey())
			.baseUrl(baseUrl())
			.completionsPath(environmentOrDefault("DATAAGENT_LIVE_DEEPSEEK_COMPLETIONS_PATH", DEFAULT_COMPLETIONS_PATH))
			.modelName(environmentOrDefault("DATAAGENT_LIVE_DEEPSEEK_CHAT_MODEL", "deepseek-v4-flash"))
			.modelType("CHAT")
			.temperature(0.0)
			.maxTokens(256)
			.build());

		Prompt prompt = new Prompt(
				"Return exactly DATAAGENT_LIVE_OK with no punctuation, explanation, or Markdown formatting.");
		List<ChatResponse> responses = chatModel.stream(prompt).collectList().block(Duration.ofSeconds(90));

		assertThat(responses).isNotNull().isNotEmpty();
		String content = responses.stream()
			.map(ChatResponse::getResult)
			.filter(Objects::nonNull)
			.map(result -> result.getOutput().getText())
			.filter(Objects::nonNull)
			.reduce("", String::concat)
			.trim();
		assertThat(content).isEqualTo(EXPECTED_CHAT_RESPONSE);

		int totalTokens = responses.stream()
			.map(ChatResponse::getMetadata)
			.filter(Objects::nonNull)
			.map(metadata -> metadata.getUsage())
			.filter(Objects::nonNull)
			.map(usage -> usage.getTotalTokens())
			.filter(Objects::nonNull)
			.mapToInt(Integer::intValue)
			.max()
			.orElse(0);
		assertThat(totalTokens).isPositive();
	}

	private String requiredApiKey() {
		String apiKey = environmentOrDefault("DATAAGENT_LIVE_DEEPSEEK_API_KEY", System.getenv("DEEPSEEK_API_KEY"));
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalStateException(
					"Set DATAAGENT_LIVE_DEEPSEEK_API_KEY or DEEPSEEK_API_KEY before running live-model tests");
		}
		if (!apiKey.equals(apiKey.strip())) {
			throw new IllegalStateException("DeepSeek API key must not contain leading or trailing whitespace");
		}
		return apiKey;
	}

	private String baseUrl() {
		return environmentOrDefault("DATAAGENT_LIVE_DEEPSEEK_BASE_URL", DEFAULT_BASE_URL).replaceAll("/+$", "");
	}

	private String environmentOrDefault(String name, String defaultValue) {
		String value = System.getenv(name);
		return StringUtils.hasText(value) ? value : defaultValue;
	}

}
