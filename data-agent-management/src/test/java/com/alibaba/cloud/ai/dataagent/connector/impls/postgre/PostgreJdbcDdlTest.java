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
package com.alibaba.cloud.ai.dataagent.connector.impls.postgre;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostgreJdbcDdlTest {

	private PostgreJdbcDdl postgreJdbcDdl;

	@Mock
	private Connection connection;

	@BeforeEach
	void setUp() {
		postgreJdbcDdl = new PostgreJdbcDdl();
	}

	@Test
	void getDataSourceType_returnsPostgresql() {
		assertEquals(BizDataSourceTypeEnum.POSTGRESQL, postgreJdbcDdl.getDataSourceType());
	}

	@Test
	void showDatabases_returnsResults() throws SQLException {
		String[][] resultArr = { { "datname" }, { "postgres" }, { "mydb" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<DatabaseInfoBO> databases = postgreJdbcDdl.showDatabases(connection);
			assertEquals(2, databases.size());
			assertEquals("postgres", databases.get(0).getName());
		}
	}

	@Test
	void showDatabases_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "datname" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			assertTrue(postgreJdbcDdl.showDatabases(connection).isEmpty());
		}
	}

	@Test
	void showSchemas_returnsSchemaList() throws SQLException {
		String[][] resultArr = { { "schema_name" }, { "public" }, { "pg_catalog" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<SchemaInfoBO> schemas = postgreJdbcDdl.showSchemas(connection);
			assertEquals(2, schemas.size());
			assertEquals("public", schemas.get(0).getName());
		}
	}

	@Test
	void showTables_withPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "table_name", "description" }, { "users", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = postgreJdbcDdl.showTables(connection, "public", "user");
			assertEquals(1, tables.size());
			assertEquals("users", tables.get(0).getName());
		}
	}

	@Test
	void showTables_withoutPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "table_name", "description" }, { "orders", null } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = postgreJdbcDdl.showTables(connection, "public", null);
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_returnsTableList() throws SQLException {
		String[][] resultArr = { { "table_name", "description" }, { "users", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = postgreJdbcDdl.fetchTables(connection, "public",
					Arrays.asList("users", "orders"));
			assertEquals(1, tables.size());
		}
	}

	@Test
	void showColumns_returnsColumnList() throws SQLException {
		String[][] resultArr = { { "column_name", "description", "data_type", "primary", "notnull" },
				{ "id", "Primary key", "integer", "true", "true" },
				{ "email", "Email", "character varying", "false", "false" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ColumnInfoBO> columns = postgreJdbcDdl.showColumns(connection, "public", "users");
			assertEquals(2, columns.size());
			assertEquals("id", columns.get(0).getName());
			assertTrue(columns.get(0).isPrimary());
		}
	}

	@Test
	void showForeignKeys_returnsForeignKeyList() throws SQLException {
		String[][] resultArr = { { "table_name", "column_name", "constraint_name", "foreign_table", "foreign_column" },
				{ "orders", "user_id", "fk_orders_users", "users", "id" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ForeignKeyInfoBO> fks = postgreJdbcDdl.showForeignKeys(connection, "public",
					Arrays.asList("orders", "users"));
			assertEquals(1, fks.size());
			assertEquals("orders", fks.get(0).getTable());
			assertEquals("users", fks.get(0).getReferencedTable());
		}
	}

	@Test
	void sampleColumn_returnsDedupedSamples() throws SQLException {
		String[][] resultArr = { { "name" }, { "Alice" }, { "Bob" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = postgreJdbcDdl.sampleColumn(connection, "public", "users", "name");
			assertTrue(samples.contains("Alice"));
			assertTrue(samples.contains("Bob"));
		}
	}

	@Test
	void sampleColumn_sqlException_returnsEmptyList() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString(), anyString()))
				.thenThrow(new SQLException("error"));

			List<String> samples = postgreJdbcDdl.sampleColumn(connection, "public", "users", "name");
			assertTrue(samples.isEmpty());
		}
	}

	@Test
	void scanTable_returnsResultSet() throws SQLException {
		ResultSetBO expected = ResultSetBO.builder().build();

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), anyString(), anyString()))
				.thenReturn(expected);

			ResultSetBO result = postgreJdbcDdl.scanTable(connection, "public", "users");
			assertNotNull(result);
		}
	}

	@Test
	void scanTable_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), anyString(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> postgreJdbcDdl.scanTable(connection, "public", "users"));
		}
	}

}
