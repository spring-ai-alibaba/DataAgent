/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.mapper;

import com.alibaba.cloud.ai.MySqlContainerConfiguration;
import com.alibaba.cloud.ai.entity.AgentDatasource;
import com.alibaba.cloud.ai.entity.Datasource;
import com.alibaba.cloud.ai.entity.UserPromptConfig;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@MybatisTest
@TestPropertySource(properties = { "spring.sql.init.mode=never" })
@ImportTestcontainers(MySqlContainerConfiguration.class)
@ImportAutoConfiguration(MySqlContainerConfiguration.class)
@Transactional
public class MappersTest {

	@Autowired
	private DatasourceMapper datasourceMapper;

	@Autowired
	private UserPromptConfigMapper userPromptConfigMapper;

	@Autowired
	private AgentDatasourceMapper agentDatasourceMapper;

	// ==================== DatasourceMapper Tests ====================

	@Test
	public void testDatasourceInsertAndSelectById() {
		// Given
		Datasource datasource = createTestDatasource();

		// When
		int insertResult = datasourceMapper.insert(datasource);
		Datasource selected = datasourceMapper.selectById(datasource.getId());

		// Then
		assertEquals(1, insertResult);
		assertNotNull(selected);
		assertEquals(datasource.getName(), selected.getName());
		assertEquals(datasource.getType(), selected.getType());
		assertEquals(datasource.getHost(), selected.getHost());
		assertEquals(datasource.getPort(), selected.getPort());
		assertEquals(datasource.getDatabaseName(), selected.getDatabaseName());
		assertEquals(datasource.getStatus(), selected.getStatus());
		assertEquals(datasource.getTestStatus(), selected.getTestStatus());
	}

	@Test
	public void testDatasourceSelectAll() {
		// Given
		Datasource datasource1 = createTestDatasource();
		Datasource datasource2 = createTestDatasource();
		datasource2.setName("测试数据源2");
		datasource2.setType("postgresql");

		datasourceMapper.insert(datasource1);
		datasourceMapper.insert(datasource2);

		// When
		List<Datasource> allDatasources = datasourceMapper.selectAll();

		// Then
		assertTrue(allDatasources.size() >= 2);
		assertTrue(allDatasources.stream().anyMatch(d -> d.getName().equals(datasource1.getName())));
		assertTrue(allDatasources.stream().anyMatch(d -> d.getName().equals(datasource2.getName())));
	}

	@Test
	public void testDatasourceUpdateById() {
		// Given
		Datasource datasource = createTestDatasource();
		datasourceMapper.insert(datasource);

		// When
		datasource.setName("更新后的数据源");
		datasource.setDescription("更新后的描述");
		int updateResult = datasourceMapper.updateById(datasource);
		Datasource updated = datasourceMapper.selectById(datasource.getId());

		// Then
		assertEquals(1, updateResult);
		assertEquals("更新后的数据源", updated.getName());
		assertEquals("更新后的描述", updated.getDescription());
	}

	@Test
	public void testDatasourceUpdateTestStatusById() {
		// Given
		Datasource datasource = createTestDatasource();
		datasourceMapper.insert(datasource);

		// When
		int updateResult = datasourceMapper.updateTestStatusById(datasource.getId(), "failed");
		Datasource updated = datasourceMapper.selectById(datasource.getId());

		// Then
		assertEquals(1, updateResult);
		assertEquals("failed", updated.getTestStatus());
	}

	@Test
	public void testDatasourceSelectByStatus() {
		// Given
		Datasource activeDatasource = createTestDatasource();
		activeDatasource.setStatus("active");
		Datasource inactiveDatasource = createTestDatasource();
		inactiveDatasource.setName("禁用数据源");
		inactiveDatasource.setStatus("inactive");

		datasourceMapper.insert(activeDatasource);
		datasourceMapper.insert(inactiveDatasource);

		// When
		List<Datasource> activeDatasources = datasourceMapper.selectByStatus("active");
		List<Datasource> inactiveDatasources = datasourceMapper.selectByStatus("inactive");

		// Then
		assertTrue(activeDatasources.size() >= 1);
		assertTrue(inactiveDatasources.size() >= 1);
		assertTrue(activeDatasources.stream().allMatch(d -> "active".equals(d.getStatus())));
		assertTrue(inactiveDatasources.stream().allMatch(d -> "inactive".equals(d.getStatus())));
	}

	@Test
	public void testDatasourceSelectByType() {
		// Given
		Datasource mysqlDatasource = createTestDatasource();
		mysqlDatasource.setType("mysql");
		Datasource postgresDatasource = createTestDatasource();
		postgresDatasource.setName("PostgreSQL数据源");
		postgresDatasource.setType("postgresql");

		datasourceMapper.insert(mysqlDatasource);
		datasourceMapper.insert(postgresDatasource);

		// When
		List<Datasource> mysqlDatasources = datasourceMapper.selectByType("mysql");
		List<Datasource> postgresDatasources = datasourceMapper.selectByType("postgresql");

		// Then
		assertTrue(mysqlDatasources.size() >= 1);
		assertTrue(postgresDatasources.size() >= 1);
		assertTrue(mysqlDatasources.stream().allMatch(d -> "mysql".equals(d.getType())));
		assertTrue(postgresDatasources.stream().allMatch(d -> "postgresql".equals(d.getType())));
	}

