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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaPublicationCoordinatorTest {

	private JdbcTemplate jdbc;

	private SchemaPublicationCoordinator coordinator;

	@BeforeEach
	void setUp() {
		DataSource dataSource = new DriverManagerDataSource(
				"jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
		jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("""
				CREATE TABLE datasource (
				  id INT PRIMARY KEY,
				  schema_revision CHAR(64),
				  schema_generation BIGINT NOT NULL,
				  update_time TIMESTAMP
				)
				""");
		jdbc.update("INSERT INTO datasource (id, schema_revision, schema_generation) VALUES (1, 'old-revision', 3)");
		coordinator = new SchemaPublicationCoordinator(dataSource);
	}

	@Test
	void publishInvalidatesBeforeReplacementAndPublishesAfterSuccess() {
		AtomicReference<String> revisionDuringReplacement = new AtomicReference<>();

		coordinator.publish(1, 3, "new-revision", () -> revisionDuringReplacement
			.set(jdbc.queryForObject("SELECT schema_revision FROM datasource WHERE id = 1", String.class)));

		assertThat(revisionDuringReplacement).hasValue(null);
		assertThat(jdbc.queryForObject("SELECT schema_revision FROM datasource WHERE id = 1", String.class))
			.isEqualTo("new-revision");
	}

	@Test
	void explicitInvalidationFailsClosedBeforeMetadataExtraction() {
		coordinator.invalidate(1, 3);

		assertThat(jdbc.queryForObject("SELECT schema_revision FROM datasource WHERE id = 1", String.class)).isNull();
	}

	@Test
	void explicitInvalidationRejectsStaleGeneration() {
		assertThatThrownBy(() -> coordinator.invalidate(1, 2)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("schema inputs changed");

		assertThat(jdbc.queryForObject("SELECT schema_revision FROM datasource WHERE id = 1", String.class))
			.isEqualTo("old-revision");
	}

	@Test
	void failedReplacementLeavesRevisionInvalidated() {
		assertThatThrownBy(() -> coordinator.publish(1, 3, "new-revision", () -> {
			throw new IllegalStateException("vector store unavailable");
		})).isInstanceOf(IllegalStateException.class).hasMessageContaining("vector store unavailable");

		assertThat(jdbc.queryForObject("SELECT schema_revision FROM datasource WHERE id = 1", String.class)).isNull();
	}

	@Test
	void concurrentPublicationsAreSerializedPerDatasource() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch firstReplacementStarted = new CountDownLatch(1);
		CountDownLatch releaseFirstReplacement = new CountDownLatch(1);
		CountDownLatch secondReplacementStarted = new CountDownLatch(1);
		AtomicReference<String> vectorRevision = new AtomicReference<>();
		try {
			Future<?> first = executor.submit(() -> coordinator.publish(1, 3, "revision-one", () -> {
				firstReplacementStarted.countDown();
				await(releaseFirstReplacement);
				vectorRevision.set("revision-one");
			}));
			assertThat(firstReplacementStarted.await(2, TimeUnit.SECONDS)).isTrue();

			Future<?> second = executor.submit(() -> coordinator.publish(1, 3, "revision-two", () -> {
				secondReplacementStarted.countDown();
				vectorRevision.set("revision-two");
			}));

			assertThat(secondReplacementStarted.await(200, TimeUnit.MILLISECONDS)).isFalse();
			releaseFirstReplacement.countDown();
			first.get(2, TimeUnit.SECONDS);
			second.get(2, TimeUnit.SECONDS);

			assertThat(secondReplacementStarted.getCount()).isZero();
			assertThat(vectorRevision).hasValue("revision-two");
			assertThat(jdbc.queryForObject("SELECT schema_revision FROM datasource WHERE id = 1", String.class))
				.isEqualTo("revision-two");
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void generationChangeDuringReplacementCannotPublishStaleRevision() {
		assertThatThrownBy(() -> coordinator.publish(1, 3, "stale-revision",
				() -> jdbc.update("UPDATE datasource SET schema_generation = 4, schema_revision = NULL WHERE id = 1")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("schema inputs changed");

		assertThat(jdbc.queryForObject("SELECT schema_revision FROM datasource WHERE id = 1", String.class)).isNull();
		assertThat(jdbc.queryForObject("SELECT schema_generation FROM datasource WHERE id = 1", Long.class))
			.isEqualTo(4L);
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(2, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out waiting for test latch");
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for test latch", e);
		}
	}

}
