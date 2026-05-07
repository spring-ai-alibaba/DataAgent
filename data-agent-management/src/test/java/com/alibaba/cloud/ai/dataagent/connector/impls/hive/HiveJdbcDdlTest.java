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
package com.alibaba.cloud.ai.dataagent.connector.impls.hive;

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
class HiveJdbcDdlTest {

	private HiveJdbcDdl hiveJdbcDdl;

	@Mock
	private Connection connection;

	@BeforeEach
	void setUp() {
		hiveJdbcDdl = new HiveJdbcDdl();
	}

	@Test
	void getDataSourceType_returnsHive() {
		assertEquals(BizDataSourceTypeEnum.HIVE, hiveJdbcDdl.getDataSourceType());
	}

	@Test
	void showDatabases_returnsResults() throws SQLException {
		String[][] resultArr = { { "database_name" }, { "default" }, { "analytics" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<DatabaseInfoBO> databases = hiveJdbcDdl.showDatabases(connection);
			assertEquals(2, databases.size());
			assertEquals("default", databases.get(0).getName());
		}
	}

	@Test
	void showDatabases_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "database_name" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			assertTrue(hiveJdbcDdl.showDatabases(connection).isEmpty());
		}
	}

	@Test
	void showDatabases_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> hiveJdbcDdl.showDatabases(connection));
		}
	}

	@Test
	void showSchemas_returnsEmptyList() {
		List<SchemaInfoBO> schemas = hiveJdbcDdl.showSchemas(connection);
		assertTrue(schemas.isEmpty());
	}

	@Test
	void showTables_withSchemaAndPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "tab_name" }, { "user_events" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = hiveJdbcDdl.showTables(connection, "analytics", "user*");
			assertEquals(1, tables.size());
			assertEquals("user_events", tables.get(0).getName());
		}
	}

	@Test
	void showTables_withoutSchemaOrPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "tab_name" }, { "events" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = hiveJdbcDdl.showTables(connection, null, null);
			assertEquals(1, tables.size());
		}
	}

	@Test
	void showTables_withSchemaOnly_returnsTableList() throws SQLException {
		String[][] resultArr = { { "tab_name" }, { "events" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = hiveJdbcDdl.showTables(connection, "analytics", null);
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_returnsTableListWithComments() throws SQLException {
		String[][] resultArr = { { "col_name", "data_type" }, { "comment", "Test table comment" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = hiveJdbcDdl.fetchTables(connection, "db", Arrays.asList("events"));
			assertEquals(1, tables.size());
			assertEquals("events", tables.get(0).getName());
			assertEquals("Test table comment", tables.get(0).getDescription());
		}
	}

	@Test
	void fetchTables_sqlException_returnsTableWithoutComment() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			List<TableInfoBO> tables = hiveJdbcDdl.fetchTables(connection, "db", Arrays.asList("events"));
			assertEquals(1, tables.size());
			assertEquals("events", tables.get(0).getName());
		}
	}

	@Test
	void fetchTables_withSchema_prefixesTableName() throws SQLException {
		String[][] resultArr = { { "col_name", "data_type" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = hiveJdbcDdl.fetchTables(connection, "mydb", Arrays.asList("t1"));
			assertEquals(1, tables.size());
		}
	}

	@Test
	void showColumns_returnsColumnList() throws SQLException {
		String[][] resultArr = { { "col_name", "data_type", "comment" }, { "id", "bigint", "Primary key" },
				{ "name", "string", "User name" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<ColumnInfoBO> columns = hiveJdbcDdl.showColumns(connection, "db", "users");
			assertEquals(2, columns.size());
			assertEquals("id", columns.get(0).getName());
			assertEquals("Primary key", columns.get(0).getDescription());
			assertFalse(columns.get(0).isPrimary());
			assertFalse(columns.get(0).isNotnull());
		}
	}

	@Test
	void showColumns_skipsBlankColumnNames() throws SQLException {
		String[][] resultArr = { { "col_name", "data_type", "comment" }, { "id", "bigint", "" }, { "", "string", "" },
				{ "# Partition Information", "", "" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<ColumnInfoBO> columns = hiveJdbcDdl.showColumns(connection, null, "users");
			assertEquals(1, columns.size());
			assertEquals("id", columns.get(0).getName());
		}
	}

	@Test
	void showColumns_withSchema_prefixesTableName() throws SQLException {
		String[][] resultArr = { { "col_name", "data_type" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<ColumnInfoBO> columns = hiveJdbcDdl.showColumns(connection, "mydb", "users");
			assertTrue(columns.isEmpty());
		}
	}

	@Test
	void showForeignKeys_returnsEmptyList() {
		List<ForeignKeyInfoBO> fks = hiveJdbcDdl.showForeignKeys(connection, "db", Arrays.asList("t1"));
		assertTrue(fks.isEmpty());
	}

	@Test
	void sampleColumn_returnsDedupedSamples() throws SQLException {
		String[][] resultArr = { { "name" }, { "Alice" }, { "Bob" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = hiveJdbcDdl.sampleColumn(connection, "db", "users", "name");
			assertTrue(samples.contains("Alice"));
			assertTrue(samples.contains("Bob"));
			assertEquals(2, samples.size());
		}
	}

	@Test
	void sampleColumn_withSchema_prefixesTableName() throws SQLException {
		String[][] resultArr = { { "name" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = hiveJdbcDdl.sampleColumn(connection, "mydb", "users", "name");
			assertEquals(1, samples.size());
		}
	}

	@Test
	void sampleColumn_sqlException_returnsEmptyList() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			List<String> samples = hiveJdbcDdl.sampleColumn(connection, "db", "users", "name");
			assertTrue(samples.isEmpty());
		}
	}

	@Test
	void scanTable_returnsResultSet() throws SQLException {
		ResultSetBO expected = ResultSetBO.builder().build();

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), anyString(), anyString()))
				.thenReturn(expected);

			ResultSetBO result = hiveJdbcDdl.scanTable(connection, "db", "users");
			assertNotNull(result);
		}
	}

	@Test
	void scanTable_withSchema_prefixesTableName() throws SQLException {
		ResultSetBO expected = ResultSetBO.builder().build();

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), anyString(), anyString()))
				.thenReturn(expected);

			ResultSetBO result = hiveJdbcDdl.scanTable(connection, "mydb", "users");
			assertNotNull(result);
		}
	}

	@Test
	void scanTable_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), anyString(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> hiveJdbcDdl.scanTable(connection, "db", "users"));
		}
	}

	@Test
	void scanTable_withoutSchema_usesTableNameOnly() throws SQLException {
		ResultSetBO expected = ResultSetBO.builder().build();

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), any(), anyString()))
				.thenReturn(expected);

			ResultSetBO result = hiveJdbcDdl.scanTable(connection, null, "users");
			assertNotNull(result);
		}
	}

	@Test
	void showDatabases_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "database_name" }, {}, { "default" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<DatabaseInfoBO> databases = hiveJdbcDdl.showDatabases(connection);
			assertEquals(1, databases.size());
		}
	}

	@Test
	void showTables_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "tab_name" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			assertTrue(hiveJdbcDdl.showTables(connection, null, null).isEmpty());
		}
	}

	@Test
	void showTables_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "tab_name" }, {}, { "events" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = hiveJdbcDdl.showTables(connection, null, null);
			assertEquals(1, tables.size());
		}
	}

	@Test
	void showTables_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> hiveJdbcDdl.showTables(connection, "db", null));
		}
	}

	@Test
	void showTables_withPatternOnly_returnsTableList() throws SQLException {
		String[][] resultArr = { { "tab_name" }, { "user_events" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = hiveJdbcDdl.showTables(connection, null, "user*");
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_withoutSchema_noPrefix() throws SQLException {
		String[][] resultArr = { { "col_name", "data_type" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = hiveJdbcDdl.fetchTables(connection, null, Arrays.asList("t1"));
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_noCommentFound_usesEmptyDescription() throws SQLException {
		String[][] resultArr = { { "col_name", "data_type" }, { "other_key", "other_value" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = hiveJdbcDdl.fetchTables(connection, "db", Arrays.asList("events"));
			assertEquals(1, tables.size());
			assertEquals("", tables.get(0).getDescription());
		}
	}

	@Test
	void showColumns_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "col_name", "data_type" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			assertTrue(hiveJdbcDdl.showColumns(connection, null, "users").isEmpty());
		}
	}

	@Test
	void showColumns_twoColumnRow_usesEmptyComment() throws SQLException {
		String[][] resultArr = { { "col_name", "data_type" }, { "id", "bigint" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<ColumnInfoBO> columns = hiveJdbcDdl.showColumns(connection, null, "users");
			assertEquals(1, columns.size());
			assertEquals("", columns.get(0).getDescription());
		}
	}

	@Test
	void showColumns_rowWithOneColumn_skipsRow() throws SQLException {
		String[][] resultArr = { { "col_name", "data_type", "comment" }, { "id" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			assertTrue(hiveJdbcDdl.showColumns(connection, null, "users").isEmpty());
		}
	}

	@Test
	void showColumns_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> hiveJdbcDdl.showColumns(connection, "db", "users"));
		}
	}

	@Test
	void sampleColumn_withoutSchema_usesTableNameOnly() throws SQLException {
		String[][] resultArr = { { "name" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = hiveJdbcDdl.sampleColumn(connection, null, "users", "name");
			assertEquals(1, samples.size());
		}
	}

	@Test
	void sampleColumn_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "name" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			assertTrue(hiveJdbcDdl.sampleColumn(connection, "db", "users", "name").isEmpty());
		}
	}

	@Test
	void sampleColumn_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "name" }, {}, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = hiveJdbcDdl.sampleColumn(connection, "db", "users", "name");
			assertEquals(1, samples.size());
		}
	}

	@Test
	void sampleColumn_valueMatchesColumnName_skipsRow() throws SQLException {
		String[][] resultArr = { { "name" }, { "name" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = hiveJdbcDdl.sampleColumn(connection, "db", "users", "name");
			assertEquals(1, samples.size());
			assertTrue(samples.contains("Alice"));
		}
	}

}
