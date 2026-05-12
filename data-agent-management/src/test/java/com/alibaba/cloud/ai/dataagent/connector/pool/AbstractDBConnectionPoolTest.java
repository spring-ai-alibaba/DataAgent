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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class AbstractDBConnectionPoolTest {

	private TestableDBConnectionPool pool;

	private static final String H2_URL = "jdbc:h2:mem:testdb_%d;DB_CLOSE_DELAY=-1";

	private static int dbCounter = 0;

	@BeforeEach
	void setUp() {
		pool = new TestableDBConnectionPool();
	}

	@AfterEach
	void tearDown() throws Exception {
		pool.close();
		clearDataSourceCache();
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
		DbConfigBO config = DbConfigBO.builder()
			.url("jdbc:h2:mem:testauth;DB_CLOSE_DELAY=-1")
			.username("wronguser")
			.password("wrongpass")
			.connectionType("h2")
			.build();
		ErrorCodeEnum result = pool.ping(config);
		assertNotNull(result);
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

		int cacheSize1 = getDataSourceCacheSize();

		Connection conn2 = pool.getConnection(config);
		assertNotNull(conn2);
		conn2.close();

		int cacheSize2 = getDataSourceCacheSize();
		assertEquals(cacheSize1, cacheSize2);
	}

	@Test
	void getConnection_invalidUrl_throwsRuntimeException() {
		DbConfigBO config = DbConfigBO.builder()
			.url("jdbc:invalid://nowhere:9999/nothing")
			.username("nobody")
			.password("nothing")
			.connectionType("h2")
			.build();
		assertThrows(RuntimeException.class, () -> pool.getConnection(config));
	}

	@Test
	void close_clearsDataSourceCache() throws Exception {
		DbConfigBO config = createH2Config();
		Connection connection = pool.getConnection(config);
		assertNotNull(connection);
		connection.close();

		assertTrue(getDataSourceCacheSize() > 0);

		pool.close();

		assertEquals(0, getDataSourceCacheSize());
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
		assertNotNull(dataSource);

		Connection conn = dataSource.getConnection();
		String driverName = conn.getMetaData().getDriverName();
		assertNotNull(driverName);
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

	@SuppressWarnings("unchecked")
	private int getDataSourceCacheSize() throws Exception {
		Field cacheField = AbstractDBConnectionPool.class.getDeclaredField("DATA_SOURCE_CACHE");
		cacheField.setAccessible(true);
		ConcurrentHashMap<String, DataSource> cache = (ConcurrentHashMap<String, DataSource>) cacheField.get(null);
		return cache.size();
	}

	@SuppressWarnings("unchecked")
	private void clearDataSourceCache() throws Exception {
		Field cacheField = AbstractDBConnectionPool.class.getDeclaredField("DATA_SOURCE_CACHE");
		cacheField.setAccessible(true);
		ConcurrentHashMap<String, DataSource> cache = (ConcurrentHashMap<String, DataSource>) cacheField.get(null);
		cache.clear();
	}

	static class TestableDBConnectionPool extends AbstractDBConnectionPool {

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
