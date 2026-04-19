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
package com.alibaba.cloud.ai.dataagent.connector.impls.oracle;

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
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OracleJdbcDdlTest {

	private OracleJdbcDdl oracleJdbcDdl;

	@Mock
	private Connection connection;

	@Mock
	private DatabaseMetaData databaseMetaData;

	@BeforeEach
	void setUp() {
		oracleJdbcDdl = new OracleJdbcDdl();
	}

	@Test
	void getDataSourceType_returnsOracle() {
		assertEquals(BizDataSourceTypeEnum.ORACLE, oracleJdbcDdl.getDataSourceType());
	}

	@Test
	void showDatabases_returnsUserList() throws SQLException {
		String[][] resultArr = { { "USERNAME" }, { "HR" }, { "SYSTEM" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<DatabaseInfoBO> databases = oracleJdbcDdl.showDatabases(connection);
			assertEquals(2, databases.size());
			assertEquals("HR", databases.get(0).getName());
		}
	}

	@Test
	void showSchemas_returnsSchemaList() throws SQLException {
		String[][] resultArr = { { "USERNAME" }, { "HR" }, { "SCOTT" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<SchemaInfoBO> schemas = oracleJdbcDdl.showSchemas(connection);
			assertEquals(2, schemas.size());
		}
	}

	@Test
	void showTables_withSchema_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "COMMENTS" }, { "EMPLOYEES", "Employee table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString(), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = oracleJdbcDdl.showTables(connection, "HR", null);
			assertEquals(1, tables.size());
			assertEquals("EMPLOYEES", tables.get(0).getName());
		}
	}

	@Test
	void showTables_withPattern_returnsFilteredTables() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "COMMENTS" }, { "EMPLOYEES", "Employee table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString(), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = oracleJdbcDdl.showTables(connection, "HR", "EMP");
			assertEquals(1, tables.size());
		}
	}

	@Test
	void showTables_usesConnectionSchemaWhenParamBlank() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "COMMENTS" } };

		when(connection.getSchema()).thenReturn("HR");

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString(), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = oracleJdbcDdl.showTables(connection, "", null);
			assertTrue(tables.isEmpty());
		}
	}

	@Test
	void showTables_usesUserNameWhenSchemaBlank() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "COMMENTS" } };

		when(connection.getSchema()).thenReturn(null);
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getUserName()).thenReturn("hr");

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString(), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = oracleJdbcDdl.showTables(connection, null, null);
			assertTrue(tables.isEmpty());
		}
	}

	@Test
	void fetchTables_nullTables_returnsEmptyList() {
		List<TableInfoBO> tables = oracleJdbcDdl.fetchTables(connection, "HR", null);
		assertTrue(tables.isEmpty());
	}

	@Test
	void fetchTables_emptyTables_returnsEmptyList() {
		List<TableInfoBO> tables = oracleJdbcDdl.fetchTables(connection, "HR", Collections.emptyList());
		assertTrue(tables.isEmpty());
	}

	@Test
	void fetchTables_returnsTableList() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "COMMENTS" }, { "EMPLOYEES", "Employee table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = oracleJdbcDdl.fetchTables(connection, "HR", Arrays.asList("EMPLOYEES"));
			assertEquals(1, tables.size());
		}
	}

	@Test
	void showColumns_returnsColumnList() throws SQLException {
		String[][] resultArr = { { "COLUMN_NAME", "COMMENTS", "DATA_TYPE", "IS_PRIMARY", "IS_NOT_NULL" },
				{ "EMPLOYEE_ID", "PK", "NUMBER", "true", "true" },
				{ "FIRST_NAME", "First name", "VARCHAR2", "false", "false" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ColumnInfoBO> columns = oracleJdbcDdl.showColumns(connection, "HR", "employees");
			assertEquals(2, columns.size());
			assertEquals("EMPLOYEE_ID", columns.get(0).getName());
			assertTrue(columns.get(0).isPrimary());
		}
	}

	@Test
	void showColumns_rowTooShort_skipsRow() throws SQLException {
		String[][] resultArr = { { "COLUMN_NAME", "COMMENTS", "DATA_TYPE", "IS_PRIMARY", "IS_NOT_NULL" },
				{ "EMPLOYEE_ID", "PK" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ColumnInfoBO> columns = oracleJdbcDdl.showColumns(connection, "HR", "employees");
			assertTrue(columns.isEmpty());
		}
	}

	@Test
	void showForeignKeys_nullTables_returnsEmptyList() {
		List<ForeignKeyInfoBO> fks = oracleJdbcDdl.showForeignKeys(connection, "HR", null);
		assertTrue(fks.isEmpty());
	}

	@Test
	void showForeignKeys_returnsForeignKeyList() throws SQLException {
		String[][] resultArr = {
				{ "TABLE_NAME", "COLUMN_NAME", "CONSTRAINT_NAME", "REFERENCED_TABLE_NAME", "REFERENCED_COLUMN_NAME" },
				{ "ORDERS", "CUSTOMER_ID", "FK_ORDERS_CUST", "CUSTOMERS", "ID" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ForeignKeyInfoBO> fks = oracleJdbcDdl.showForeignKeys(connection, "HR",
					Arrays.asList("ORDERS", "CUSTOMERS"));
			assertEquals(1, fks.size());
			assertEquals("ORDERS", fks.get(0).getTable());
		}
	}

	@Test
	void sampleColumn_returnsSamples() throws SQLException {
		String[][] resultArr = { { "NAME" }, { "Alice" }, { "Bob" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = oracleJdbcDdl.sampleColumn(connection, "HR", "employees", "name");
			assertEquals(2, samples.size());
		}
	}

	@Test
	void sampleColumn_filtersNullValues() throws SQLException {
		String[][] resultArr = { { "NAME" }, { null }, { "Bob" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = oracleJdbcDdl.sampleColumn(connection, "HR", "employees", "name");
			assertEquals(1, samples.size());
			assertTrue(samples.contains("Bob"));
		}
	}

	@Test
	void sampleColumn_sqlException_returnsEmptyList() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			List<String> samples = oracleJdbcDdl.sampleColumn(connection, "HR", "employees", "name");
			assertTrue(samples.isEmpty());
		}
	}

	@Test
	void scanTable_returnsResultSet() throws SQLException {
		ResultSetBO expected = ResultSetBO.builder().build();

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), anyString(), anyString()))
				.thenReturn(expected);

			ResultSetBO result = oracleJdbcDdl.scanTable(connection, "HR", "employees");
			assertNotNull(result);
		}
	}

	@Test
	void scanTable_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnObject(any(Connection.class), anyString(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> oracleJdbcDdl.scanTable(connection, "HR", "employees"));
		}
	}

	@Test
	void showDatabases_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "USERNAME" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			assertTrue(oracleJdbcDdl.showDatabases(connection).isEmpty());
		}
	}

	@Test
	void showDatabases_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> oracleJdbcDdl.showDatabases(connection));
		}
	}

	@Test
	void showDatabases_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "USERNAME" }, {}, { "HR" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<DatabaseInfoBO> databases = oracleJdbcDdl.showDatabases(connection);
			assertEquals(1, databases.size());
		}
	}

	@Test
	void showSchemas_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "USERNAME" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			assertTrue(oracleJdbcDdl.showSchemas(connection).isEmpty());
		}
	}

	@Test
	void showSchemas_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> oracleJdbcDdl.showSchemas(connection));
		}
	}

	@Test
	void showSchemas_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "USERNAME" }, {}, { "HR" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<SchemaInfoBO> schemas = oracleJdbcDdl.showSchemas(connection);
			assertEquals(1, schemas.size());
		}
	}

	@Test
	void showTables_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "COMMENTS" }, {}, { "EMPLOYEES", "Employee table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString(), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = oracleJdbcDdl.showTables(connection, "HR", null);
			assertEquals(1, tables.size());
		}
	}

	@Test
	void showTables_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> oracleJdbcDdl.showTables(connection, "HR", null));
		}
	}

	@Test
	void showTables_singleColumnRow_handlesGracefully() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "COMMENTS" }, { "EMPLOYEES" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString(), anyString()))
				.thenReturn(resultArr);

			List<TableInfoBO> tables = oracleJdbcDdl.showTables(connection, "HR", null);
			assertEquals(1, tables.size());
			assertNull(tables.get(0).getDescription());
		}
	}

	@Test
	void fetchTables_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "TABLE_NAME", "COMMENTS" }, {}, { "EMPLOYEES", "Employee table" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString())).thenReturn(resultArr);

			List<TableInfoBO> tables = oracleJdbcDdl.fetchTables(connection, "HR", Arrays.asList("EMPLOYEES"));
			assertEquals(1, tables.size());
		}
	}

	@Test
	void fetchTables_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class,
					() -> oracleJdbcDdl.fetchTables(connection, "HR", Arrays.asList("EMPLOYEES")));
		}
	}

	@Test
	void showColumns_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "COLUMN_NAME", "COMMENTS", "DATA_TYPE", "IS_PRIMARY", "IS_NOT_NULL" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ColumnInfoBO> columns = oracleJdbcDdl.showColumns(connection, "HR", "employees");
			assertTrue(columns.isEmpty());
		}
	}

	@Test
	void showColumns_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class, () -> oracleJdbcDdl.showColumns(connection, "HR", "employees"));
		}
	}

	@Test
	void showForeignKeys_emptyTables_returnsEmptyList() {
		List<ForeignKeyInfoBO> fks = oracleJdbcDdl.showForeignKeys(connection, "HR", Collections.emptyList());
		assertTrue(fks.isEmpty());
	}

	@Test
	void showForeignKeys_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = {
				{ "TABLE_NAME", "COLUMN_NAME", "CONSTRAINT_NAME", "REFERENCED_TABLE_NAME", "REFERENCED_COLUMN_NAME" },
				{}, { "ORDERS", "CUSTOMER_ID", "FK_ORDERS_CUST", "CUSTOMERS", "ID" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ForeignKeyInfoBO> fks = oracleJdbcDdl.showForeignKeys(connection, "HR",
					Arrays.asList("ORDERS", "CUSTOMERS"));
			assertEquals(1, fks.size());
		}
	}

	@Test
	void showForeignKeys_rowTooShort_skipsRow() throws SQLException {
		String[][] resultArr = {
				{ "TABLE_NAME", "COLUMN_NAME", "CONSTRAINT_NAME", "REFERENCED_TABLE_NAME", "REFERENCED_COLUMN_NAME" },
				{ "ORDERS", "CUSTOMER_ID" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<ForeignKeyInfoBO> fks = oracleJdbcDdl.showForeignKeys(connection, "HR",
					Arrays.asList("ORDERS", "CUSTOMERS"));
			assertTrue(fks.isEmpty());
		}
	}

	@Test
	void showForeignKeys_sqlException_throwsRuntimeException() throws SQLException {
		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenThrow(new SQLException("error"));

			assertThrows(RuntimeException.class,
					() -> oracleJdbcDdl.showForeignKeys(connection, "HR", Arrays.asList("ORDERS")));
		}
	}

	@Test
	void sampleColumn_emptyResult_returnsEmptyList() throws SQLException {
		String[][] resultArr = { { "NAME" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = oracleJdbcDdl.sampleColumn(connection, "HR", "employees", "name");
			assertTrue(samples.isEmpty());
		}
	}

	@Test
	void sampleColumn_emptyRowInResults_skipsRow() throws SQLException {
		String[][] resultArr = { { "NAME" }, {}, { "Alice" } };

		try (MockedStatic<SqlExecutor> ms = mockStatic(SqlExecutor.class)) {
			ms.when(() -> SqlExecutor.executeSqlAndReturnArr(any(Connection.class), any(), anyString()))
				.thenReturn(resultArr);

			List<String> samples = oracleJdbcDdl.sampleColumn(connection, "HR", "employees", "name");
			assertEquals(1, samples.size());
		}
	}

}
