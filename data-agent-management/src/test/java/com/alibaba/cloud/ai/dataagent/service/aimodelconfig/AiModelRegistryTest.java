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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
	void getChatClient_noActiveConfig_throwsRuntimeException() {
		when(modelConfigDataService.getActiveConfigByType(ModelType.CHAT)).thenReturn(null);

		assertThrows(RuntimeException.class, () -> registry.getChatClient());
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
	void getChatClient_factoryThrows_throwsRuntimeException() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("http://localhost:8080")
			.modelName("gpt-4")
			.build();
		when(modelConfigDataService.getActiveConfigByType(ModelType.CHAT)).thenReturn(config);
		when(modelFactory.createChatModel(config)).thenThrow(new RuntimeException("factory error"));

		assertThrows(RuntimeException.class, () -> registry.getChatClient());
	}

	@Test
	void getEmbeddingModel_noActiveConfig_returnsDummyModel() {
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		EmbeddingModel result = registry.getEmbeddingModel();

		assertNotNull(result);
		assertEquals(1536, result.dimensions());
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
	void getEmbeddingModel_factoryThrows_returnsDummyModel() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("http://localhost:8080")
			.modelName("text-embedding")
			.build();
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(config);
		when(modelFactory.createEmbeddingModel(config)).thenThrow(new RuntimeException("factory error"));

		EmbeddingModel result = registry.getEmbeddingModel();

		assertNotNull(result);
		assertEquals(1536, result.dimensions());
	}

	@Test
	void getEmbeddingModel_cachesResult() {
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

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
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		registry.getEmbeddingModel();
		registry.refreshEmbedding();

		registry.getEmbeddingModel();
		verify(modelConfigDataService, times(2)).getActiveConfigByType(ModelType.EMBEDDING);
	}

	@Test
	void dummyEmbeddingModel_callThrowsException() {
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		EmbeddingModel dummy = registry.getEmbeddingModel();

		assertThrows(RuntimeException.class, () -> dummy.call(null));
	}

	@Test
	void dummyEmbeddingModel_embedTextReturnsEmpty() {
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		EmbeddingModel dummy = registry.getEmbeddingModel();

		assertEquals(0, dummy.embed("text").length);
	}

	@Test
	void dummyEmbeddingModel_embedListReturnsEmpty() {
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		EmbeddingModel dummy = registry.getEmbeddingModel();

		assertTrue(dummy.embed(java.util.List.of("text")).isEmpty());
	}

	@Test
	void dummyEmbeddingModel_embedForResponseReturnsNull() {
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		EmbeddingModel dummy = registry.getEmbeddingModel();

		assertNull(dummy.embedForResponse(java.util.List.of("text")));
	}

}
