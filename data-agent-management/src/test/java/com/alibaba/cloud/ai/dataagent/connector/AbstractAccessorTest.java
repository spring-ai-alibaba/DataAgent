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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AbstractAccessor;
import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import com.alibaba.cloud.ai.dataagent.connector.ddl.DdlFactory;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractAccessorTest {

	@Mock
	private DdlFactory ddlFactory;

	@Mock
	private DBConnectionPool dbConnectionPool;

	@Mock
	private Connection connection;

	@Mock
	private AbstractJdbcDdl ddlExecutor;

	@Mock
	private Statement statement;

	@Mock
	private ResultSet resultSet;

	@Mock
	private ResultSetMetaData resultSetMetaData;

	@Mock
	private DatabaseMetaData databaseMetaData;

	private TestableAccessor accessor;

	private DbConfigBO dbConfig;

	@BeforeEach
	void setUp() {
		accessor = new TestableAccessor(ddlFactory, dbConnectionPool);
		dbConfig = DbConfigBO.builder()
			.url("jdbc:mysql://localhost:3306/test")
			.username("user")
			.password("pass")
			.dialectType("mysql")
			.build();
	}

	@Test
	void executeSqlAndReturnObject_validQuery_returnsResultSetBO() throws Exception {
		when(dbConnectionPool.getConnection(dbConfig)).thenReturn(connection);
		when(ddlFactory.getDdlExecutorByDbConfig(dbConfig)).thenReturn(ddlExecutor);
		when(connection.createStatement()).thenReturn(statement);
		when(connection.getMetaData()).thenReturn(databaseMetaData);
		when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
		when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
		when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
		when(resultSetMetaData.getColumnCount()).thenReturn(1);
		when(resultSetMetaData.getColumnLabel(1)).thenReturn("1");
		when(resultSet.next()).thenReturn(true, false);
		when(resultSet.getString("1")).thenReturn("1");

		DbQueryParameter param = new DbQueryParameter();
		param.setSql("SELECT 1");

		ResultSetBO result = accessor.executeSqlAndReturnObject(dbConfig, param);

		assertNotNull(result);
		assertEquals(1, result.getColumn().size());
		assertEquals(1, result.getData().size());
	}

	@Test
	void accessDb_unknownMethod_throwsUnsupportedOperationException() {
		when(dbConnectionPool.getConnection(dbConfig)).thenReturn(connection);
		when(ddlFactory.getDdlExecutorByDbConfig(dbConfig)).thenReturn(ddlExecutor);

		DbQueryParameter param = new DbQueryParameter();
		assertThrows(UnsupportedOperationException.class, () -> accessor.accessDb(dbConfig, "unknownMethod", param));
	}

	@Test
	void getConnection_delegatesToConnectionPool() {
		when(dbConnectionPool.getConnection(dbConfig)).thenReturn(connection);

		Connection result = accessor.getConnection(dbConfig);

		assertSame(connection, result);
		verify(dbConnectionPool).getConnection(dbConfig);
	}

	private static class TestableAccessor extends AbstractAccessor {

		TestableAccessor(DdlFactory ddlFactory, DBConnectionPool dbConnectionPool) {
			super(ddlFactory, dbConnectionPool);
		}

		@Override
		public String getAccessorType() {
			return "test";
		}

		@Override
		public boolean supportedDataSourceType(String type) {
			return "mysql".equalsIgnoreCase(type);
		}

	}

}
