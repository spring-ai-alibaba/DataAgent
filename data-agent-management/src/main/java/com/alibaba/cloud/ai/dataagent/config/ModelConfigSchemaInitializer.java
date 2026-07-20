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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelConfigSchemaInitializer {

	private final DataSource dataSource;

	private final JdbcTemplate jdbcTemplate;

	@PostConstruct
	public void ensureThinkingColumns() throws SQLException {
		ensureColumn("thinking_enabled",
				"ALTER TABLE model_config ADD COLUMN thinking_enabled BOOLEAN DEFAULT FALSE");
		ensureColumn("reasoning_effort",
				"ALTER TABLE model_config ADD COLUMN reasoning_effort VARCHAR(16) DEFAULT 'high'");
	}

	private void ensureColumn(String columnName, String ddl) throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			if (hasColumn(connection, columnName)) {
				return;
			}
		}
		log.info("Adding model_config.{} column", columnName);
		jdbcTemplate.execute(ddl);
	}

	private boolean hasColumn(Connection connection, String columnName) throws SQLException {
		for (String tableName : new String[] { "model_config", "MODEL_CONFIG" }) {
			try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, null)) {
				while (columns.next()) {
					if (columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
