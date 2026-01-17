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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.MySqlContainerConfiguration;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.TestPropertySource;

@MybatisTest
@TestPropertySource(
		properties = { "spring.sql.init.mode=never", "mybatis.configuration.map-underscore-to-camel-case=true" })
@ImportTestcontainers(MySqlContainerConfiguration.class)
@ImportAutoConfiguration(MySqlContainerConfiguration.class)
public class AgentDatasourceMapperTest {

	@Autowired
	private DatasourceMapper datasourceMapper;

	@Autowired
	private AgentDatasourceMapper agentDatasourceMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long agentId;

	@BeforeEach
	public void setUpAgent() {
		this.agentId = createTestAgentAndGetId();
	}

	// ==================== AgentDatasourceMapper Tests ====================

	@Test
	public void testAgentDatasourceCreateNewRelationEnabled() {
		// Given
		Long agentId = this.agentId;
		Integer datasourceId = createTestDatasourceAndGetId();

		// When
		int insertResult = agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId);
		AgentDatasource relation = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);

		// Then
		assertEquals(1, insertResult);
		assertNotNull(relation);
		assertEquals(agentId, relation.getAgentId());
		assertEquals(datasourceId, relation.getDatasourceId());
		assertEquals(Integer.valueOf(1), relation.getIsActive());
	}

	@Test
	public void testAgentDatasourceSelectByAgentId() {
		// Given
		Long agentId = this.agentId;
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
		Long agentId = this.agentId;
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
		Long agentId = this.agentId;
		Integer datasourceId = createTestDatasourceAndGetId();
		agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId);

		// When - 禁用关联
		int disableResult = agentDatasourceMapper.updateRelation(agentId, datasourceId, 0);
		AgentDatasource disabledRelation = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);

		// Then
		assertEquals(1, disableResult);
		assertEquals(Integer.valueOf(0), disabledRelation.getIsActive());

		// When - 启用关联
		int enableResult = agentDatasourceMapper.enableRelation(agentId, datasourceId);
		AgentDatasource enabledRelation = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);

		// Then
		assertEquals(1, enableResult);
		assertEquals(Integer.valueOf(1), enabledRelation.getIsActive());
	}

	@Test
	public void testAgentDatasourceDisableAllByAgentId() {
		// Given
		Long agentId = this.agentId;
		Integer datasourceId1 = createTestDatasourceAndGetId();
		Integer datasourceId2 = createTestDatasourceAndGetId();

		agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId1);
		agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId2);

		// When
		int disableResult = agentDatasourceMapper.disableAllByAgentId(agentId);

		// Then
		assertEquals(2, disableResult);

		List<AgentDatasource> relations = agentDatasourceMapper.selectByAgentId(agentId);
		assertTrue(relations.stream().allMatch(r -> r.getIsActive() != null && r.getIsActive() == 0));
	}

	@Test
	public void testAgentDatasourceCountActiveByAgentIdExcluding() {
		// Given
		Long agentId = this.agentId;
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
		Long agentId = this.agentId;
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
		Long agentId1 = createTestAgentAndGetId();
		Long agentId2 = createTestAgentAndGetId();
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

	private Integer createTestDatasourceAndGetId() {
		Datasource datasource = createTestDatasource();
		datasourceMapper.insert(datasource);
		return datasource.getId();
	}

	private Long createTestAgentAndGetId() {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(con -> {
			var ps = con.prepareStatement(
					"INSERT INTO agent (name, description, status, category) VALUES (?, ?, 'published', 'test')",
					Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, "测试智能体" + UUID.randomUUID());
			ps.setString(2, "用于单元测试的临时智能体");
			return ps;
		}, keyHolder);
		Number key = keyHolder.getKey();
		Long id = key == null ? null : key.longValue();
		// 立即校验插入是否可见且存在，避免外键引用失败
		if (id == null) {
			throw new IllegalStateException("创建测试Agent失败：未获取到自增ID");
		}
		Long exists = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM agent WHERE id = ?", Long.class, id);
		if (exists == null || exists == 0) {
			throw new IllegalStateException("创建测试Agent失败：数据库中不存在id=" + id);
		}
		return id;
	}

}
