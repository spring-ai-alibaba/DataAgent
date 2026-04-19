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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DBConnectionPoolFactoryTest {

	private DBConnectionPoolFactory factory;

	private DBConnectionPool jdbcPool;

	private DBConnectionPool memoryPool;

	@BeforeEach
	void setUp() {
		jdbcPool = new StubDBConnectionPool("jdbc", "mysql", "postgresql");
		memoryPool = new StubDBConnectionPool("in-memory", "h2");
		factory = new DBConnectionPoolFactory(Arrays.asList(jdbcPool, memoryPool));
	}

	@Test
	void getPoolByType_returnsDirectMatch() {
		DBConnectionPool result = factory.getPoolByType("jdbc");
		assertEquals(jdbcPool, result);
	}

	@Test
	void getPoolByType_returnsDirectMatchForMemory() {
		DBConnectionPool result = factory.getPoolByType("in-memory");
		assertEquals(memoryPool, result);
	}

	@Test
	void getPoolByType_fallsBackToSupportedDataSourceType() {
		DBConnectionPool result = factory.getPoolByType("mysql");
		assertEquals(jdbcPool, result);
	}

	@Test
	void getPoolByType_returnsNullWhenNotFound() {
		DBConnectionPool result = factory.getPoolByType("unknown");
		assertNull(result);
	}

	@Test
	void getPoolByDbType_returnsMatchingPool() {
		DBConnectionPool result = factory.getPoolByDbType("mysql");
		assertEquals(jdbcPool, result);
	}

	@Test
	void getPoolByDbType_returnsH2Pool() {
		DBConnectionPool result = factory.getPoolByDbType("h2");
		assertEquals(memoryPool, result);
	}

	@Test
	void getPoolByDbType_throwsOnUnknownType() {
		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> factory.getPoolByDbType("unknown"));
		assertTrue(ex.getMessage().contains("unknown"));
	}

	@Test
	void isRegistered_returnsTrueForRegisteredType() {
		assertTrue(factory.isRegistered("jdbc"));
		assertTrue(factory.isRegistered("in-memory"));
	}

	@Test
	void isRegistered_returnsFalseForUnregisteredType() {
		assertFalse(factory.isRegistered("unknown"));
		assertFalse(factory.isRegistered("mysql"));
	}

	@Test
	void register_addsNewPool() {
		DBConnectionPool newPool = new StubDBConnectionPool("sdk", "oracle");
		factory.register(newPool);
		assertTrue(factory.isRegistered("sdk"));
		assertEquals(newPool, factory.getPoolByType("sdk"));
	}

	@Test
	void constructor_withEmptyList_createsEmptyFactory() {
		DBConnectionPoolFactory emptyFactory = new DBConnectionPoolFactory(Collections.emptyList());
		assertFalse(emptyFactory.isRegistered("jdbc"));
		assertNull(emptyFactory.getPoolByType("jdbc"));
	}

	@Test
	void getPoolByType_prefersDirectMatchOverSupportedType() {
		DBConnectionPool directPool = new StubDBConnectionPool("mysql", "mysql");
		factory.register(directPool);
		DBConnectionPool result = factory.getPoolByType("mysql");
		assertEquals(directPool, result);
	}

	private static class StubDBConnectionPool implements DBConnectionPool {

		private final String poolType;

		private final String[] supportedTypes;

		StubDBConnectionPool(String poolType, String... supportedTypes) {
			this.poolType = poolType;
			this.supportedTypes = supportedTypes;
		}

		@Override
		public ErrorCodeEnum ping(DbConfigBO config) {
			return null;
		}

		@Override
		public Connection getConnection(DbConfigBO config) {
			return null;
		}

		@Override
		public boolean supportedDataSourceType(String type) {
			for (String supported : supportedTypes) {
				if (supported.equals(type)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public String getConnectionPoolType() {
			return poolType;
		}

		@Override
		public void close() {
		}

	}

}
