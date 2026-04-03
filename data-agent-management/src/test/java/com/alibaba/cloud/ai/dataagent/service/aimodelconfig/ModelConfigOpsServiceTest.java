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

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelConfigOpsServiceTest {

	private ModelConfigOpsService service;

	@Mock
	private ModelConfigDataService modelConfigDataService;

	@Mock
	private DynamicModelFactory modelFactory;

	@Mock
	private AiModelRegistry aiModelRegistry;

	@BeforeEach
	void setUp() {
		service = new ModelConfigOpsService(modelConfigDataService, modelFactory, aiModelRegistry);
	}

	@Test
	void testUpdateAndRefresh_activeChat() {
		ModelConfigDTO dto = new ModelConfigDTO();
		ModelConfig entity = new ModelConfig();
		entity.setIsActive(true);
		entity.setModelType(ModelType.CHAT);
		when(modelConfigDataService.updateConfigInDb(dto)).thenReturn(entity);

		service.updateAndRefresh(dto);

		verify(aiModelRegistry).refreshChat();
	}

	@Test
	void testUpdateAndRefresh_activeEmbedding() {
		ModelConfigDTO dto = new ModelConfigDTO();
		ModelConfig entity = new ModelConfig();
		entity.setIsActive(true);
		entity.setModelType(ModelType.EMBEDDING);
		when(modelConfigDataService.updateConfigInDb(dto)).thenReturn(entity);

		service.updateAndRefresh(dto);

		verify(aiModelRegistry).refreshEmbedding();
	}

	@Test
	void testUpdateAndRefresh_inactive() {
		ModelConfigDTO dto = new ModelConfigDTO();
		ModelConfig entity = new ModelConfig();
		entity.setIsActive(false);
		when(modelConfigDataService.updateConfigInDb(dto)).thenReturn(entity);

		service.updateAndRefresh(dto);

		verify(aiModelRegistry, never()).refreshChat();
		verify(aiModelRegistry, never()).refreshEmbedding();
	}

	@Test
	void testActivateConfig_chat() {
		ModelConfig entity = new ModelConfig();
		entity.setModelType(ModelType.CHAT);
		when(modelConfigDataService.findById(1)).thenReturn(entity);

		service.activateConfig(1);

		verify(aiModelRegistry).refreshChat();
		verify(modelConfigDataService).switchActiveStatus(1, ModelType.CHAT);
	}

	@Test
	void testActivateConfig_notFound() {
		when(modelConfigDataService.findById(1)).thenReturn(null);

		assertThrows(RuntimeException.class, () -> service.activateConfig(1));
	}

	@Test
	void testTestConnection_chat() {
		ModelConfigDTO dto = new ModelConfigDTO();
		dto.setModelType("CHAT");
		dto.setProvider("openai");
		dto.setModelName("gpt-4");

		ChatModel chatModel = mock(ChatModel.class);
		when(modelFactory.createChatModel(dto)).thenReturn(chatModel);
		when(chatModel.call("Hello")).thenReturn("Hi there");

		assertDoesNotThrow(() -> service.testConnection(dto));
	}

	@Test
	void testTestConnection_embedding() {
		ModelConfigDTO dto = new ModelConfigDTO();
		dto.setModelType("EMBEDDING");
		dto.setProvider("openai");
		dto.setModelName("text-embedding");

		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(modelFactory.createEmbeddingModel(dto)).thenReturn(embeddingModel);
		when(embeddingModel.embed("Test")).thenReturn(new float[] { 0.1f, 0.2f });

		assertDoesNotThrow(() -> service.testConnection(dto));
	}

	@Test
	void testTestConnection_unknownType() {
		ModelConfigDTO dto = new ModelConfigDTO();
		dto.setModelType("UNKNOWN");

		assertThrows(RuntimeException.class, () -> service.testConnection(dto));
	}

	@Test
	void testTestConnection_chatReturnsEmpty() {
		ModelConfigDTO dto = new ModelConfigDTO();
		dto.setModelType("CHAT");

		ChatModel chatModel = mock(ChatModel.class);
		when(modelFactory.createChatModel(dto)).thenReturn(chatModel);
		when(chatModel.call("Hello")).thenReturn("");

		assertThrows(RuntimeException.class, () -> service.testConnection(dto));
	}

	@Test
	void testTestConnection_embeddingReturnsEmpty() {
		ModelConfigDTO dto = new ModelConfigDTO();
		dto.setModelType("EMBEDDING");

		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(modelFactory.createEmbeddingModel(dto)).thenReturn(embeddingModel);
		when(embeddingModel.embed("Test")).thenReturn(new float[0]);

		assertThrows(RuntimeException.class, () -> service.testConnection(dto));
	}

}
