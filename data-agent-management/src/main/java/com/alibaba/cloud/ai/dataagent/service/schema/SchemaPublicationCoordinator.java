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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Publishes one datasource schema revision behind a database-wide writer lock. The
 * revision is invalidated before the external vector-store replacement starts, so readers
 * never treat a partially replaced index as current.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaPublicationCoordinator {

	private static final int LOCK_TIMEOUT_SECONDS = 30;

	private static final String UPDATE_REVISION_SQL = """
			UPDATE datasource
			SET schema_revision = ?, update_time = CURRENT_TIMESTAMP
			WHERE id = ? AND schema_generation = ?
			""";

	private static final String SELECT_GENERATION_SQL = """
			SELECT schema_generation
			FROM datasource
			WHERE id = ?
			""";

	private final DataSource dataSource;

	private final ConcurrentHashMap<Integer, ReentrantLock> localLocks = new ConcurrentHashMap<>();

	/**
	 * Fails closed as soon as a schema refresh starts. Metadata extraction and model
	 * enrichment happen outside this lock, but readers cannot keep using the previous
	 * revision if those steps fail.
	 */
	public void invalidate(Integer datasourceId, long expectedGeneration) {
		withPublicationLock(datasourceId,
				connection -> updateRevision(connection, datasourceId, expectedGeneration, null));
	}

	public void publish(Integer datasourceId, long expectedGeneration, String schemaRevision,
			Runnable vectorReplacement) {
		withPublicationLock(datasourceId, connection -> {
			try {
				updateRevision(connection, datasourceId, expectedGeneration, null);
				vectorReplacement.run();
				updateRevision(connection, datasourceId, expectedGeneration, schemaRevision);
			}
			catch (RuntimeException | Error publicationFailure) {
				invalidateAfterFailure(connection, datasourceId, expectedGeneration, publicationFailure);
				throw publicationFailure;
			}
		});
	}

	private void withPublicationLock(Integer datasourceId, ConnectionWork work) {
		ReentrantLock localLock = localLocks.computeIfAbsent(datasourceId, ignored -> new ReentrantLock());
		localLock.lock();
		try (Connection connection = dataSource.getConnection()) {
			if (!connection.getAutoCommit()) {
				connection.setAutoCommit(true);
			}
			String productName = connection.getMetaData().getDatabaseProductName();
			boolean mysql = isMysql(productName);
			if (!mysql && !isH2(productName)) {
				throw new IllegalStateException(
						"Schema publication locking is unsupported for management database: " + productName);
			}
			if (mysql) {
				acquireDatabaseLock(connection, datasourceId);
			}
			try {
				work.run(connection);
			}
			finally {
				if (mysql) {
					releaseDatabaseLock(connection, datasourceId);
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Failed to publish schema revision for datasource " + datasourceId, e);
		}
		finally {
			localLock.unlock();
		}
	}

	private void acquireDatabaseLock(Connection connection, Integer datasourceId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
			statement.setString(1, lockName(datasourceId));
			statement.setInt(2, LOCK_TIMEOUT_SECONDS);
			try (ResultSet result = statement.executeQuery()) {
				if (!result.next() || result.getInt(1) != 1) {
					throw new IllegalStateException(
							"Timed out waiting for schema publication lock for datasource " + datasourceId);
				}
			}
		}
	}

	private void releaseDatabaseLock(Connection connection, Integer datasourceId) {
		try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
			statement.setString(1, lockName(datasourceId));
			statement.executeQuery().close();
		}
		catch (SQLException e) {
			log.warn("Failed to release schema publication lock for datasource {}", datasourceId, e);
		}
	}

	private void updateRevision(Connection connection, Integer datasourceId, long expectedGeneration,
			String schemaRevision) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(UPDATE_REVISION_SQL)) {
			if (schemaRevision == null) {
				statement.setNull(1, Types.CHAR);
			}
			else {
				statement.setString(1, schemaRevision);
			}
			statement.setInt(2, datasourceId);
			statement.setLong(3, expectedGeneration);
			int updated = statement.executeUpdate();
			if (updated == 0 && generationMatches(connection, datasourceId, expectedGeneration)) {
				// MySQL can report zero changed rows for an idempotent assignment when
				// useAffectedRows is enabled. The generation predicate is the actual
				// fence.
				return;
			}
			if (updated != 1) {
				throw new IllegalStateException("Datasource schema inputs changed while publishing datasource "
						+ datasourceId + " at generation " + expectedGeneration);
			}
		}
	}

	private boolean generationMatches(Connection connection, Integer datasourceId, long expectedGeneration)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(SELECT_GENERATION_SQL)) {
			statement.setInt(1, datasourceId);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() && result.getLong(1) == expectedGeneration;
			}
		}
	}

	private void invalidateAfterFailure(Connection connection, Integer datasourceId, long expectedGeneration,
			Throwable publicationFailure) {
		try {
			updateRevision(connection, datasourceId, expectedGeneration, null);
		}
		catch (Exception invalidationFailure) {
			publicationFailure.addSuppressed(invalidationFailure);
		}
	}

	private boolean isMysql(String productName) {
		String normalized = productName.toLowerCase(Locale.ROOT);
		return normalized.contains("mysql") || normalized.contains("mariadb");
	}

	private boolean isH2(String productName) {
		return productName.toLowerCase(Locale.ROOT).contains("h2");
	}

	private String lockName(Integer datasourceId) {
		return "dataagent:schema:" + datasourceId;
	}

	@FunctionalInterface
	private interface ConnectionWork {

		void run(Connection connection) throws SQLException;

	}

}
