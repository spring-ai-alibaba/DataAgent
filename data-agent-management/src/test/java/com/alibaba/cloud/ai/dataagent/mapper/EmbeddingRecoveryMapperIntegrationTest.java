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

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
class EmbeddingRecoveryMapperIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AgentKnowledgeMapper agentKnowledgeMapper;

	@Autowired
	private BusinessKnowledgeMapper businessKnowledgeMapper;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("DROP TABLE IF EXISTS agent_knowledge");
		jdbcTemplate.execute("DROP TABLE IF EXISTS business_knowledge");
		jdbcTemplate.execute("""
				CREATE TABLE agent_knowledge (
				  id INT PRIMARY KEY,
				  embedding_status VARCHAR(20),
				  error_msg VARCHAR(255),
				  updated_time TIMESTAMP,
				  is_deleted INT
				)
				""");
		jdbcTemplate.execute("""
				CREATE TABLE business_knowledge (
				  id INT PRIMARY KEY,
				  embedding_status VARCHAR(20),
				  error_msg VARCHAR(255),
				  updated_time TIMESTAMP,
				  is_deleted INT
				)
				""");
	}

	@Test
	void staleProcessingRowsAreRecoveredButRecentAndDeletedRowsAreNot() {
		LocalDateTime now = LocalDateTime.now();
		insertRecoveryFixtures("agent_knowledge", now);
		insertRecoveryFixtures("business_knowledge", now);

		assertThat(agentKnowledgeMapper.resetStaleProcessing(now.minusMinutes(10))).isEqualTo(1);
		assertThat(businessKnowledgeMapper.resetStaleProcessing(now.minusMinutes(10))).isEqualTo(1);
		assertRecoveryResult("agent_knowledge");
		assertRecoveryResult("business_knowledge");
	}

	private void insertRecoveryFixtures(String table, LocalDateTime now) {
		jdbcTemplate.update(
				"INSERT INTO " + table
						+ " (id, embedding_status, updated_time, is_deleted) VALUES (?, 'PROCESSING', ?, 0)",
				1, now.minusMinutes(20));
		jdbcTemplate.update(
				"INSERT INTO " + table
						+ " (id, embedding_status, updated_time, is_deleted) VALUES (?, 'PROCESSING', ?, 0)",
				2, now.minusMinutes(2));
		jdbcTemplate.update(
				"INSERT INTO " + table
						+ " (id, embedding_status, updated_time, is_deleted) VALUES (?, 'PROCESSING', ?, 1)",
				3, now.minusMinutes(20));
	}

	private void assertRecoveryResult(String table) {
		assertThat(jdbcTemplate.queryForObject("SELECT embedding_status FROM " + table + " WHERE id = 1", String.class))
			.isEqualTo("PENDING");
		assertThat(jdbcTemplate.queryForObject("SELECT error_msg FROM " + table + " WHERE id = 1", String.class))
			.isEqualTo("Recovered stale PROCESSING job");
		assertThat(jdbcTemplate.queryForObject("SELECT embedding_status FROM " + table + " WHERE id = 2", String.class))
			.isEqualTo("PROCESSING");
		assertThat(jdbcTemplate.queryForObject("SELECT embedding_status FROM " + table + " WHERE id = 3", String.class))
			.isEqualTo("PROCESSING");
	}

}
