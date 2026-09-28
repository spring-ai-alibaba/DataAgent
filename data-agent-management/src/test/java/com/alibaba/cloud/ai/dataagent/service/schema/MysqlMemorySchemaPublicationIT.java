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
package com.alibaba.cloud.ai.dataagent.service.schema;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class MysqlMemorySchemaPublicationIT {

	private static final String ACTIVE_IDENTITY = "a".repeat(64);

	private static final String PUBLICATION_LOCK = "dataagent:schema:1";

	@Container
	private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
		.withDatabaseName("dataagent_memory_it")
		.withUsername("dataagent")
		.withPassword("dataagent");

	private static DataSource dataSource;

	private static JdbcTemplate jdbc;

	@BeforeAll
	static void migrateLegacySchema() throws SQLException {
		dataSource = newDataSource();
		jdbc = new JdbcTemplate(dataSource);
		executeScript("sql/mysql/legacy-memory-schema.sql");
		executeScript("sql/migration/V20260729_01__create_durable_memory.sql");
		executeScript("sql/migration/V20260820_01__add_datasource_schema_revision.sql");
	}

	@BeforeEach
	void resetData() {
		jdbc.update("DELETE FROM memory_outbox");
		jdbc.update("DELETE FROM memory_item");
		jdbc.update("DELETE FROM turn_artifact");
		jdbc.update("DELETE FROM turn_run");
		jdbc.update("DELETE FROM conversation_turn");
		jdbc.update("DELETE FROM chat_session");
		jdbc.update("DELETE FROM datasource");
		jdbc.update("DELETE FROM agent");

		jdbc.update("INSERT INTO agent (id, name) VALUES (1, 'memory-it-agent')");
		jdbc.update("""
				INSERT INTO datasource
				  (id, name, type, host, port, database_name, username, password, description)
				VALUES
				  (1, 'orders', 'mysql', '127.0.0.1', 3306, 'orders', 'user', 'password', 'orders')
				""");
		jdbc.update("INSERT INTO chat_session (id, agent_id, title) VALUES ('conversation-1', 1, 'memory')");
	}

	@Test
	void productionMigrationsUpgradeLegacySchemaAndEnforceMemoryConstraints() {
		assertThat(tableCount("conversation_turn", "turn_run", "turn_artifact", "memory_item", "memory_outbox"))
			.isEqualTo(5);
		assertThat(columnCount("datasource", "schema_revision", "schema_generation")).isEqualTo(2);
		assertThat(columnCount("memory_outbox", "lease_token")).isEqualTo(1);
		assertThat(indexCount("memory_item", "uk_memory_item_active_identity")).isEqualTo(1);
		assertThat(constraintCount("memory_item", "chk_memory_item_active_identity")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT schema_generation FROM datasource WHERE id = 1", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT schema_revision FROM datasource WHERE id = 1", String.class)).isNull();

		jdbc.update("""
				INSERT INTO conversation_turn
				  (id, conversation_id, agent_id, raw_query, status, memory_eligible)
				VALUES
				  ('turn-1', 'conversation-1', 1, 'show orders', 'SUCCEEDED', 1)
				""");

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO memory_item
				  (scope_type, agent_id, memory_kind, memory_key, value_json, identity_hash,
				   active_identity_hash, source_turn_id, status)
				VALUES
				  ('AGENT', 1, 'PREFERENCE', 'currency', '\"CNY\"', ?, NULL, 'turn-1', 'CONFIRMED')
				""", ACTIVE_IDENTITY)).isInstanceOf(DataAccessException.class)
			.hasMessageContaining("chk_memory_item_active_identity");

		jdbc.update("""
				INSERT INTO memory_item
				  (scope_type, agent_id, memory_kind, memory_key, value_json, identity_hash,
				   active_identity_hash, source_turn_id, status)
				VALUES
				  ('AGENT', 1, 'PREFERENCE', 'currency', '\"CNY\"', ?, ?, 'turn-1', 'CONFIRMED')
				""", ACTIVE_IDENTITY, ACTIVE_IDENTITY);
		jdbc.update("""
				INSERT INTO memory_item
				  (scope_type, agent_id, memory_kind, memory_key, value_json, identity_hash,
				   active_identity_hash, source_turn_id, status)
				VALUES
				  ('AGENT', 1, 'PREFERENCE', 'currency', '\"USD\"', ?, NULL, 'turn-1', 'CANDIDATE')
				""", ACTIVE_IDENTITY);

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO memory_item
				  (scope_type, agent_id, memory_kind, memory_key, value_json, identity_hash,
				   active_identity_hash, source_turn_id, status)
				VALUES
				  ('AGENT', 1, 'PREFERENCE', 'currency', '\"EUR\"', ?, ?, 'turn-1', 'CONFIRMED')
				""", ACTIVE_IDENTITY, ACTIVE_IDENTITY)).isInstanceOf(DataAccessException.class)
			.hasMessageContaining("uk_memory_item_active_identity");

		jdbc.update("""
				INSERT INTO turn_run (run_id, turn_id, attempt, status)
				VALUES ('run-1', 'turn-1', 1, 'SUCCEEDED')
				""");
		jdbc.update("""
				INSERT INTO turn_artifact (turn_id, run_id, artifact_type, content)
				VALUES ('turn-1', 'run-1', 'REPORT', 'done')
				""");
		jdbc.update("""
				INSERT INTO memory_outbox
				  (aggregate_type, aggregate_id, event_type, status, lease_token)
				VALUES
				  ('TURN', 'turn-1', 'TURN_COMPLETED', 'DEAD', 'lease-1')
				""");

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory_item", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT lease_token FROM memory_outbox WHERE aggregate_id = 'turn-1'",
				String.class))
			.isEqualTo("lease-1");

		jdbc.update("DELETE FROM chat_session WHERE id = 'conversation-1'");

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversation_turn", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM turn_run", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM turn_artifact", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory_item", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory_outbox", Integer.class)).isOne();
	}

	@Test
	void mysqlAdvisoryLockSerializesDifferentCoordinatorInstances() throws Exception {
		jdbc.update("UPDATE datasource SET schema_revision = 'old-revision' WHERE id = 1");
		SchemaPublicationCoordinator firstCoordinator = new SchemaPublicationCoordinator(newDataSource());
		SchemaPublicationCoordinator secondCoordinator = new SchemaPublicationCoordinator(newDataSource());
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch firstReplacementStarted = new CountDownLatch(1);
		CountDownLatch releaseFirstReplacement = new CountDownLatch(1);
		CountDownLatch secondPublicationStarted = new CountDownLatch(1);
		CountDownLatch secondReplacementStarted = new CountDownLatch(1);
		try {
			Future<?> first = executor.submit(() -> firstCoordinator.publish(1, 0, "revision-one", () -> {
				firstReplacementStarted.countDown();
				await(releaseFirstReplacement);
			}));
			assertThat(firstReplacementStarted.await(10, TimeUnit.SECONDS)).isTrue();
			assertThat(jdbc.queryForObject("SELECT IS_USED_LOCK(?)", Long.class, PUBLICATION_LOCK)).isNotNull();

			Future<?> second = executor.submit(() -> {
				secondPublicationStarted.countDown();
				secondCoordinator.publish(1, 0, "revision-two", secondReplacementStarted::countDown);
			});
			assertThat(secondPublicationStarted.await(10, TimeUnit.SECONDS)).isTrue();
			assertThat(secondReplacementStarted.await(500, TimeUnit.MILLISECONDS)).isFalse();

			releaseFirstReplacement.countDown();
			first.get(10, TimeUnit.SECONDS);
			second.get(10, TimeUnit.SECONDS);

			assertThat(secondReplacementStarted.getCount()).isZero();
			assertThat(jdbc.queryForObject("SELECT schema_revision FROM datasource WHERE id = 1", String.class))
				.isEqualTo("revision-two");
			assertThat(jdbc.queryForObject("SELECT IS_FREE_LOCK(?)", Integer.class, PUBLICATION_LOCK)).isOne();
		}
		finally {
			releaseFirstReplacement.countDown();
			executor.shutdownNow();
			executor.awaitTermination(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void generationChangeOnAnotherConnectionPreventsStalePublication() {
		jdbc.update("UPDATE datasource SET schema_revision = 'old-revision', schema_generation = 7 WHERE id = 1");
		SchemaPublicationCoordinator coordinator = new SchemaPublicationCoordinator(newDataSource());
		JdbcTemplate concurrentInstance = new JdbcTemplate(newDataSource());

		assertThatThrownBy(() -> coordinator.publish(1, 7, "stale-revision",
				() -> concurrentInstance.update("UPDATE datasource SET schema_generation = 8 WHERE id = 1")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("schema inputs changed");

		assertThat(jdbc.queryForObject("SELECT schema_generation FROM datasource WHERE id = 1", Long.class))
			.isEqualTo(8L);
		assertThat(jdbc.queryForObject("SELECT schema_revision FROM datasource WHERE id = 1", String.class)).isNull();
		assertThat(jdbc.queryForObject("SELECT IS_FREE_LOCK(?)", Integer.class, PUBLICATION_LOCK)).isOne();
	}

	private static DataSource newDataSource() {
		return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
	}

	private static void executeScript(String path) throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(connection, new ClassPathResource(path));
		}
	}

	private int tableCount(String... tableNames) {
		String placeholders = String.join(", ", java.util.Collections.nCopies(tableNames.length, "?"));
		return jdbc.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = DATABASE()
				  AND table_name IN (%s)
				""".formatted(placeholders), Integer.class, (Object[]) tableNames);
	}

	private int columnCount(String tableName, String... columnNames) {
		String placeholders = String.join(", ", java.util.Collections.nCopies(columnNames.length, "?"));
		Object[] arguments = new Object[columnNames.length + 1];
		arguments[0] = tableName;
		System.arraycopy(columnNames, 0, arguments, 1, columnNames.length);
		return jdbc.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_schema = DATABASE()
				  AND table_name = ?
				  AND column_name IN (%s)
				""".formatted(placeholders), Integer.class, arguments);
	}

	private int indexCount(String tableName, String indexName) {
		return jdbc.queryForObject("""
				SELECT COUNT(DISTINCT index_name)
				FROM information_schema.statistics
				WHERE table_schema = DATABASE()
				  AND table_name = ?
				  AND index_name = ?
				""", Integer.class, tableName, indexName);
	}

	private int constraintCount(String tableName, String constraintName) {
		return jdbc.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.table_constraints
				WHERE table_schema = DATABASE()
				  AND table_name = ?
				  AND constraint_name = ?
				""", Integer.class, tableName, constraintName);
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out waiting for test latch");
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for test latch", e);
		}
	}

}
