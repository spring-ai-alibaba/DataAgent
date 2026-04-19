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
package com.alibaba.cloud.ai.dataagent.service.prompt;

import com.alibaba.cloud.ai.dataagent.dto.prompt.PromptConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.UserPromptConfig;
import com.alibaba.cloud.ai.dataagent.mapper.UserPromptConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPromptServiceImplTest {

	private UserPromptServiceImpl userPromptService;

	@Mock
	private UserPromptConfigMapper userPromptConfigMapper;

	@BeforeEach
	void setUp() {
		userPromptService = new UserPromptServiceImpl(userPromptConfigMapper);
	}

	@Test
	void saveOrUpdateConfig_newConfig_createsWithGeneratedId() {
		PromptConfigDTO dto = new PromptConfigDTO(null, "test", "optimization", 1L, "prompt", true, "desc", "creator",
				1, 0);

		UserPromptConfig result = userPromptService.saveOrUpdateConfig(dto);

		assertNotNull(result.getId());
		assertEquals("test", result.getName());
		verify(userPromptConfigMapper).insert(any(UserPromptConfig.class));
		verify(userPromptConfigMapper).enableById(anyString());
	}

	@Test
	void saveOrUpdateConfig_existingId_updatesConfig() {
		UserPromptConfig existing = new UserPromptConfig();
		existing.setId("existing-id");
		existing.setName("old");
		when(userPromptConfigMapper.selectById("existing-id")).thenReturn(existing);

		PromptConfigDTO dto = new PromptConfigDTO("existing-id", "updated", "optimization", 1L, "prompt", false, "desc",
				"creator", 1, 0);

		UserPromptConfig result = userPromptService.saveOrUpdateConfig(dto);

		assertEquals("updated", result.getName());
		verify(userPromptConfigMapper).updateById(any(UserPromptConfig.class));
	}

	@Test
	void saveOrUpdateConfig_nonExistentId_createsNew() {
		when(userPromptConfigMapper.selectById("non-existent")).thenReturn(null);

		PromptConfigDTO dto = new PromptConfigDTO("non-existent", "new", "optimization", 1L, "prompt", false,
				"desc", "creator", null, null);

		UserPromptConfig result = userPromptService.saveOrUpdateConfig(dto);

		assertEquals("non-existent", result.getId());
		verify(userPromptConfigMapper).insert(any(UserPromptConfig.class));
	}

	@Test
	void getConfigById_returnsConfig() {
		UserPromptConfig config = new UserPromptConfig();
		config.setId("test-id");
		when(userPromptConfigMapper.selectById("test-id")).thenReturn(config);

		assertEquals("test-id", userPromptService.getConfigById("test-id").getId());
	}

	@Test
	void getActiveConfigsByType_returnsList() {
		when(userPromptConfigMapper.getActiveConfigsByType("opt", 1L)).thenReturn(List.of(new UserPromptConfig()));

		List<UserPromptConfig> result = userPromptService.getActiveConfigsByType("opt", 1L);
		assertEquals(1, result.size());
	}

	@Test
	void getAllConfigs_returnsList() {
		when(userPromptConfigMapper.selectAll()).thenReturn(List.of(new UserPromptConfig()));

		assertEquals(1, userPromptService.getAllConfigs().size());
	}

	@Test
	void deleteConfig_existsAndDeleted_returnsTrue() {
		UserPromptConfig config = new UserPromptConfig();
		when(userPromptConfigMapper.selectById("id")).thenReturn(config);
		when(userPromptConfigMapper.deleteById("id")).thenReturn(1);

		assertTrue(userPromptService.deleteConfig("id"));
	}

	@Test
	void deleteConfig_notFound_returnsFalse() {
		when(userPromptConfigMapper.selectById("id")).thenReturn(null);

		assertFalse(userPromptService.deleteConfig("id"));
	}

	@Test
	void enableConfig_existsAndEnabled_returnsTrue() {
		when(userPromptConfigMapper.selectById("id")).thenReturn(new UserPromptConfig());
		when(userPromptConfigMapper.enableById("id")).thenReturn(1);

		assertTrue(userPromptService.enableConfig("id"));
	}

	@Test
	void enableConfig_notFound_returnsFalse() {
		when(userPromptConfigMapper.selectById("id")).thenReturn(null);

		assertFalse(userPromptService.enableConfig("id"));
	}

	@Test
	void disableConfig_success_returnsTrue() {
		when(userPromptConfigMapper.disableById("id")).thenReturn(1);

		assertTrue(userPromptService.disableConfig("id"));
	}

	@Test
	void disableConfig_failure_returnsFalse() {
		when(userPromptConfigMapper.disableById("id")).thenReturn(0);

		assertFalse(userPromptService.disableConfig("id"));
	}

	@Test
	void enableConfigs_batchEnable_returnsTrue() {
		assertTrue(userPromptService.enableConfigs(List.of("id1", "id2")));
		verify(userPromptConfigMapper, times(2)).enableById(anyString());
	}

	@Test
	void disableConfigs_batchDisable_returnsTrue() {
		assertTrue(userPromptService.disableConfigs(List.of("id1", "id2")));
		verify(userPromptConfigMapper, times(2)).disableById(anyString());
	}

	@Test
	void updatePriority_exists_returnsTrue() {
		UserPromptConfig config = new UserPromptConfig();
		when(userPromptConfigMapper.selectById("id")).thenReturn(config);

		assertTrue(userPromptService.updatePriority("id", 5));
		assertEquals(5, config.getPriority());
		verify(userPromptConfigMapper).updateById(config);
	}

	@Test
	void updatePriority_notFound_returnsFalse() {
		when(userPromptConfigMapper.selectById("id")).thenReturn(null);

		assertFalse(userPromptService.updatePriority("id", 5));
	}

	@Test
	void updateDisplayOrder_exists_returnsTrue() {
		UserPromptConfig config = new UserPromptConfig();
		when(userPromptConfigMapper.selectById("id")).thenReturn(config);

		assertTrue(userPromptService.updateDisplayOrder("id", 10));
		assertEquals(10, config.getDisplayOrder());
	}

	@Test
	void updateDisplayOrder_notFound_returnsFalse() {
		when(userPromptConfigMapper.selectById("id")).thenReturn(null);

		assertFalse(userPromptService.updateDisplayOrder("id", 10));
	}

}
