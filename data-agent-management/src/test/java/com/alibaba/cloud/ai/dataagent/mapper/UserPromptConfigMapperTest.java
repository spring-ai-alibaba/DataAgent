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
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.UserPromptConfig;
import com.alibaba.cloud.ai.dataagent.service.MySqlContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@MybatisTest
@TestPropertySource(
		properties = { "spring.sql.init.mode=never", "mybatis.configuration.map-underscore-to-camel-case=true" })
@ImportTestcontainers(MySqlContainerConfiguration.class)
@ImportAutoConfiguration(MySqlContainerConfiguration.class)
public class UserPromptConfigMapperTest {

	@Autowired
	private UserPromptConfigMapper userPromptConfigMapper;

	// ==================== UserPromptConfigMapper Tests ====================

	@Test
	public void testUserPromptConfigInsertAndSelectById() {
		// Given
		UserPromptConfig config = createTestUserPromptConfig();

		// When
		int insertResult = userPromptConfigMapper.insert(config);
		UserPromptConfig selected = userPromptConfigMapper.selectById(config.getId());

		// Then
		assertEquals(1, insertResult);
		assertNotNull(selected);
		assertEquals(config.getName(), selected.getName());
		assertEquals(config.getPromptType(), selected.getPromptType());
		assertEquals(config.getSystemPrompt(), selected.getSystemPrompt());
		assertEquals(config.getEnabled(), selected.getEnabled());
		assertEquals(config.getDescription(), selected.getDescription());
		assertEquals(config.getPriority(), selected.getPriority());
		assertEquals(config.getDisplayOrder(), selected.getDisplayOrder());
	}

	@Test
	public void testUserPromptConfigSelectByPromptType() {
		// Given
		UserPromptConfig config1 = createTestUserPromptConfig();
		config1.setPromptType("report-generator");
		config1.setAgentId(1L);
		UserPromptConfig config2 = createTestUserPromptConfig();
		config2.setId(UUID.randomUUID().toString());
		config2.setName("配置2");
		config2.setPromptType("report-generator");
		config2.setAgentId(1L);

		userPromptConfigMapper.insert(config1);
		userPromptConfigMapper.insert(config2);

		// When
		List<UserPromptConfig> configs = userPromptConfigMapper.selectByPromptType("report-generator", 1L);

		// Then
		assertTrue(configs.size() >= 2);
		assertTrue(configs.stream().allMatch(c -> "report-generator".equals(c.getPromptType())));
	}

	@Test
	public void testUserPromptConfigSelectActiveByPromptType() {
		// Given
		UserPromptConfig enabledConfig = createTestUserPromptConfig();
		enabledConfig.setPromptType("planner");
		enabledConfig.setEnabled(true);
		enabledConfig.setAgentId(2L);

		UserPromptConfig disabledConfig = createTestUserPromptConfig();
		disabledConfig.setId(UUID.randomUUID().toString());
		disabledConfig.setName("禁用配置");
		disabledConfig.setPromptType("planner");
		disabledConfig.setEnabled(false);
		disabledConfig.setAgentId(2L);

		userPromptConfigMapper.insert(enabledConfig);
		userPromptConfigMapper.insert(disabledConfig);

		// When
		UserPromptConfig activeConfig = userPromptConfigMapper.selectActiveByPromptType("planner", 2L);

		// Then
		assertNotNull(activeConfig);
		assertTrue(activeConfig.getEnabled());
		assertEquals("planner", activeConfig.getPromptType());
	}

	@Test
	public void testUserPromptConfigEnableAndDisable() {
		// Given
		UserPromptConfig config = createTestUserPromptConfig();
		config.setEnabled(false);
		userPromptConfigMapper.insert(config);

		// When - 启用配置
		int enableResult = userPromptConfigMapper.enableById(config.getId());
		UserPromptConfig enabledConfig = userPromptConfigMapper.selectById(config.getId());

		// Then
		assertEquals(1, enableResult);
		assertTrue(enabledConfig.getEnabled());

		// When - 禁用配置
		int disableResult = userPromptConfigMapper.disableById(config.getId());
		UserPromptConfig disabledConfig = userPromptConfigMapper.selectById(config.getId());

		// Then
		assertEquals(1, disableResult);
		assertFalse(disabledConfig.getEnabled());
	}

