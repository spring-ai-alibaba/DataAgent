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
package com.alibaba.cloud.ai.dataagent.connector.impls.h2;

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
class H2JdbcDdlTest {

	private H2JdbcDdl h2JdbcDdl;

	@Mock
	private Connection connection;

	@BeforeEach
	void setUp() {
		h2JdbcDdl = new H2JdbcDdl();
	}

	@Test
	void getDataSourceType_returnsH2() {
		assertEquals(BizDataSourceTypeEnum.H2, h2JdbcDdl.getDataSourceType());
	}

	@Test
	void showDatabases_withResults_returnsDatabaseList() throws SQLException {
		String[][] resultArr = { { "CATALOG_NAME" }, { "TESTDB" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<DatabaseInfoBO> databases = h2JdbcDdl.showDatabases(connection);
			assertEquals(1, databases.size());
			assertEquals("TESTDB", databases.get(0).getName());
		}
	}

	@Test
	void showDatabases_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "CATALOG_NAME" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			assertTrue(h2JdbcDdl.showDatabases(connection).isEmpty());
		}
	}

	@Test
	void showSchemas_returnsSchemaList() throws SQLException {
		String[][] resultArr = { { "schema_name" }, { "PUBLIC" }, { "INFORMATION_SCHEMA" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<SchemaInfoBO> schemas = h2JdbcDdl.showSchemas(connection);
			assertEquals(2, schemas.size());
			assertEquals("PUBLIC", schemas.get(0).getName());
		}
	}

	@Test
	void showSchemas_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "schema_name" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			assertTrue(h2JdbcDdl.showSchemas(connection).isEmpty());
		}
	}

	@Test
	void showTables_withPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "REMARKS" }, { "USERS", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = h2JdbcDdl.showTables(connection, "PUBLIC", "USER");
			assertEquals(1, tables.size());
			assertEquals("USERS", tables.get(0).getName());
			assertEquals("PUBLIC", tables.get(0).getSchema());
		}
	}

	@Test
	void showTables_withoutPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "REMARKS" }, { "ORDERS", "" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = h2JdbcDdl.showTables(connection, "PUBLIC", null);
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "REMARKS" }, { "USERS", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = h2JdbcDdl.fetchTables(connection, "PUBLIC", Arrays.asList("USERS"));
			assertEquals(1, tables.size());
			assertEquals("PUBLIC", tables.get(0).getSchema());
		}
	}

	@Test
	void showColumns_returnsColumnList() throws SQLException {
		String[][] resultArr = { { "column_name", "remarks", "data_type", "primary", "notnull" },
				{ "ID", "Primary key", "BIGINT", "true", "true" }, { "NAME", "Name", "VARCHAR", "false", "false" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString(), anyString()))
				.thenReturn(resultArr);

			List<ColumnInfoBO> columns = h2JdbcDdl.showColumns(connection, "PUBLIC", "USERS");
			assertEquals(2, columns.size());
			assertEquals("ID", columns.get(0).getName());
			assertTrue(columns.get(0).isPrimary());
		}
	}

	@Test
	void showForeignKeys_returnsForeignKeyList() throws SQLException {
		String[][] resultArr = { { "table", "column", "ref_table", "ref_column" },
				{ "ORDERS", "USER_ID", "USERS", "ID" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString(), anyString()))
				.thenReturn(resultArr);

			List<ForeignKeyInfoBO> fks = h2JdbcDdl.showForeignKeys(connection, "PUBLIC",
					Arrays.asList("ORDERS", "USERS"));
			assertEquals(1, fks.size());
			assertEquals("ORDERS", fks.get(0).getTable());
			assertEquals("USER_ID", fks.get(0).getColumn());
		}
	}

	@Test
	void sampleColumn_returnsDedupedSamples() throws SQLException {
		String[][] resultArr = { { "NAME" }, { "Alice" }, { "Bob" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = h2JdbcDdl.sampleColumn(connection, "PUBLIC", "USERS", "NAME");
			assertTrue(samples.contains("Alice"));
			assertTrue(samples.contains("Bob"));
		}
	}

	@Test
	void sampleColumn_sqlException_returnsEmptyList() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			List<String> samples = h2JdbcDdl.sampleColumn(connection, "PUBLIC", "USERS", "NAME");
			assertTrue(samples.isEmpty());
		}
	}

	@Test
	void scanTable_returnsResultSet() throws SQLException {
		ResultSetBO expected = ResultSetBO.builder().build();

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), any(), anyString()))
				.thenReturn(expected);

			ResultSetBO result = h2JdbcDdl.scanTable(connection, "PUBLIC", "USERS");
			assertNotNull(result);
		}
	}

	@Test
	void scanTable_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> h2JdbcDdl.scanTable(connection, "PUBLIC", "USERS"));
		}
	}

}
