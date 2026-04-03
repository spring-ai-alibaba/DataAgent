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
package com.alibaba.cloud.ai.dataagent.connector.impls.dameng;

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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DamengJdbcDdlTest {

	private DamengJdbcDdl damengJdbcDdl;

	@Mock
	private Connection connection;

	@BeforeEach
	void setUp() {
		damengJdbcDdl = new DamengJdbcDdl();
	}

	@Test
	void getDataSourceType_returnsDameng() {
		assertEquals(BizDataSourceTypeEnum.DAMENG, damengJdbcDdl.getDataSourceType());
	}

	@Test
	void showDatabases_returnsEmptyList() {
		List<DatabaseInfoBO> databases = damengJdbcDdl.showDatabases(connection);
		assertTrue(databases.isEmpty());
	}

	@Test
	void showSchemas_returnsSchemaList() throws SQLException {
		String[][] resultArr = { { "USERNAME" }, { "SYSDBA" }, { "TEST" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<SchemaInfoBO> schemas = damengJdbcDdl.showSchemas(connection);
			assertEquals(2, schemas.size());
			assertEquals("SYSDBA", schemas.get(0).getName());
		}
	}

	@Test
	void showSchemas_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "USERNAME" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			assertTrue(damengJdbcDdl.showSchemas(connection).isEmpty());
		}
	}

	@Test
	void showSchemas_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> damengJdbcDdl.showSchemas(connection));
		}
	}

	@Test
	void showTables_withPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME" }, { "USERS" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = damengJdbcDdl.showTables(connection, "SYSDBA", "USER");
			assertEquals(1, tables.size());
			assertEquals("USERS", tables.get(0).getName());
		}
	}

	@Test
	void showTables_withoutPattern_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME" }, { "ORDERS" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = damengJdbcDdl.showTables(connection, "SYSDBA", null);
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_nullTables_returnsEmptyList() {
		assertTrue(damengJdbcDdl.fetchTables(connection, "SYSDBA", null).isEmpty());
	}

	@Test
	void fetchTables_emptyTables_returnsEmptyList() {
		assertTrue(damengJdbcDdl.fetchTables(connection, "SYSDBA", Collections.emptyList()).isEmpty());
	}

	@Test
	void fetchTables_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME" }, { "USERS" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = damengJdbcDdl.fetchTables(connection, "SYSDBA", Arrays.asList("USERS"));
			assertEquals(1, tables.size());
		}
	}

	@Test
	void showColumns_returnsColumnList() throws SQLException {
		String[][] resultArr = { { "COLUMN_NAME", "DATA_TYPE", "DATA_LENGTH", "NULLABLE" },
				{ "ID", "NUMBER", "22", "N" }, { "NAME", "VARCHAR2", "100", "Y" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ColumnInfoBO> columns = damengJdbcDdl.showColumns(connection, "SYSDBA", "USERS");
			assertEquals(2, columns.size());
			assertEquals("ID", columns.get(0).getName());
			assertTrue(columns.get(0).isNotnull());
			assertFalse(columns.get(1).isNotnull());
		}
	}

	@Test
	void showForeignKeys_nullTables_returnsEmptyList() {
		assertTrue(damengJdbcDdl.showForeignKeys(connection, "SYSDBA", null).isEmpty());
	}

	@Test
	void showForeignKeys_emptyTables_returnsEmptyList() {
		assertTrue(damengJdbcDdl.showForeignKeys(connection, "SYSDBA", Collections.emptyList()).isEmpty());
	}

	@Test
	void showForeignKeys_returnsForeignKeyList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "COLUMN_NAME", "CONSTRAINT_NAME", "R_OWNER", "R_CONSTRAINT_NAME" },
				{ "ORDERS", "USER_ID", "FK_ORDERS", "SYSDBA", "PK_USERS" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ForeignKeyInfoBO> fks = damengJdbcDdl.showForeignKeys(connection, "SYSDBA", Arrays.asList("ORDERS"));
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

			List<String> samples = damengJdbcDdl.sampleColumn(connection, "SYSDBA", "USERS", "NAME");
			assertTrue(samples.contains("Alice"));
			assertTrue(samples.contains("Bob"));
			assertEquals(2, samples.size());
		}
	}

	@Test
	void sampleColumn_sqlException_returnsEmptyList() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			List<String> samples = damengJdbcDdl.sampleColumn(connection, "SYSDBA", "USERS", "NAME");
			assertTrue(samples.isEmpty());
		}
	}

	@Test
	void scanTable_returnsResultSet() throws SQLException {
		ResultSetBO expected = ResultSetBO.builder().build();

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), anyString(), anyString()))
				.thenReturn(expected);

			ResultSetBO result = damengJdbcDdl.scanTable(connection, "SYSDBA", "USERS");
			assertNotNull(result);
		}
	}

	@Test
	void scanTable_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), anyString(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> damengJdbcDdl.scanTable(connection, "SYSDBA", "USERS"));
		}
	}

	@Test
	void showSchemas_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "USERNAME" }, {}, { "SYSDBA" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<SchemaInfoBO> schemas = damengJdbcDdl.showSchemas(connection);
			assertEquals(1, schemas.size());
		}
	}

	@Test
	void showTables_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			assertTrue(damengJdbcDdl.showTables(connection, "SYSDBA", null).isEmpty());
		}
	}

	@Test
	void showTables_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME" }, {}, { "USERS" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = damengJdbcDdl.showTables(connection, "SYSDBA", null);
			assertEquals(1, tables.size());
		}
	}

	@Test
	void showTables_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> damengJdbcDdl.showTables(connection, "SYSDBA", null));
		}
	}

	@Test
	void showTables_withBlankPattern_usesBaseSqlOnly() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME" }, { "USERS" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = damengJdbcDdl.showTables(connection, "SYSDBA", "");
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			assertTrue(damengJdbcDdl.fetchTables(connection, "SYSDBA", Arrays.asList("USERS")).isEmpty());
		}
	}

	@Test
	void fetchTables_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME" }, {}, { "USERS" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = damengJdbcDdl.fetchTables(connection, "SYSDBA", Arrays.asList("USERS"));
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class,
					() -> damengJdbcDdl.fetchTables(connection, "SYSDBA", Arrays.asList("USERS")));
		}
	}

	@Test
	void showColumns_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "COLUMN_NAME", "DATA_TYPE", "DATA_LENGTH", "NULLABLE" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			assertTrue(damengJdbcDdl.showColumns(connection, "SYSDBA", "USERS").isEmpty());
		}
	}

	@Test
	void showColumns_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "COLUMN_NAME", "DATA_TYPE", "DATA_LENGTH", "NULLABLE" }, {},
				{ "ID", "NUMBER", "22", "N" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ColumnInfoBO> columns = damengJdbcDdl.showColumns(connection, "SYSDBA", "USERS");
			assertEquals(1, columns.size());
		}
	}

	@Test
	void showColumns_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> damengJdbcDdl.showColumns(connection, "SYSDBA", "USERS"));
		}
	}

	@Test
	void showForeignKeys_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "COLUMN_NAME", "CONSTRAINT_NAME", "R_OWNER", "R_CONSTRAINT_NAME" }, {},
				{ "ORDERS", "USER_ID", "FK_ORDERS", "SYSDBA", "PK_USERS" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ForeignKeyInfoBO> fks = damengJdbcDdl.showForeignKeys(connection, "SYSDBA", Arrays.asList("ORDERS"));
			assertEquals(1, fks.size());
		}
	}

	@Test
	void showForeignKeys_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class,
					() -> damengJdbcDdl.showForeignKeys(connection, "SYSDBA", Arrays.asList("ORDERS")));
		}
	}

	@Test
	void sampleColumn_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "NAME" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			assertTrue(damengJdbcDdl.sampleColumn(connection, "SYSDBA", "USERS", "NAME").isEmpty());
		}
	}

	@Test
	void sampleColumn_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "NAME" }, {}, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = damengJdbcDdl.sampleColumn(connection, "SYSDBA", "USERS", "NAME");
			assertEquals(1, samples.size());
		}
	}

	@Test
	void sampleColumn_valueMatchesColumnName_skipsRow() throws SQLException {
		String[][] resultArr = { { "NAME" }, { "NAME" }, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = damengJdbcDdl.sampleColumn(connection, "SYSDBA", "USERS", "NAME");
			assertEquals(1, samples.size());
			assertTrue(samples.contains("Alice"));
		}
	}

}
