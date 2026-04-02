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
package com.alibaba.cloud.ai.dataagent.connector.impls.mysql;

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
class MysqlJdbcDdlTest {

	private MysqlJdbcDdl mysqlJdbcDdl;

	@Mock
	private Connection connection;

	@BeforeEach
	void setUp() {
		mysqlJdbcDdl = new MysqlJdbcDdl();
	}

	@Test
	void getDataSourceType_returnsMysql() {
		assertEquals(BizDataSourceTypeEnum.MYSQL, mysqlJdbcDdl.getDataSourceType());
	}

	@Test
	void showDatabases_withResults_returnsDatabaseList() throws SQLException {
		String[][] resultArr = { { "Database" }, { "testdb" }, { "production" } };

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<DatabaseInfoBO> databases = mysqlJdbcDdl.showDatabases(connection);

			assertEquals(2, databases.size());
			assertEquals("testdb", databases.get(0).getName());
			assertEquals("production", databases.get(1).getName());
		}
	}

	@Test
	void showDatabases_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "Database" } };

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<DatabaseInfoBO> databases = mysqlJdbcDdl.showDatabases(connection);

			assertTrue(databases.isEmpty());
		}
	}

	@Test
	void showDatabases_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("connection failed"));

			assertThrows(RuntimeException.class, () -> mysqlJdbcDdl.showDatabases(connection));
		}
	}

	@Test
	void showSchemas_returnsEmptyList() {
		List<SchemaInfoBO> schemas = mysqlJdbcDdl.showSchemas(connection);
		assertTrue(schemas.isEmpty());
	}

	@Test
	void showTables_withPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, { "users", "User table" },
				{ "orders", "Order table" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = mysqlJdbcDdl.showTables(connection, "testdb", "user");

			assertEquals(2, tables.size());
			assertEquals("users", tables.get(0).getName());
			assertEquals("User table", tables.get(0).getDescription());
		}
	}

	@Test
	void showTables_withoutPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, { "users", "User table" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = mysqlJdbcDdl.showTables(connection, "testdb", null);

			assertEquals(1, tables.size());
		}
	}

	@Test
	void showTables_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = mysqlJdbcDdl.showTables(connection, "testdb", null);
			assertTrue(tables.isEmpty());
		}
	}

	@Test
	void fetchTables_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, { "users", "User table" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = mysqlJdbcDdl.fetchTables(connection, "testdb",
					Arrays.asList("users", "orders"));

			assertEquals(1, tables.size());
			assertEquals("users", tables.get(0).getName());
		}
	}

	@Test
	void showColumns_returnsColumnList() throws SQLException {
		String[][] resultArr = { { "column_name", "column_comment", "data_type", "primary", "notnull" },
				{ "id", "Primary key", "bigint", "true", "true" },
				{ "name", "User name", "varchar", "false", "false" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic
				.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString(), anyString()))
				.thenReturn(resultArr);

			List<ColumnInfoBO> columns = mysqlJdbcDdl.showColumns(connection, "testdb", "users");

			assertEquals(2, columns.size());
			assertEquals("id", columns.get(0).getName());
			assertEquals("Primary key", columns.get(0).getDescription());
			assertTrue(columns.get(0).isPrimary());
			assertTrue(columns.get(0).isNotnull());
			assertEquals("name", columns.get(1).getName());
			assertFalse(columns.get(1).isPrimary());
		}
	}

	@Test
	void showForeignKeys_returnsForeignKeyList() throws SQLException {
		String[][] resultArr = { { "table", "column", "constraint", "ref_table", "ref_column" },
				{ "orders", "user_id", "fk_user", "users", "id" } };

		when(connection.getCatalog()).thenReturn("testdb");

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic
				.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString(), anyString()))
				.thenReturn(resultArr);

			List<ForeignKeyInfoBO> fks = mysqlJdbcDdl.showForeignKeys(connection, "testdb",
					Arrays.asList("orders", "users"));

			assertEquals(1, fks.size());
			assertEquals("orders", fks.get(0).getTable());
			assertEquals("user_id", fks.get(0).getColumn());
			assertEquals("users", fks.get(0).getReferencedTable());
			assertEquals("id", fks.get(0).getReferencedColumn());
		}
	}

	@Test
	void sampleColumn_returnsSamples() throws SQLException {
		String[][] resultArr = { { "name" }, { "Alice" }, { "Bob" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic
				.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = mysqlJdbcDdl.sampleColumn(connection, "testdb", "users", "name");

			assertFalse(samples.isEmpty());
			assertTrue(samples.contains("Alice"));
			assertTrue(samples.contains("Bob"));
		}
	}

	@Test
	void sampleColumn_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "name" } };

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic
				.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = mysqlJdbcDdl.sampleColumn(connection, "testdb", "users", "name");

			assertTrue(samples.isEmpty());
		}
	}

	@Test
	void sampleColumn_sqlException_returnsEmptyList() throws SQLException {
		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic
				.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			List<String> samples = mysqlJdbcDdl.sampleColumn(connection, "testdb", "users", "name");
			assertTrue(samples.isEmpty());
		}
	}

	@Test
	void scanTable_returnsResultSet() throws SQLException {
		ResultSetBO expectedResult = ResultSetBO.builder().build();

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic
				.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), any(), anyString()))
				.thenReturn(expectedResult);

			ResultSetBO result = mysqlJdbcDdl.scanTable(connection, "testdb", "users");

			assertNotNull(result);
		}
	}

	@Test
	void scanTable_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic
				.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> mysqlJdbcDdl.scanTable(connection, "testdb", "users"));
		}
	}

	@Test
	void showDatabases_rowWithEmptyLength_skipsRow() throws SQLException {
		String[][] resultArr = { { "Database" }, {} , { "testdb" } };

		try (MockedStatic<SqlExecutor> mockedStatic = mockStatic(SqlExecutor.class)) {
			mockedStatic.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenReturn(resultArr);

			List<DatabaseInfoBO> databases = mysqlJdbcDdl.showDatabases(connection);

			assertEquals(1, databases.size());
			assertEquals("testdb", databases.get(0).getName());
		}
	}

}
