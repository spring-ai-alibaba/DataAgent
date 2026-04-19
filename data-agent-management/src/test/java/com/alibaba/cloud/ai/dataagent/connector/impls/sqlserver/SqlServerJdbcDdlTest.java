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
package com.alibaba.cloud.ai.dataagent.connector.impls.sqlserver;

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
class SqlServerJdbcDdlTest {

	private SqlServerJdbcDdl sqlServerJdbcDdl;

	@Mock
	private Connection connection;

	@BeforeEach
	void setUp() {
		sqlServerJdbcDdl = new SqlServerJdbcDdl();
	}

	@Test
	void getDataSourceType_returnsSqlServer() {
		assertEquals(BizDataSourceTypeEnum.SQL_SERVER, sqlServerJdbcDdl.getDataSourceType());
	}

	@Test
	void showDatabases_returnsResults() throws SQLException {
		String[][] resultArr = { { "name" }, { "master" }, { "mydb" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<DatabaseInfoBO> databases = sqlServerJdbcDdl.showDatabases(connection);
			assertEquals(2, databases.size());
			assertEquals("master", databases.get(0).getName());
		}
	}

	@Test
	void showDatabases_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> sqlServerJdbcDdl.showDatabases(connection));
		}
	}

	@Test
	void showSchemas_returnsSchemaList() throws SQLException {
		String[][] resultArr = { { "schema_name" }, { "dbo" }, { "sales" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<SchemaInfoBO> schemas = sqlServerJdbcDdl.showSchemas(connection);
			assertEquals(2, schemas.size());
			assertEquals("dbo", schemas.get(0).getName());
		}
	}

	@Test
	void showTables_withPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, { "Users", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = sqlServerJdbcDdl.showTables(connection, "dbo", "User");
			assertEquals(1, tables.size());
			assertEquals("Users", tables.get(0).getName());
		}
	}

	@Test
	void showTables_withoutPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, { "Orders", null } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = sqlServerJdbcDdl.showTables(connection, "dbo", null);
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, { "Users", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = sqlServerJdbcDdl.fetchTables(connection, "dbo", Arrays.asList("Users"));
			assertEquals(1, tables.size());
		}
	}

	@Test
	void showColumns_returnsColumnList() throws SQLException {
		String[][] resultArr = { { "COLUMN_NAME", "COLUMN_COMMENT", "DATA_TYPE", "IS_PRIMARY_KEY", "IS_NOT_NULL" },
				{ "Id", "Primary key", "int", "true", "true" }, { "Name", "Name", "nvarchar", "false", "false" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ColumnInfoBO> columns = sqlServerJdbcDdl.showColumns(connection, "dbo", "Users");
			assertEquals(2, columns.size());
			assertEquals("Id", columns.get(0).getName());
			assertTrue(columns.get(0).isPrimary());
		}
	}

	@Test
	void showForeignKeys_returnsForeignKeyList() throws SQLException {
		String[][] resultArr = { { "Table", "Column", "Referenced_Table", "Referenced_Column" },
				{ "Orders", "UserId", "Users", "Id" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ForeignKeyInfoBO> fks = sqlServerJdbcDdl.showForeignKeys(connection, "dbo",
					Arrays.asList("Orders", "Users"));
			assertEquals(1, fks.size());
			assertEquals("Orders", fks.get(0).getTable());
			assertEquals("Users", fks.get(0).getReferencedTable());
		}
	}

	@Test
	void sampleColumn_returnsSamples() throws SQLException {
		String[][] resultArr = { { "Name" }, { "Alice" }, { "Bob" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = sqlServerJdbcDdl.sampleColumn(connection, "dbo", "Users", "Name");
			assertEquals(2, samples.size());
		}
	}

	@Test
	void sampleColumn_sqlException_returnsEmptyList() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			List<String> samples = sqlServerJdbcDdl.sampleColumn(connection, "dbo", "Users", "Name");
			assertTrue(samples.isEmpty());
		}
	}

	@Test
	void scanTable_returnsResultSet() throws SQLException {
		ResultSetBO expected = ResultSetBO.builder().build();

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), anyString(), anyString()))
				.thenReturn(expected);

			ResultSetBO result = sqlServerJdbcDdl.scanTable(connection, "dbo", "Users");
			assertNotNull(result);
		}
	}

	@Test
	void scanTable_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), anyString(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> sqlServerJdbcDdl.scanTable(connection, "dbo", "Users"));
		}
	}

	@Test
	void showDatabases_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "name" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			assertTrue(sqlServerJdbcDdl.showDatabases(connection).isEmpty());
		}
	}

	@Test
	void showDatabases_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "name" }, {}, { "mydb" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<DatabaseInfoBO> databases = sqlServerJdbcDdl.showDatabases(connection);
			assertEquals(1, databases.size());
		}
	}

	@Test
	void showSchemas_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "schema_name" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			assertTrue(sqlServerJdbcDdl.showSchemas(connection).isEmpty());
		}
	}

	@Test
	void showSchemas_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> sqlServerJdbcDdl.showSchemas(connection));
		}
	}

	@Test
	void showSchemas_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "schema_name" }, {}, { "dbo" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<SchemaInfoBO> schemas = sqlServerJdbcDdl.showSchemas(connection);
			assertEquals(1, schemas.size());
		}
	}

	@Test
	void showTables_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			assertTrue(sqlServerJdbcDdl.showTables(connection, "dbo", null).isEmpty());
		}
	}

	@Test
	void showTables_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, {}, { "Users", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = sqlServerJdbcDdl.showTables(connection, "dbo", null);
			assertEquals(1, tables.size());
		}
	}

	@Test
	void showTables_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> sqlServerJdbcDdl.showTables(connection, "dbo", null));
		}
	}

	@Test
	void showTables_withBlankPattern_usesBaseSqlOnly() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, { "Users", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = sqlServerJdbcDdl.showTables(connection, "dbo", "");
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			assertTrue(sqlServerJdbcDdl.fetchTables(connection, "dbo", Arrays.asList("Users")).isEmpty());
		}
	}

	@Test
	void fetchTables_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "TABLE_COMMENT" }, {}, { "Users", "User table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = sqlServerJdbcDdl.fetchTables(connection, "dbo", Arrays.asList("Users"));
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class,
					() -> sqlServerJdbcDdl.fetchTables(connection, "dbo", Arrays.asList("Users")));
		}
	}

	@Test
	void showColumns_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "COLUMN_NAME", "COLUMN_COMMENT", "DATA_TYPE", "IS_PRIMARY_KEY", "IS_NOT_NULL" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			assertTrue(sqlServerJdbcDdl.showColumns(connection, "dbo", "Users").isEmpty());
		}
	}

	@Test
	void showColumns_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "COLUMN_NAME", "COLUMN_COMMENT", "DATA_TYPE", "IS_PRIMARY_KEY", "IS_NOT_NULL" }, {},
				{ "Id", "Primary key", "int", "true", "true" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ColumnInfoBO> columns = sqlServerJdbcDdl.showColumns(connection, "dbo", "Users");
			assertEquals(1, columns.size());
		}
	}

	@Test
	void showColumns_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> sqlServerJdbcDdl.showColumns(connection, "dbo", "Users"));
		}
	}

	@Test
	void showForeignKeys_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "Table", "Column", "Referenced_Table", "Referenced_Column" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			assertTrue(sqlServerJdbcDdl.showForeignKeys(connection, "dbo", Arrays.asList("Orders", "Users")).isEmpty());
		}
	}

	@Test
	void showForeignKeys_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "Table", "Column", "Referenced_Table", "Referenced_Column" }, {},
				{ "Orders", "UserId", "Users", "Id" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ForeignKeyInfoBO> fks = sqlServerJdbcDdl.showForeignKeys(connection, "dbo",
					Arrays.asList("Orders", "Users"));
			assertEquals(1, fks.size());
		}
	}

	@Test
	void showForeignKeys_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class,
					() -> sqlServerJdbcDdl.showForeignKeys(connection, "dbo", Arrays.asList("Orders")));
		}
	}

	@Test
	void sampleColumn_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "Name" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			assertTrue(sqlServerJdbcDdl.sampleColumn(connection, "dbo", "Users", "Name").isEmpty());
		}
	}

	@Test
	void sampleColumn_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "Name" }, {}, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = sqlServerJdbcDdl.sampleColumn(connection, "dbo", "Users", "Name");
			assertEquals(1, samples.size());
		}
	}

	@Test
	void sampleColumn_deduplicatesResults() throws SQLException {
		String[][] resultArr = { { "Name" }, { "Alice" }, { "Bob" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = sqlServerJdbcDdl.sampleColumn(connection, "dbo", "Users", "Name");
			assertEquals(2, samples.size());
		}
	}

	@Test
	void sampleColumn_valueMatchesColumnName_skipsRow() throws SQLException {
		String[][] resultArr = { { "Name" }, { "Name" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = sqlServerJdbcDdl.sampleColumn(connection, "dbo", "Users", "Name");
			assertEquals(1, samples.size());
			assertTrue(samples.contains("Alice"));
		}
	}

}
