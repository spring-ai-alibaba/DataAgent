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
package com.alibaba.cloud.ai.dataagent.connector.pool;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractDBConnectionPoolTest {

	private TestableDBConnectionPool pool;

	private static final String H2_URL = "jdbc:h2:mem:testdb_%d;DB_CLOSE_DELAY=-1";

	private static int dbCounter = 0;

	@BeforeEach
	void setUp() {
		pool = new TestableDBConnectionPool(new ConnectionRetryPolicy(3, ignored -> {
		}));
	}

	@AfterEach
	void tearDown() {
		pool.close();
	}

	@Test
	void ping_validH2Connection_returnsSuccess() {
		DbConfigBO config = createH2Config();
		ErrorCodeEnum result = pool.ping(config);
		assertEquals(ErrorCodeEnum.SUCCESS, result);
	}

	@Test
	void ping_invalidUrl_returnsErrorCode() {
		DbConfigBO config = DbConfigBO.builder()
			.url("jdbc:h2:mem:nonexistent;IFEXISTS=TRUE")
			.username("sa")
			.password("")
			.connectionType("h2")
			.build();
		ErrorCodeEnum result = pool.ping(config);
		assertNotEquals(ErrorCodeEnum.SUCCESS, result);
	}

	@Test
	void ping_invalidCredentials_returnsErrorCode() {
		String url = String.format(H2_URL, nextDbId());
		DbConfigBO validConfig = DbConfigBO.builder().url(url).username("sa").password("").connectionType("h2").build();
		assertEquals(ErrorCodeEnum.SUCCESS, pool.ping(validConfig));

		DbConfigBO config = DbConfigBO.builder()
			.url(url)
			.username("wronguser")
			.password("wrongpass")
			.connectionType("h2")
			.build();
		ErrorCodeEnum result = pool.ping(config);
		assertEquals(ErrorCodeEnum.PASSWORD_ERROR_28000, result);
	}

	@Test
	void getConnection_validConfig_returnsConnection() throws SQLException {
		DbConfigBO config = createH2Config();
		Connection connection = pool.getConnection(config);
		assertNotNull(connection);
		assertFalse(connection.isClosed());
		connection.close();
	}

	@Test
	void getConnection_calledTwice_reusesCachedDataSource() throws Exception {
		DbConfigBO config = createH2Config();

		Connection conn1 = pool.getConnection(config);
		assertNotNull(conn1);
		conn1.close();

		Connection conn2 = pool.getConnection(config);
		assertNotNull(conn2);
		conn2.close();

		assertEquals(1, pool.getDataSourceCreationCount());
	}

	@Test
	void getConnection_differentPasswordsWithSameHash_doNotShareDataSource() throws Exception {
		String url = String.format(H2_URL, nextDbId());
		DbConfigBO first = DbConfigBO.builder().url(url).username("sa").password("Aa").connectionType("h2").build();
		DbConfigBO second = DbConfigBO.builder().url(url).username("sa").password("BB").connectionType("h2").build();
		assertEquals(first.getPassword().hashCode(), second.getPassword().hashCode());

		try (Connection ignored = pool.getConnection(first)) {
			assertNotNull(ignored);
		}

		assertThrowsExactly(RuntimeException.class, () -> pool.getConnection(second));
		assertEquals(2, pool.getDataSourceCreationCount());
	}

	@Test
	void getConnection_failingDataSource_retriesThreeTimesAndThrows() throws SQLException {
		DataSource failingDataSource = mock(DataSource.class);
		when(failingDataSource.getConnection()).thenThrow(new SQLException("unavailable"));
		pool.useDataSource(failingDataSource);
		DbConfigBO config = DbConfigBO.builder()
			.url("jdbc:test:unavailable")
			.username("nobody")
			.password("nothing")
			.connectionType("h2")
			.build();

		RuntimeException error = assertThrowsExactly(RuntimeException.class, () -> pool.getConnection(config));

		assertTrue(error.getMessage().contains("3 attempts"));
		verify(failingDataSource, times(3)).getConnection();
	}

	@Test
	void getConnection_interruptedBackoff_restoresInterruptAndStopsRetrying() throws SQLException {
		TestableDBConnectionPool interruptedPool = new TestableDBConnectionPool(
				new ConnectionRetryPolicy(3, ignored -> {
					throw new InterruptedException("cancelled");
				}));
		DataSource failingDataSource = mock(DataSource.class);
		when(failingDataSource.getConnection()).thenThrow(new SQLException("unavailable"));
		interruptedPool.useDataSource(failingDataSource);

		try {
			IllegalStateException error = assertThrows(IllegalStateException.class,
					() -> interruptedPool.getConnection(createH2Config()));
			assertTrue(error.getMessage().contains("Interrupted"));
			assertTrue(Thread.currentThread().isInterrupted());
			verify(failingDataSource).getConnection();
		}
		finally {
			Thread.interrupted();
			interruptedPool.close();
		}
	}

	@Test
	void close_clearsDataSourceCache() throws Exception {
		DbConfigBO config = createH2Config();
		Connection connection = pool.getConnection(config);
		assertNotNull(connection);
		connection.close();

		assertEquals(1, pool.getDataSourceCreationCount());

		pool.close();

		Connection connectionAfterClose = pool.getConnection(config);
		connectionAfterClose.close();
		assertEquals(2, pool.getDataSourceCreationCount());
	}

	@Test
	void createdDataSource_returnsValidDataSource() throws Exception {
		String url = String.format(H2_URL, nextDbId());
		DataSource dataSource = pool.createdDataSource(url, "sa", "");
		assertNotNull(dataSource);
		Connection connection = dataSource.getConnection();
		assertNotNull(connection);
		assertFalse(connection.isClosed());
		connection.close();
	}

	@Test
	void createdDataSource_setsDriverCorrectly() throws Exception {
		String url = String.format(H2_URL, nextDbId());
		DataSource dataSource = pool.createdDataSource(url, "sa", "");
		assertInstanceOf(DruidDataSource.class, dataSource);
		DruidDataSource druidDataSource = (DruidDataSource) dataSource;
		assertEquals("org.h2.Driver", druidDataSource.getDriverClassName());
		assertEquals(url, druidDataSource.getUrl());
		assertEquals("sa", druidDataSource.getUsername());
		assertEquals(20, druidDataSource.getMaxActive());

		Connection conn = dataSource.getConnection();
		String driverName = conn.getMetaData().getDriverName();
		assertEquals("H2 JDBC Driver", driverName);
		conn.close();
	}

	@Test
	void getSelectSchemaSQL_containsSchemaName() {
		String sql = pool.getSelectSchemaSQL("my_schema");
		assertTrue(sql.contains("my_schema"));
		assertTrue(sql.contains("information_schema"));
	}

	@Test
	void supportedDataSourceType_matchesExpectedType() {
		assertTrue(pool.supportedDataSourceType("h2"));
		assertFalse(pool.supportedDataSourceType("mysql"));
	}

	@Test
	void getConnectionPoolType_returnsExpectedType() {
		assertEquals("h2", pool.getConnectionPoolType());
	}

	@Test
	void errorMapping_unknownState_returnsOthers() {
		ErrorCodeEnum result = pool.errorMapping("XXXXX");
		assertEquals(ErrorCodeEnum.OTHERS, result);
	}

	private DbConfigBO createH2Config() {
		return DbConfigBO.builder()
			.url(String.format(H2_URL, nextDbId()))
			.username("sa")
			.password("")
			.connectionType("h2")
			.build();
	}

	private static synchronized int nextDbId() {
		return ++dbCounter;
	}

	static class TestableDBConnectionPool extends AbstractDBConnectionPool {

		private final AtomicInteger dataSourceCreationCount = new AtomicInteger();

		private DataSource dataSource;

		TestableDBConnectionPool(ConnectionRetryPolicy retryPolicy) {
			super(retryPolicy);
		}

		void useDataSource(DataSource dataSource) {
			this.dataSource = dataSource;
		}

		int getDataSourceCreationCount() {
			return dataSourceCreationCount.get();
		}

		@Override
		public DataSource createdDataSource(String url, String username, String password) throws Exception {
			dataSourceCreationCount.incrementAndGet();
			return dataSource != null ? dataSource : super.createdDataSource(url, username, password);
		}

		@Override
		public String getDriver() {
			return "org.h2.Driver";
		}

		@Override
		public ErrorCodeEnum errorMapping(String sqlState) {
			if ("90013".equals(sqlState) || "90124".equals(sqlState)) {
				return ErrorCodeEnum.DATABASE_NOT_EXIST_3D000;
			}
			if ("28000".equals(sqlState)) {
				return ErrorCodeEnum.PASSWORD_ERROR_28000;
			}
			return ErrorCodeEnum.OTHERS;
		}

		@Override
		public boolean supportedDataSourceType(String type) {
			return "h2".equalsIgnoreCase(type);
		}

		@Override
		public String getConnectionPoolType() {
			return "h2";
		}

	}

}