	@Test
	public void testDatasourceStatistics() {
		// Given
		Datasource datasource1 = createTestDatasource();
		datasource1.setStatus("active");
		datasource1.setType("mysql");
		datasource1.setTestStatus("success");

		Datasource datasource2 = createTestDatasource();
		datasource2.setName("数据源2");
		datasource2.setStatus("inactive");
		datasource2.setType("postgresql");
		datasource2.setTestStatus("failed");

		datasourceMapper.insert(datasource1);
		datasourceMapper.insert(datasource2);

		// When
		List<Map<String, Object>> statusStats = datasourceMapper.selectStatusStats();
		List<Map<String, Object>> typeStats = datasourceMapper.selectTypeStats();
		List<Map<String, Object>> testStatusStats = datasourceMapper.selectTestStatusStats();
		Long totalCount = datasourceMapper.selectCount();

		// Then
		assertNotNull(statusStats);
		assertNotNull(typeStats);
		assertNotNull(testStatusStats);
		assertTrue(totalCount >= 2);

		// 验证统计结果包含预期数据
		assertTrue(statusStats.stream().anyMatch(stat -> "active".equals(stat.get("status"))));
		assertTrue(typeStats.stream().anyMatch(stat -> "mysql".equals(stat.get("type"))));
		assertTrue(testStatusStats.stream().anyMatch(stat -> "success".equals(stat.get("test_status"))));
	}

	@Test
	public void testDatasourceDeleteById() {
		// Given
		Datasource datasource = createTestDatasource();
		datasourceMapper.insert(datasource);

		// When
		int deleteResult = datasourceMapper.deleteById(datasource.getId());
		Datasource deleted = datasourceMapper.selectById(datasource.getId());

		// Then
		assertEquals(1, deleteResult);
		assertNull(deleted);
	}

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
		UserPromptConfig config2 = createTestUserPromptConfig();
		config2.setId(UUID.randomUUID().toString());
		config2.setName("配置2");
		config2.setPromptType("report-generator");

		userPromptConfigMapper.insert(config1);
		userPromptConfigMapper.insert(config2);

		// When
		List<UserPromptConfig> configs = userPromptConfigMapper.selectByPromptType("report-generator");

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

		UserPromptConfig disabledConfig = createTestUserPromptConfig();
		disabledConfig.setId(UUID.randomUUID().toString());
		disabledConfig.setName("禁用配置");
		disabledConfig.setPromptType("planner");
		disabledConfig.setEnabled(false);

		userPromptConfigMapper.insert(enabledConfig);
		userPromptConfigMapper.insert(disabledConfig);

		// When
		UserPromptConfig activeConfig = userPromptConfigMapper.selectActiveByPromptType("planner");

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

		UserPromptConfig config2 = createTestUserPromptConfig();
		config2.setId(UUID.randomUUID().toString());
		config2.setName("配置2");
		config2.setPromptType("test-type");
		config2.setEnabled(true);

		userPromptConfigMapper.insert(config1);
		userPromptConfigMapper.insert(config2);

		// When
		int disableResult = userPromptConfigMapper.disableAllByPromptType("test-type");

		// Then
		assertEquals(2, disableResult);

		List<UserPromptConfig> configs = userPromptConfigMapper.selectByPromptType("test-type");
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

		UserPromptConfig inactiveConfig = createTestUserPromptConfig();
		inactiveConfig.setId(UUID.randomUUID().toString());
		inactiveConfig.setName("禁用配置");
		inactiveConfig.setPromptType("test-type");
		inactiveConfig.setEnabled(false);
		inactiveConfig.setPriority(1);

		userPromptConfigMapper.insert(activeConfig);
		userPromptConfigMapper.insert(inactiveConfig);

		// When
		List<UserPromptConfig> activeConfigs = userPromptConfigMapper.getActiveConfigsByType("test-type");

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

	// ==================== AgentDatasourceMapper Tests ====================

