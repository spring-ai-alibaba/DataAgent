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

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fails fast when the manually managed relational memory schema is incomplete.
 * Framework-owned ChatMemory, Store and checkpoint tables remain initialized by their
 * respective framework components.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class MemorySchemaVerifier implements ApplicationRunner {

	private static final List<TableRequirement> REQUIRED_SCHEMA = List.of(new TableRequirement("conversation_turn",
			List.of("id", "conversation_id", "agent_id", "owner_id", "accepted_run_id", "datasource_id", "raw_query",
					"canonical_query", "query_frame", "result_summary", "final_answer", "schema_fingerprint", "status",
					"memory_eligible", "observed_at", "completed_at", "create_time", "update_time")),
			new TableRequirement("turn_run",
					List.of("run_id", "turn_id", "attempt", "status", "error_message", "create_time", "update_time")),
			new TableRequirement("turn_artifact",
					List.of("id", "turn_id", "run_id", "artifact_type", "content", "content_hash", "create_time")),
			new TableRequirement("memory_item",
					List.of("id", "scope_type", "owner_id", "agent_id", "datasource_id", "memory_kind", "memory_key",
							"value_json", "identity_hash", "active_identity_hash", "source_turn_id", "status",
							"confidence", "schema_fingerprint", "valid_until", "supersedes_id", "create_time",
							"update_time")),
			new TableRequirement("memory_outbox",
					List.of("id", "aggregate_type", "aggregate_id", "event_type", "payload", "status", "attempt_count",
							"lease_token", "available_at", "last_error", "create_time", "update_time")),
			new TableRequirement("datasource", List.of("schema_revision", "schema_generation")));

	private final DataSource dataSource;

	@Override
	public void run(ApplicationArguments args) {
		List<String> missing = new ArrayList<>();
		try (Connection connection = dataSource.getConnection()) {
			DatabaseMetaData metadata = connection.getMetaData();
			for (TableRequirement requirement : REQUIRED_SCHEMA) {
				if (!tableExists(metadata, connection.getCatalog(), requirement.table())) {
					missing.add("table " + requirement.table());
					continue;
				}
				for (String column : requirement.columns()) {
					if (!columnExists(metadata, connection.getCatalog(), requirement.table(), column)) {
						missing.add("column " + requirement.table() + "." + column);
					}
				}
			}
			if (tableExists(metadata, connection.getCatalog(), "memory_item")
					&& !hasUniqueIndex(metadata, connection.getCatalog(), "memory_item", "active_identity_hash")) {
				missing.add("unique index memory_item(active_identity_hash)");
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Failed to verify durable memory database schema", e);
		}
		if (!missing.isEmpty()) {
			throw new IllegalStateException("Durable memory schema is incomplete: " + String.join(", ", missing)
					+ ". Apply sql/migration/V20260729_01__create_durable_memory.sql and "
					+ "sql/migration/V20260820_01__add_datasource_schema_revision.sql before startup");
		}
	}

	private boolean tableExists(DatabaseMetaData metadata, String catalog, String table) throws SQLException {
		for (String candidate : identifierCandidates(table)) {
			try (ResultSet tables = metadata.getTables(catalog, null, candidate, new String[] { "TABLE" })) {
				if (tables.next()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean columnExists(DatabaseMetaData metadata, String catalog, String table, String column)
			throws SQLException {
		for (String tableCandidate : identifierCandidates(table)) {
			for (String columnCandidate : identifierCandidates(column)) {
				try (ResultSet columns = metadata.getColumns(catalog, null, tableCandidate, columnCandidate)) {
					if (columns.next()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean hasUniqueIndex(DatabaseMetaData metadata, String catalog, String table, String column)
			throws SQLException {
		for (String tableCandidate : identifierCandidates(table)) {
			Map<String, List<String>> uniqueIndexes = new LinkedHashMap<>();
			try (ResultSet indexes = metadata.getIndexInfo(catalog, null, tableCandidate, true, false)) {
				while (indexes.next()) {
					String indexName = indexes.getString("INDEX_NAME");
					String indexedColumn = indexes.getString("COLUMN_NAME");
					if (indexName != null) {
						uniqueIndexes.computeIfAbsent(indexName, ignored -> new ArrayList<>()).add(indexedColumn);
					}
				}
			}
			if (uniqueIndexes.values()
				.stream()
				.anyMatch(columns -> columns.size() == 1 && column.equalsIgnoreCase(columns.get(0)))) {
				return true;
			}
		}
		return false;
	}

	private List<String> identifierCandidates(String identifier) {
		return List.of(identifier, identifier.toUpperCase(Locale.ROOT), identifier.toLowerCase(Locale.ROOT))
			.stream()
			.distinct()
			.toList();
	}

	private record TableRequirement(String table, List<String> columns) {
	}

}
