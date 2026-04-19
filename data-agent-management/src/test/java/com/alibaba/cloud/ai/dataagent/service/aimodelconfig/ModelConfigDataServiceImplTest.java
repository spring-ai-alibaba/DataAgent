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
import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.mapper.ModelConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelConfigDataServiceImplTest {

	private ModelConfigDataServiceImpl service;

	@Mock
	private ModelConfigMapper modelConfigMapper;

	@BeforeEach
	void setUp() {
		service = new ModelConfigDataServiceImpl(modelConfigMapper);
	}

	@Test
	void findById_returnsConfig() {
		ModelConfig config = new ModelConfig();
		config.setId(1);
		when(modelConfigMapper.findById(1)).thenReturn(config);

		assertNotNull(service.findById(1));
	}

	@Test
	void switchActiveStatus_deactivatesOthersAndActivatesCurrent() {
		ModelConfig config = new ModelConfig();
		config.setId(1);
		when(modelConfigMapper.findById(1)).thenReturn(config);

		service.switchActiveStatus(1, ModelType.CHAT);

		verify(modelConfigMapper).deactivateOthers(ModelType.CHAT.getCode(), 1);
		verify(modelConfigMapper).updateById(config);
		assertTrue(config.getIsActive());
	}

	@Test
	void switchActiveStatus_entityNotFound_noUpdate() {
		when(modelConfigMapper.findById(1)).thenReturn(null);

		service.switchActiveStatus(1, ModelType.CHAT);

		verify(modelConfigMapper).deactivateOthers(ModelType.CHAT.getCode(), 1);
		verify(modelConfigMapper, never()).updateById(any());
	}

	@Test
	void listConfigs_returnsDTOList() {
		ModelConfig config = new ModelConfig();
		config.setId(1);
		config.setModelName("gpt-4");
		config.setModelType(ModelType.CHAT);
		config.setBaseUrl("http://example.com");
		config.setApiKey("key");
		config.setProvider("openai");
		when(modelConfigMapper.findAll()).thenReturn(List.of(config));

		List<ModelConfigDTO> result = service.listConfigs();
		assertEquals(1, result.size());
	}

	@Test
	void addConfig_insertsEntity() {
		ModelConfigDTO dto = new ModelConfigDTO();
		dto.setModelName(" gpt-4 ");
		dto.setBaseUrl(" http://example.com ");
		dto.setApiKey(" key ");
		dto.setModelType("CHAT");

		service.addConfig(dto);

		verify(modelConfigMapper).insert(any(ModelConfig.class));
		assertEquals("gpt-4", dto.getModelName());
	}

	@Test
	void updateConfigInDb_configNotFound_throwsException() {
		ModelConfigDTO dto = new ModelConfigDTO();
		dto.setId(99);
		dto.setModelName("m");
		dto.setBaseUrl("u");
		dto.setApiKey("k");
		when(modelConfigMapper.findById(99)).thenReturn(null);

		assertThrows(RuntimeException.class, () -> service.updateConfigInDb(dto));
	}

	@Test
	void updateConfigInDb_typeChanged_throwsException() {
		ModelConfig existing = new ModelConfig();
		existing.setId(1);
		existing.setModelType(ModelType.CHAT);
		when(modelConfigMapper.findById(1)).thenReturn(existing);

		ModelConfigDTO dto = new ModelConfigDTO();
		dto.setId(1);
		dto.setModelType("EMBEDDING");
		dto.setModelName("m");
		dto.setBaseUrl("u");
		dto.setApiKey("k");

		assertThrows(RuntimeException.class, () -> service.updateConfigInDb(dto));
	}

	@Test
	void updateConfigInDb_success_returnsEntity() {
		ModelConfig existing = new ModelConfig();
		existing.setId(1);
		existing.setModelType(ModelType.CHAT);
		existing.setApiKey("old-key");
		when(modelConfigMapper.findById(1)).thenReturn(existing);

		ModelConfigDTO dto = new ModelConfigDTO();
		dto.setId(1);
		dto.setModelType("CHAT");
		dto.setModelName("new-model");
		dto.setBaseUrl("http://new.com");
		dto.setApiKey("new-key");
		dto.setProvider("openai");

		ModelConfig result = service.updateConfigInDb(dto);

		assertEquals("new-model", result.getModelName());
		assertEquals("new-key", result.getApiKey());
		verify(modelConfigMapper).updateById(existing);
	}

	@Test
	void updateConfigInDb_maskedApiKey_preservesOldKey() {
		ModelConfig existing = new ModelConfig();
		existing.setId(1);
		existing.setModelType(ModelType.CHAT);
		existing.setApiKey("real-secret-key");
		when(modelConfigMapper.findById(1)).thenReturn(existing);

		ModelConfigDTO dto = new ModelConfigDTO();
		dto.setId(1);
		dto.setModelType("CHAT");
		dto.setModelName("model");
		dto.setBaseUrl("http://example.com");
		dto.setApiKey("sk-****xxxx");
		dto.setProvider("openai");

		ModelConfig result = service.updateConfigInDb(dto);

		assertEquals("real-secret-key", result.getApiKey());
	}

	@Test
	void deleteConfig_notFound_throwsException() {
		when(modelConfigMapper.findById(99)).thenReturn(null);

		assertThrows(RuntimeException.class, () -> service.deleteConfig(99));
	}

	@Test
	void deleteConfig_isActive_throwsException() {
		ModelConfig config = new ModelConfig();
		config.setIsActive(true);
		when(modelConfigMapper.findById(1)).thenReturn(config);

		assertThrows(RuntimeException.class, () -> service.deleteConfig(1));
	}

	@Test
	void deleteConfig_success_softDeletes() {
		ModelConfig config = new ModelConfig();
		config.setIsActive(false);
		when(modelConfigMapper.findById(1)).thenReturn(config);
		when(modelConfigMapper.updateById(config)).thenReturn(1);

		service.deleteConfig(1);

		assertEquals(1, config.getIsDeleted());
		verify(modelConfigMapper).updateById(config);
	}

	@Test
	void getActiveConfigByType_returnsDTO() {
		ModelConfig config = new ModelConfig();
		config.setId(1);
		config.setModelName("gpt-4");
		config.setModelType(ModelType.CHAT);
		config.setBaseUrl("http://example.com");
		config.setApiKey("key");
		config.setProvider("openai");
		when(modelConfigMapper.selectActiveByType("CHAT")).thenReturn(config);

		ModelConfigDTO result = service.getActiveConfigByType(ModelType.CHAT);
		assertNotNull(result);
	}

	@Test
	void getActiveConfigByType_notFound_returnsNull() {
		when(modelConfigMapper.selectActiveByType("CHAT")).thenReturn(null);

		assertNull(service.getActiveConfigByType(ModelType.CHAT));
	}

}