	@Test
	public void testUserPromptConfigDisableAllByPromptType() {
		// Given
		UserPromptConfig config1 = createTestUserPromptConfig();
		config1.setPromptType("test-type");
		config1.setEnabled(true);
		config1.setAgentId(3L);

		UserPromptConfig config2 = createTestUserPromptConfig();
		config2.setId(UUID.randomUUID().toString());
		config2.setName("配置2");
		config2.setPromptType("test-type");
		config2.setEnabled(true);
		config2.setAgentId(3L);

		userPromptConfigMapper.insert(config1);
		userPromptConfigMapper.insert(config2);

		// When
		int disableResult = userPromptConfigMapper.disableAllByPromptType("test-type", 3L);

		// Then
		assertEquals(2, disableResult);

		List<UserPromptConfig> configs = userPromptConfigMapper.selectByPromptType("test-type", 3L);
		assertTrue(configs.stream().allMatch(c -> !c.getEnabled()));
	}

	@Test
	public void testUserPromptConfigUpdateById() {
		// Given
		UserPromptConfig config = createTestUserPromptConfig();
		userPromptConfigMapper.insert(config);

		// When
		config.setName("更新后的配置");
		config.setDescription("更新后的描述");
		config.setPriority(5);
		int updateResult = userPromptConfigMapper.updateById(config);
		UserPromptConfig updated = userPromptConfigMapper.selectById(config.getId());

		// Then
		assertEquals(1, updateResult);
		assertEquals("更新后的配置", updated.getName());
		assertEquals("更新后的描述", updated.getDescription());
		assertEquals(5, updated.getPriority());
	}

	@Test
	public void testUserPromptConfigGetActiveConfigsByType() {
		// Given
		UserPromptConfig activeConfig = createTestUserPromptConfig();
		activeConfig.setPromptType("test-type");
		activeConfig.setEnabled(true);
		activeConfig.setPriority(2);
		activeConfig.setAgentId(4L);

		UserPromptConfig inactiveConfig = createTestUserPromptConfig();
		inactiveConfig.setId(UUID.randomUUID().toString());
		inactiveConfig.setName("禁用配置");
		inactiveConfig.setPromptType("test-type");
		inactiveConfig.setEnabled(false);
		inactiveConfig.setPriority(1);
		inactiveConfig.setAgentId(4L);

		userPromptConfigMapper.insert(activeConfig);
		userPromptConfigMapper.insert(inactiveConfig);

		// When
		List<UserPromptConfig> activeConfigs = userPromptConfigMapper.getActiveConfigsByType("test-type", 4L);

		// Then
		assertTrue(activeConfigs.size() >= 1);
		assertTrue(activeConfigs.stream().allMatch(c -> c.getEnabled()));
		assertTrue(activeConfigs.stream().allMatch(c -> "test-type".equals(c.getPromptType())));
	}

	@Test
	public void testUserPromptConfigSelectAll() {
		// Given
		UserPromptConfig config1 = createTestUserPromptConfig();
		UserPromptConfig config2 = createTestUserPromptConfig();
		config2.setId(UUID.randomUUID().toString());
		config2.setName("配置2");

		userPromptConfigMapper.insert(config1);
		userPromptConfigMapper.insert(config2);

		// When
		List<UserPromptConfig> allConfigs = userPromptConfigMapper.selectAll();

		// Then
		assertTrue(allConfigs.size() >= 2);
		assertTrue(allConfigs.stream().anyMatch(c -> c.getName().equals(config1.getName())));
		assertTrue(allConfigs.stream().anyMatch(c -> c.getName().equals(config2.getName())));
	}

	@Test
	public void testUserPromptConfigDeleteById() {
		// Given
		UserPromptConfig config = createTestUserPromptConfig();
		userPromptConfigMapper.insert(config);

		// When
		int deleteResult = userPromptConfigMapper.deleteById(config.getId());
		UserPromptConfig deleted = userPromptConfigMapper.selectById(config.getId());

		// Then
		assertEquals(1, deleteResult);
		assertNull(deleted);
	}

	// ==================== Helper Methods ====================
	private UserPromptConfig createTestUserPromptConfig() {
		return UserPromptConfig.builder()
			.id(UUID.randomUUID().toString())
			.name("测试Prompt配置")
			.promptType("report-generator")
			.systemPrompt("你是一个专业的数据分析师...")
			.enabled(true)
			.description("测试用Prompt配置")
			.priority(1)
			.displayOrder(1)
			.createTime(LocalDateTime.now())
			.updateTime(LocalDateTime.now())
			.creator("test_user")
			.build();
	}

}
