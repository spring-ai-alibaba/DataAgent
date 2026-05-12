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
package com.alibaba.cloud.ai.dataagent.service.aimodelconfig;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.junit.jupiter.api.Assertions.*;

class DynamicModelFactoryTest {

	private DynamicModelFactory dynamicModelFactory;

	@BeforeEach
	void setUp() {
		dynamicModelFactory = new DynamicModelFactory();
	}

	@Test
	void createChatModel_validConfig_returnsChatModel() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test-key")
			.baseUrl("http://localhost:8080")
			.modelName("gpt-4")
			.temperature(0.7)
			.maxTokens(2000)
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertNotNull(chatModel);
	}

	@Test
	void createChatModel_emptyBaseUrl_throwsException() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("")
			.modelName("gpt-4")
			.build();

		assertThrows(IllegalArgumentException.class, () -> dynamicModelFactory.createChatModel(config));
	}

	@Test
	void createChatModel_nullBaseUrl_throwsException() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl(null)
			.modelName("gpt-4")
			.build();

		assertThrows(IllegalArgumentException.class, () -> dynamicModelFactory.createChatModel(config));
	}

	@Test
	void createChatModel_emptyModelName_throwsException() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("http://localhost:8080")
			.modelName("")
			.build();

		assertThrows(IllegalArgumentException.class, () -> dynamicModelFactory.createChatModel(config));
	}

	@Test
	void createChatModel_nonCustomProviderWithoutApiKey_throwsException() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("")
			.baseUrl("http://localhost:8080")
			.modelName("gpt-4")
			.build();

		assertThrows(IllegalArgumentException.class, () -> dynamicModelFactory.createChatModel(config));
	}

	@Test
	void createChatModel_customProviderWithoutApiKey_succeeds() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("custom")
			.apiKey("")
			.baseUrl("http://localhost:8080")
			.modelName("local-model")
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertNotNull(chatModel);
	}

	@Test
	void createChatModel_withCompletionsPath_succeeds() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("custom")
			.apiKey("")
			.baseUrl("http://localhost:8080")
			.modelName("local-model")
			.completionsPath("/custom/chat")
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertNotNull(chatModel);
	}

	@Test
	void createEmbeddingModel_validConfig_returnsEmbeddingModel() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test-key")
			.baseUrl("http://localhost:8080")
			.modelName("text-embedding-ada-002")
			.build();

		EmbeddingModel embeddingModel = dynamicModelFactory.createEmbeddingModel(config);
		assertNotNull(embeddingModel);
	}

	@Test
	void createEmbeddingModel_emptyBaseUrl_throwsException() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("")
			.modelName("text-embedding-ada-002")
			.build();

		assertThrows(IllegalArgumentException.class, () -> dynamicModelFactory.createEmbeddingModel(config));
	}

	@Test
	void createEmbeddingModel_customProviderWithoutApiKey_succeeds() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("custom")
			.apiKey("")
			.baseUrl("http://localhost:8080")
			.modelName("local-embed")
			.build();

		EmbeddingModel embeddingModel = dynamicModelFactory.createEmbeddingModel(config);
		assertNotNull(embeddingModel);
	}

	@Test
	void createEmbeddingModel_withEmbeddingsPath_succeeds() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("custom")
			.apiKey("")
			.baseUrl("http://localhost:8080")
			.modelName("local-embed")
			.embeddingsPath("/custom/embeddings")
			.build();

		EmbeddingModel embeddingModel = dynamicModelFactory.createEmbeddingModel(config);
		assertNotNull(embeddingModel);
	}

	@Test
	void createChatModel_withProxy_succeeds() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test-key")
			.baseUrl("http://localhost:8080")
			.modelName("gpt-4")
			.proxyEnabled(true)
			.proxyHost("proxy.example.com")
			.proxyPort(8888)
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertNotNull(chatModel);
	}

	@Test
	void createChatModel_withProxyAndAuth_succeeds() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test-key")
			.baseUrl("http://localhost:8080")
			.modelName("gpt-4")
			.proxyEnabled(true)
			.proxyHost("proxy.example.com")
			.proxyPort(8888)
			.proxyUsername("user")
			.proxyPassword("pass")
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertNotNull(chatModel);
	}

	@Test
	void createChatModel_proxyDisabled_noProxyUsed() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test-key")
			.baseUrl("http://localhost:8080")
			.modelName("gpt-4")
			.proxyEnabled(false)
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertNotNull(chatModel);
	}

	@Test
	void createChatModel_proxyNull_noProxyUsed() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test-key")
			.baseUrl("http://localhost:8080")
			.modelName("gpt-4")
			.proxyEnabled(null)
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertNotNull(chatModel);
	}

	@Test
	void createChatModel_nullApiKey_treatedAsEmpty() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("custom")
			.apiKey(null)
			.baseUrl("http://localhost:8080")
			.modelName("local-model")
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertNotNull(chatModel);
	}

}
