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
package com.alibaba.cloud.ai.dataagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemorySchemaVerifierTest {

	@Test
	void verifiesSchemaBeforeOtherStartupRunners() {
		Order order = AnnotationUtils.findAnnotation(MemorySchemaVerifier.class, Order.class);

		assertThat(order).isNotNull();
		assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
	}

	@Test
	void completeSchemaPasses() {
		DataSource dataSource = dataSource();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		createRequiredSchema(jdbc, true, true);

		assertThatCode(() -> new MemorySchemaVerifier(dataSource).run(null)).doesNotThrowAnyException();
	}

	@Test
	void missingRevisionFailsWithActionableMigrationMessage() {
		DataSource dataSource = dataSource();
		createRequiredSchema(new JdbcTemplate(dataSource), false, true);

		assertThatThrownBy(() -> new MemorySchemaVerifier(dataSource).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("datasource.schema_revision", "V20260820_01");
	}

	@Test
	void missingSchemaGenerationFailsWithActionableMigrationMessage() {
		DataSource dataSource = dataSource();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		createRequiredSchema(jdbc, true, true);
		jdbc.execute("ALTER TABLE datasource DROP COLUMN schema_generation");

		assertThatThrownBy(() -> new MemorySchemaVerifier(dataSource).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("datasource.schema_generation", "V20260820_01");
	}

	@Test
	void missingOutboxLeaseFailsWithActionableMigrationMessage() {
		DataSource dataSource = dataSource();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		createRequiredSchema(jdbc, true, true);
		jdbc.execute("ALTER TABLE memory_outbox DROP COLUMN lease_token");

		assertThatThrownBy(() -> new MemorySchemaVerifier(dataSource).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("memory_outbox.lease_token", "V20260820_01");
	}

	@Test
	void missingRuntimeColumnFailsBeforeServingTraffic() {
		DataSource dataSource = dataSource();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		createRequiredSchema(jdbc, true, true);
		jdbc.execute("ALTER TABLE memory_item DROP COLUMN value_json");

		assertThatThrownBy(() -> new MemorySchemaVerifier(dataSource).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("column memory_item.value_json", "V20260729_01");
	}

	@Test
	void missingConfirmedIdentityConstraintFailsBeforeServingTraffic() {
		DataSource dataSource = dataSource();
		createRequiredSchema(new JdbcTemplate(dataSource), true, false);

		assertThatThrownBy(() -> new MemorySchemaVerifier(dataSource).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("unique index memory_item(active_identity_hash)", "V20260729_01");
	}

	@Test
	void compositeIdentityIndexDoesNotSatisfyConfirmedIdentityConstraint() {
		DataSource dataSource = dataSource();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		createRequiredSchema(jdbc, true, false);
		jdbc.execute("CREATE UNIQUE INDEX uk_wrong_identity ON memory_item(active_identity_hash, agent_id)");

		assertThatThrownBy(() -> new MemorySchemaVerifier(dataSource).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("unique index memory_item(active_identity_hash)");
	}

	private DataSource dataSource() {
		return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa",
				"");
	}

	private void createRequiredSchema(JdbcTemplate jdbc, boolean includeRevision, boolean includeUniqueIndex) {
		jdbc.execute("CREATE TABLE datasource (id INT PRIMARY KEY"
				+ (includeRevision ? ", schema_revision CHAR(64), schema_generation BIGINT NOT NULL DEFAULT 0" : "")
				+ ")");
		jdbc.execute("""
				CREATE TABLE conversation_turn (
				  id VARCHAR(36) PRIMARY KEY, conversation_id VARCHAR(36), agent_id INT, owner_id BIGINT,
				  accepted_run_id VARCHAR(36), datasource_id INT, raw_query VARCHAR, canonical_query VARCHAR,
				  query_frame VARCHAR, result_summary VARCHAR, final_answer VARCHAR, schema_fingerprint VARCHAR,
				  status VARCHAR, memory_eligible INT, observed_at TIMESTAMP, completed_at TIMESTAMP,
				  create_time TIMESTAMP, update_time TIMESTAMP
				)
				""");
		jdbc.execute("""
				CREATE TABLE turn_run (
				  run_id VARCHAR(36) PRIMARY KEY, turn_id VARCHAR(36), attempt INT, status VARCHAR,
				  error_message VARCHAR, create_time TIMESTAMP, update_time TIMESTAMP
				)
				""");
		jdbc.execute("""
				CREATE TABLE turn_artifact (
				  id BIGINT PRIMARY KEY, turn_id VARCHAR(36), run_id VARCHAR(36), artifact_type VARCHAR,
				  content VARCHAR, content_hash VARCHAR, create_time TIMESTAMP
				)
				""");
		jdbc.execute("""
				CREATE TABLE memory_item (
				  id BIGINT PRIMARY KEY, scope_type VARCHAR, owner_id BIGINT, agent_id INT, datasource_id INT,
				  memory_kind VARCHAR, memory_key VARCHAR, value_json VARCHAR, identity_hash CHAR(64),
				  active_identity_hash CHAR(64), source_turn_id VARCHAR(36), status VARCHAR, confidence DECIMAL,
				  schema_fingerprint VARCHAR, valid_until TIMESTAMP, supersedes_id BIGINT,
				  create_time TIMESTAMP, update_time TIMESTAMP
				)
				""");
		if (includeUniqueIndex) {
			jdbc.execute("CREATE UNIQUE INDEX uk_memory_active_identity ON memory_item(active_identity_hash)");
		}
		jdbc.execute(
				"""
							CREATE TABLE memory_outbox (
							  id BIGINT PRIMARY KEY, aggregate_type VARCHAR, aggregate_id VARCHAR, event_type VARCHAR,
							  payload VARCHAR, status VARCHAR, attempt_count INT, lease_token VARCHAR, available_at TIMESTAMP, last_error VARCHAR,
						  create_time TIMESTAMP, update_time TIMESTAMP
						)
						""");
	}

}
