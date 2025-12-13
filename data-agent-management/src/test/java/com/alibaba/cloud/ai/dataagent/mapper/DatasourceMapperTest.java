/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.MySqlContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@MybatisTest
@TestPropertySource(
		properties = { "spring.sql.init.mode=never", "mybatis.configuration.map-underscore-to-camel-case=true" })
@ImportTestcontainers(MySqlContainerConfiguration.class)
@ImportAutoConfiguration(MySqlContainerConfiguration.class)
public class DatasourceMapperTest {

	@Autowired
	private DatasourceMapper datasourceMapper;

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

}
