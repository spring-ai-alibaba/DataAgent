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
package com.alibaba.cloud.ai.dataagent.converter;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelConfigConverterTest {

	@Test
	void toDTO_nullEntity_returnsNull() {
		assertNull(ModelConfigConverter.toDTO(null));
	}

	@Test
	void toDTO_validEntity_mapsAllFields() {
		ModelConfig entity = new ModelConfig();
		entity.setId(1);
		entity.setProvider("openai");
		entity.setBaseUrl("http://localhost:8080");
		entity.setModelName("gpt-4");
		entity.setTemperature(0.7);
		entity.setMaxTokens(2000);
		entity.setIsActive(true);
		entity.setApiKey("sk-test");
		entity.setModelType(ModelType.CHAT);
		entity.setCompletionsPath("/v1/chat");
		entity.setEmbeddingsPath("/v1/embeddings");
		entity.setProxyEnabled(true);
		entity.setProxyHost("proxy.example.com");
		entity.setProxyPort(8888);
		entity.setProxyUsername("user");
		entity.setProxyPassword("pass");

		ModelConfigDTO dto = ModelConfigConverter.toDTO(entity);

		assertNotNull(dto);
		assertEquals(1, dto.getId());
		assertEquals("openai", dto.getProvider());
		assertEquals("http://localhost:8080", dto.getBaseUrl());
		assertEquals("gpt-4", dto.getModelName());
		assertEquals(0.7, dto.getTemperature());
		assertEquals(2000, dto.getMaxTokens());
		assertTrue(dto.getIsActive());
		assertEquals("sk-test", dto.getApiKey());
		assertEquals("CHAT", dto.getModelType());
		assertEquals("/v1/chat", dto.getCompletionsPath());
		assertEquals("/v1/embeddings", dto.getEmbeddingsPath());
		assertTrue(dto.getProxyEnabled());
		assertEquals("proxy.example.com", dto.getProxyHost());
		assertEquals(8888, dto.getProxyPort());
		assertEquals("user", dto.getProxyUsername());
		assertEquals("pass", dto.getProxyPassword());
	}

	@Test
	void toEntity_validDTO_mapsAllFields() {
		ModelConfigDTO dto = ModelConfigDTO.builder()
			.id(5)
			.provider("deepseek")
			.baseUrl("http://localhost:9090")
			.apiKey("sk-deep")
			.modelName("deepseek-chat")
			.temperature(0.5)
			.maxTokens(4000)
			.modelType("CHAT")
			.completionsPath("/custom/chat")
			.embeddingsPath("/custom/embed")
			.proxyEnabled(false)
			.proxyHost("proxy2.example.com")
			.proxyPort(9999)
			.proxyUsername("u2")
			.proxyPassword("p2")
			.build();

		ModelConfig entity = ModelConfigConverter.toEntity(dto);

		assertNotNull(entity);
		assertEquals(5, entity.getId());
		assertEquals("deepseek", entity.getProvider());
		assertEquals("http://localhost:9090", entity.getBaseUrl());
		assertEquals("sk-deep", entity.getApiKey());
		assertEquals("deepseek-chat", entity.getModelName());
		assertEquals(0.5, entity.getTemperature());
		assertEquals(4000, entity.getMaxTokens());
		assertEquals(ModelType.CHAT, entity.getModelType());
		assertEquals("/custom/chat", entity.getCompletionsPath());
		assertEquals("/custom/embed", entity.getEmbeddingsPath());
		assertFalse(entity.getIsActive());
		assertEquals(0, entity.getIsDeleted());
		assertNotNull(entity.getCreatedTime());
		assertNotNull(entity.getUpdatedTime());
		assertFalse(entity.getProxyEnabled());
		assertEquals("proxy2.example.com", entity.getProxyHost());
	}

	@Test
	void toEntity_nullDTO_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> ModelConfigConverter.toEntity(null));
	}

	@Test
	void toEntity_embeddingModelType_mapsCorrectly() {
		ModelConfigDTO dto = ModelConfigDTO.builder()
			.provider("openai")
			.baseUrl("http://localhost")
			.modelName("text-embedding")
			.modelType("EMBEDDING")
			.apiKey("sk-test")
			.build();

		ModelConfig entity = ModelConfigConverter.toEntity(dto);

		assertEquals(ModelType.EMBEDDING, entity.getModelType());
	}

}
