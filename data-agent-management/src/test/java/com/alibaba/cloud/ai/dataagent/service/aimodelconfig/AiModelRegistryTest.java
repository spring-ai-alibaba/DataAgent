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
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiModelRegistryTest {

	@Mock
	private DynamicModelFactory modelFactory;

	@Mock
	private ModelConfigDataService modelConfigDataService;

	@Mock
	private ChatModel chatModel;

	@Mock
	private EmbeddingModel embeddingModel;

	private AiModelRegistry registry;

	@BeforeEach
	void setUp() {
		registry = new AiModelRegistry(modelFactory, modelConfigDataService);
	}

	@Test
	void getChatClient_noActiveConfig_throwsClearException() {
		when(modelConfigDataService.getActiveConfigByType(ModelType.CHAT)).thenReturn(null);

		IllegalStateException error = assertThrows(IllegalStateException.class, () -> registry.getChatClient());
		assertTrue(error.getMessage().contains("No active CHAT model"));
	}

	@Test
	void getChatClient_validConfig_returnsChatClient() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("http://localhost:8080")
			.modelName("gpt-4")
			.build();
		when(modelConfigDataService.getActiveConfigByType(ModelType.CHAT)).thenReturn(config);
		when(modelFactory.createChatModel(config)).thenReturn(chatModel);

		assertNotNull(registry.getChatClient());
		verify(modelConfigDataService).getActiveConfigByType(ModelType.CHAT);
		verify(modelFactory).createChatModel(config);
	}

	@Test
	void getChatClient_cachesResult_secondCallReturnsSame() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("http://localhost:8080")
			.modelName("gpt-4")
			.build();
		when(modelConfigDataService.getActiveConfigByType(ModelType.CHAT)).thenReturn(config);
		when(modelFactory.createChatModel(config)).thenReturn(chatModel);

		var first = registry.getChatClient();
		var second = registry.getChatClient();

		assertSame(first, second);
		verify(modelFactory, times(1)).createChatModel(any());
	}

	@Test
	void getChatClient_factoryThrows_preservesCause() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("http://localhost:8080")
			.modelName("gpt-4")
			.build();
		when(modelConfigDataService.getActiveConfigByType(ModelType.CHAT)).thenReturn(config);
		when(modelFactory.createChatModel(config)).thenThrow(new RuntimeException("factory error"));

		IllegalStateException error = assertThrows(IllegalStateException.class, () -> registry.getChatClient());
		assertEquals("factory error", error.getCause().getMessage());
	}

	@Test
	void getEmbeddingModel_noActiveConfig_throwsClearException() {
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		IllegalStateException error = assertThrows(IllegalStateException.class, () -> registry.getEmbeddingModel());
		assertTrue(error.getMessage().contains("No active EMBEDDING model"));
	}

	@Test
	void getEmbeddingModel_validConfig_returnsModel() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("http://localhost:8080")
			.modelName("text-embedding")
			.build();
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(config);
		when(modelFactory.createEmbeddingModel(config)).thenReturn(embeddingModel);

		EmbeddingModel result = registry.getEmbeddingModel();

		assertSame(embeddingModel, result);
	}

	@Test
	void getEmbeddingModel_factoryThrows_preservesCause() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("http://localhost:8080")
			.modelName("text-embedding")
			.build();
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(config);
		when(modelFactory.createEmbeddingModel(config)).thenThrow(new RuntimeException("factory error"));

		IllegalStateException error = assertThrows(IllegalStateException.class, () -> registry.getEmbeddingModel());
		assertEquals("factory error", error.getCause().getMessage());
	}

	@Test
	void getEmbeddingModel_cachesResult() {
		ModelConfigDTO config = ModelConfigDTO.builder().modelName("embedding").build();
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(config);
		when(modelFactory.createEmbeddingModel(config)).thenReturn(embeddingModel);

		EmbeddingModel first = registry.getEmbeddingModel();
		EmbeddingModel second = registry.getEmbeddingModel();

		assertSame(first, second);
	}

	@Test
	void refreshChat_clearsCache() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("http://localhost:8080")
			.modelName("gpt-4")
			.build();
		when(modelConfigDataService.getActiveConfigByType(ModelType.CHAT)).thenReturn(config);
		when(modelFactory.createChatModel(config)).thenReturn(chatModel);

		registry.getChatClient();
		registry.refreshChat();

		registry.getChatClient();
		verify(modelFactory, times(2)).createChatModel(any());
	}

	@Test
	void refreshEmbedding_clearsCache() {
		ModelConfigDTO config = ModelConfigDTO.builder().modelName("embedding").build();
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(config);
		when(modelFactory.createEmbeddingModel(config)).thenReturn(embeddingModel);

		registry.getEmbeddingModel();
		registry.refreshEmbedding();

		registry.getEmbeddingModel();
		verify(modelConfigDataService, times(2)).getActiveConfigByType(ModelType.EMBEDDING);
	}

}