	@Test
	public void testAgentDatasourceCreateNewRelationEnabled() {
		// Given
		Integer agentId = 1;
		Integer datasourceId = createTestDatasourceAndGetId();

		// When
		int insertResult = agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId);
		AgentDatasource relation = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);

		// Then
		assertEquals(1, insertResult);
		assertNotNull(relation);
		assertEquals(agentId, relation.getAgentId());
		assertEquals(datasourceId, relation.getDatasourceId());
		assertEquals(1, relation.getIsActive());
	}

	@Test
	public void testAgentDatasourceSelectByAgentId() {
		// Given
		Integer agentId = 1;
		Integer datasourceId1 = createTestDatasourceAndGetId();
		Integer datasourceId2 = createTestDatasourceAndGetId();

		agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId1);
		agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId2);

		// When
		List<AgentDatasource> relations = agentDatasourceMapper.selectByAgentId(agentId);

		// Then
		assertEquals(2, relations.size());
		assertTrue(relations.stream().allMatch(r -> agentId.equals(r.getAgentId())));
		assertTrue(relations.stream().anyMatch(r -> datasourceId1.equals(r.getDatasourceId())));
		assertTrue(relations.stream().anyMatch(r -> datasourceId2.equals(r.getDatasourceId())));
	}

	@Test
	public void testAgentDatasourceSelectByAgentIdAndDatasourceId() {
		// Given
		Integer agentId = 1;
		Integer datasourceId = createTestDatasourceAndGetId();
		agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId);

		// When
		AgentDatasource relation = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);

		// Then
		assertNotNull(relation);
		assertEquals(agentId, relation.getAgentId());
		assertEquals(datasourceId, relation.getDatasourceId());
	}

	@Test
	public void testAgentDatasourceUpdateRelation() {
		// Given
		Integer agentId = 1;
		Integer datasourceId = createTestDatasourceAndGetId();
		agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId);

		// When - 禁用关联
		int disableResult = agentDatasourceMapper.updateRelation(agentId, datasourceId, 0);
		AgentDatasource disabledRelation = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);

		// Then
		assertEquals(1, disableResult);
		assertEquals(0, disabledRelation.getIsActive());

		// When - 启用关联
		int enableResult = agentDatasourceMapper.enableRelation(agentId, datasourceId);
		AgentDatasource enabledRelation = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);

		// Then
		assertEquals(1, enableResult);
		assertEquals(1, enabledRelation.getIsActive());
	}

	@Test
	public void testAgentDatasourceDisableAllByAgentId() {
		// Given
		Integer agentId = 1;
		Integer datasourceId1 = createTestDatasourceAndGetId();
		Integer datasourceId2 = createTestDatasourceAndGetId();

		agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId1);
		agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId2);

		// When
		int disableResult = agentDatasourceMapper.disableAllByAgentId(agentId);

		// Then
		assertEquals(2, disableResult);

		List<AgentDatasource> relations = agentDatasourceMapper.selectByAgentId(agentId);
		assertTrue(relations.stream().allMatch(r -> r.getIsActive() == 0));
	}

	@Test
	public void testAgentDatasourceCountActiveByAgentIdExcluding() {
		// Given
		Integer agentId = 1;
		Integer datasourceId1 = createTestDatasourceAndGetId();
		Integer datasourceId2 = createTestDatasourceAndGetId();
		Integer datasourceId3 = createTestDatasourceAndGetId();

		agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId1);
		agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId2);
		agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId3);

		// When
		int count = agentDatasourceMapper.countActiveByAgentIdExcluding(agentId, datasourceId1);

		// Then
		assertEquals(2, count);
	}

	@Test
	public void testAgentDatasourceRemoveRelation() {
		// Given
		Integer agentId = 1;
		Integer datasourceId = createTestDatasourceAndGetId();
		agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId);

		// When
		int removeResult = agentDatasourceMapper.removeRelation(agentId, datasourceId);
		AgentDatasource removed = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);

		// Then
		assertEquals(1, removeResult);
		assertNull(removed);
	}

	@Test
	public void testAgentDatasourceDeleteAllByDatasourceId() {
		// Given
		Integer agentId1 = 1;
		Integer agentId2 = 2;
		Integer datasourceId = createTestDatasourceAndGetId();

		agentDatasourceMapper.createNewRelationEnabled(agentId1, datasourceId);
		agentDatasourceMapper.createNewRelationEnabled(agentId2, datasourceId);

		// When
		int deleteResult = agentDatasourceMapper.deleteAllByDatasourceId(datasourceId);

		// Then
		assertEquals(2, deleteResult);

		AgentDatasource relation1 = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId1, datasourceId);
		AgentDatasource relation2 = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId2, datasourceId);
		assertNull(relation1);
		assertNull(relation2);
	}

	// ==================== Helper Methods ====================

	private Datasource createTestDatasource() {
		return Datasource.builder()
			.name("测试数据源")
			.type("mysql")
			.host("localhost")
			.port(3306)
			.databaseName("test_db")
			.username("test_user")
			.password("test_password")
			.status("active")
			.testStatus("success")
			.description("测试用数据源")
			.creatorId(1L)
			.createTime(LocalDateTime.now())
			.updateTime(LocalDateTime.now())
			.build();
	}

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

	private Integer createTestDatasourceAndGetId() {
		Datasource datasource = createTestDatasource();
		datasourceMapper.insert(datasource);
		return datasource.getId();
	}

}
