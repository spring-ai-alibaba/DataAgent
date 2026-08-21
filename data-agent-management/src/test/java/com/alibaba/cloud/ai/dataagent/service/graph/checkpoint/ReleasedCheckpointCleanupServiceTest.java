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
package com.alibaba.cloud.ai.dataagent.service.graph.checkpoint;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleasedCheckpointCleanupServiceTest {

	private JdbcTemplate jdbcTemplate;

	private DataAgentProperties properties;

	private ReleasedCheckpointCleanupService service;

	@BeforeEach
	void setUp() {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:checkpoint-cleanup-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
		jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.execute("""
				CREATE TABLE GRAPH_THREAD (
				  thread_id VARCHAR(36) PRIMARY KEY,
				  thread_name VARCHAR(255),
				  is_released BOOLEAN NOT NULL
				)
				""");
		jdbcTemplate.execute("""
				CREATE TABLE GRAPH_CHECKPOINT (
				  checkpoint_id VARCHAR(36) PRIMARY KEY,
				  thread_id VARCHAR(36) NOT NULL,
				  saved_at TIMESTAMP NOT NULL,
				  FOREIGN KEY (thread_id) REFERENCES GRAPH_THREAD(thread_id) ON DELETE CASCADE
				)
				""");
		properties = new DataAgentProperties();
		service = new ReleasedCheckpointCleanupService(jdbcTemplate, properties);
	}

	@Test
	void purgeDeletesEveryGenerationOfTheRetainedLogicalThreadAndCascadesCheckpoints() {
		insertThread("released-row", "released-run", true);
		insertThread("racing-active-row", "released-run", false);
		insertThread("other-released-row", "other-released-run", true);
		insertThread("active-row", "active-run", false);

		service.purgeReleased("released-run");

		assertEquals(0, countThreads("released-row"));
		assertEquals(0, countCheckpoints("released-row"));
		assertEquals(0, countThreads("racing-active-row"));
		assertEquals(0, countCheckpoints("racing-active-row"));
		assertEquals(1, countThreads("other-released-row"));
		assertEquals(1, countThreads("active-row"));
	}

	@Test
	void legacyReleaseConflictDropsOnlyTheOlderReleasedGeneration() {
		insertThread("released-row", "racing-run", true);
		insertThread("racing-active-row", "racing-run", false);

		assertTrue(service.reconcileLegacyReleaseConflict("racing-run"));

		assertEquals(0, countThreads("released-row"));
		assertEquals(0, countCheckpoints("released-row"));
		assertEquals(1, countThreads("racing-active-row"));
		assertEquals(1, countCheckpoints("racing-active-row"));
	}

	@Test
	void normalReleasedGenerationIsRetainedWhenNoActiveGenerationExists() {
		insertThread("released-row", "released-run", true);

		assertFalse(service.reconcileLegacyReleaseConflict("released-run"));

		assertEquals(1, countThreads("released-row"));
		assertEquals(1, countCheckpoints("released-row"));
	}

	@Test
	void memoryCheckpointModeDoesNotTouchFrameworkDatabaseTables() {
		properties.getCheckpoint().setType("memory");
		insertThread("released-row", "released-run", true);

		service.purgeReleased("released-run");
		assertFalse(service.reconcileLegacyReleaseConflict("released-run"));

		assertEquals(1, countThreads("released-row"));
	}

	private void insertThread(String id, String threadName, boolean released) {
		jdbcTemplate.update("INSERT INTO GRAPH_THREAD(thread_id, thread_name, is_released) VALUES (?, ?, ?)", id,
				threadName, released);
		jdbcTemplate.update(
				"INSERT INTO GRAPH_CHECKPOINT(checkpoint_id, thread_id, saved_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
				"cp-" + id, id);
	}

	private int countThreads(String threadId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM GRAPH_THREAD WHERE thread_id = ?", Integer.class,
				threadId);
	}

	private int countCheckpoints(String threadId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM GRAPH_CHECKPOINT WHERE thread_id = ?", Integer.class,
				threadId);
	}

}
