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
package com.alibaba.cloud.ai.dataagent.connector;

import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqlExecutorTest {

	@Mock
	private Connection connection;

	@Mock
	private Statement statement;

	@Mock
	private ResultSet resultSet;

	@Mock
	private ResultSetMetaData resultSetMetaData;

	@Mock
	private DatabaseMetaData databaseMetaData;

	@Test
	void executeSqlAndReturnObject_validSelect_returnsResults() throws SQLException {
		when(connection.createStatement()).thenReturn(statement);
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
		when(statement.executeQuery("SELECT id, name FROM users")).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(2);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("id");
		when(resultSetMetaData.getColumnLabel(2)).thenReturn("name");
		when(resultSet.next()).thenReturn(true, false);
		when(resultSet.getString("id")).thenReturn("1");
		when(resultSet.getString("name")).thenReturn("Alice");

		ResultSetBO result = SqlExecutor.executeSqlAndReturnObject(connection, null, "SELECT id, name FROM users");

		assertNotNull(result);
		assertEquals(2, result.getColumn().size());
		assertTrue(result.getColumn().contains("id"));
		assertTrue(result.getColumn().contains("name"));
		assertEquals(1, result.getData().size());
		assertEquals("1", result.getData().get(0).get("id"));
		assertEquals("Alice", result.getData().get(0).get("name"));
	}

	@Test
	void executeSqlAndReturnObject_emptyResult_returnsEmptyData() throws SQLException {
		when(connection.createStatement()).thenReturn(statement);
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
		when(statement.executeQuery("SELECT id FROM empty_table")).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(1);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("id");
		when(resultSet.next()).thenReturn(false);

		ResultSetBO result = SqlExecutor.executeSqlAndReturnObject(connection, null, "SELECT id FROM empty_table");

		assertNotNull(result);
		assertEquals(1, result.getColumn().size());
		assertTrue(result.getData().isEmpty());
	}

	@Test
	void executeSqlAndReturnObject_invalidSql_throwsSQLException() throws SQLException {
		when(connection.createStatement()).thenReturn(statement);
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
		when(statement.executeQuery("INVALID SQL")).thenThrow(new SQLException("syntax error"));

		assertThrows(SQLException.class,
				() -> SqlExecutor.executeSqlAndReturnObject(connection, null, "INVALID SQL"));
	}

	@Test
	void executeSqlAndReturnArr_validQuery_returnsStringArray() throws SQLException {
		when(connection.createStatement()).thenReturn(statement);
		when(statement.executeQuery("SELECT id FROM users")).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(1);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("id");
		when(resultSet.next()).thenReturn(true, false);
		when(resultSet.getString("id")).thenReturn("42");

		String[][] result = SqlExecutor.executeSqlAndReturnArr(connection, "SELECT id FROM users");

		assertNotNull(result);
		assertEquals(2, result.length);
		assertEquals("id", result[0][0]);
		assertEquals("42", result[1][0]);
	}

	@Test
	void executeSqlAndReturnObject_withPostgresqlSchema_setsSearchPath() throws SQLException {
		when(connection.createStatement()).thenReturn(statement);
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");
		when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(1);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("1");
		when(resultSet.next()).thenReturn(false);

		ResultSetBO result = SqlExecutor.executeSqlAndReturnObject(connection, "public", "SELECT 1");

		assertNotNull(result);
		verify(statement).execute("set search_path = 'public';");
	}

	@Test
	void executeSqlAndReturnObject_withH2Schema_usesSchema() throws SQLException {
		when(connection.createStatement()).thenReturn(statement);
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getDatabaseProductName()).thenReturn("H2");
		when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(1);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("1");
		when(resultSet.next()).thenReturn(false);

		ResultSetBO result = SqlExecutor.executeSqlAndReturnObject(connection, "PUBLIC", "SELECT 1");

		assertNotNull(result);
		verify(statement).execute("use PUBLIC;");
	}

	@Test
	void executeSqlAndReturnObject_withOracleSchema_altersSession() throws SQLException {
		when(connection.createStatement()).thenReturn(statement);
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getDatabaseProductName()).thenReturn("Oracle");
		when(statement.executeQuery("SELECT 1 FROM DUAL")).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(1);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("1");
		when(resultSet.next()).thenReturn(false);

		ResultSetBO result = SqlExecutor.executeSqlAndReturnObject(connection, "HR", "SELECT 1 FROM DUAL");

		assertNotNull(result);
		verify(statement).execute("ALTER SESSION SET CURRENT_SCHEMA = HR");
	}

	@Test
	void executeSqlAndReturnObject_withNullSchema_skipsSchemaSwitch() throws SQLException {
		when(connection.createStatement()).thenReturn(statement);
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");
		when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(1);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("1");
		when(resultSet.next()).thenReturn(false);

		ResultSetBO result = SqlExecutor.executeSqlAndReturnObject(connection, null, "SELECT 1");

		assertNotNull(result);
		verify(statement, never()).execute(anyString());
	}

	@Test
	void executeSqlAndReturnObject_withEmptySchema_skipsSchemaSwitch() throws SQLException {
		when(connection.createStatement()).thenReturn(statement);
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getDatabaseProductName()).thenReturn("H2");
		when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(1);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("1");
		when(resultSet.next()).thenReturn(false);

		ResultSetBO result = SqlExecutor.executeSqlAndReturnObject(connection, "", "SELECT 1");

		assertNotNull(result);
		verify(statement, never()).execute(anyString());
	}

	@Test
	void executeSqlAndReturnArr_withMysqlSchema_switchesDatabase() throws SQLException {
		when(connection.createStatement()).thenReturn(statement);
		when(connection.getCatalog()).thenReturn("original_db");
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
		when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(1);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("1");
		when(resultSet.next()).thenReturn(false);

		String[][] result = SqlExecutor.executeSqlAndReturnArr(connection, "test_db", "SELECT 1");

		assertNotNull(result);
		verify(statement).execute("use `test_db`;");
		verify(statement).execute("use `original_db`;");
	}

	@Test
	void executeSqlAndReturnArr_withPostgresqlSchema_setsSearchPath() throws SQLException {
		when(connection.createStatement()).thenReturn(statement);
		when(connection.getCatalog()).thenReturn("mydb");
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");
		when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(1);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("1");
		when(resultSet.next()).thenReturn(false);

		String[][] result = SqlExecutor.executeSqlAndReturnArr(connection, "public", "SELECT 1");

		assertNotNull(result);
		verify(statement).execute("set search_path = 'public';");
	}

	@Test
	void executeSqlAndReturnArr_withOracleSchema_altersSession() throws SQLException {
		when(connection.createStatement()).thenReturn(statement);
		when(connection.getCatalog()).thenReturn("orcl");
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getDatabaseProductName()).thenReturn("Oracle");
		when(statement.executeQuery("SELECT 1 FROM DUAL")).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(1);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("1");
		when(resultSet.next()).thenReturn(false);

		String[][] result = SqlExecutor.executeSqlAndReturnArr(connection, "HR", "SELECT 1 FROM DUAL");

		assertNotNull(result);
		verify(statement).execute("ALTER SESSION SET CURRENT_SCHEMA = HR");
	}

	@Test
	void executeSqlAndReturnArr_withNullSchema_skipsSchemaSwitch() throws SQLException {
		when(connection.createStatement()).thenReturn(statement);
		when(connection.getCatalog()).thenReturn("mydb");
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
		when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(1);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("1");
		when(resultSet.next()).thenReturn(false);

		String[][] result = SqlExecutor.executeSqlAndReturnArr(connection, null, "SELECT 1");

		assertNotNull(result);
		verify(statement, never()).execute(anyString());
	}

	@Test
	void constants_areCorrect() {
		assertEquals(1000, SqlExecutor.RESULT_SET_LIMIT);
		assertEquals(30, SqlExecutor.STATEMENT_TIMEOUT);
	}

}
