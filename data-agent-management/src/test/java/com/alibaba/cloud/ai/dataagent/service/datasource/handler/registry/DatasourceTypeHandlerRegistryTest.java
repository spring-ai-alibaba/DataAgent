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
package com.alibaba.cloud.ai.dataagent.service.datasource.handler.registry;

import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasourceTypeHandlerRegistryTest {

	private DatasourceTypeHandlerRegistry registry;

	private DatasourceTypeHandler mysqlHandler;

	private DatasourceTypeHandler pgHandler;

	@BeforeEach
	void setUp() {
		mysqlHandler = new StubHandler("mysql");
		pgHandler = new StubHandler("postgresql");
		registry = new DatasourceTypeHandlerRegistry(Arrays.asList(mysqlHandler, pgHandler));
	}

	@Test
	void getRequired_returnsRegisteredHandler() {
		DatasourceTypeHandler result = registry.getRequired("mysql");
		assertEquals(mysqlHandler, result);
	}

	@Test
	void getRequired_isCaseInsensitive() {
		DatasourceTypeHandler result = registry.getRequired("MYSQL");
		assertEquals(mysqlHandler, result);
	}

	@Test
	void getRequired_trimsWhitespace() {
		DatasourceTypeHandler result = registry.getRequired("  mysql  ");
		assertEquals(mysqlHandler, result);
	}

	@Test
	void getRequired_throwsOnUnknownType() {
		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> registry.getRequired("oracle"));
		assertTrue(ex.getMessage().contains("oracle"));
	}

	@Test
	void getRequired_throwsOnBlankType() {
		assertThrows(IllegalArgumentException.class, () -> registry.getRequired(""));
		assertThrows(IllegalArgumentException.class, () -> registry.getRequired("   "));
		assertThrows(IllegalArgumentException.class, () -> registry.getRequired(null));
	}

	@Test
	void isRegistered_returnsTrueForKnownType() {
		assertTrue(registry.isRegistered("mysql"));
		assertTrue(registry.isRegistered("postgresql"));
	}

	@Test
	void isRegistered_returnsFalseForUnknownType() {
		assertFalse(registry.isRegistered("oracle"));
		assertFalse(registry.isRegistered("unknown"));
	}

	@Test
	void isRegistered_isCaseInsensitive() {
		assertTrue(registry.isRegistered("MYSQL"));
		assertTrue(registry.isRegistered("PostgreSQL"));
	}

	@Test
	void register_addsNewHandler() {
		DatasourceTypeHandler oracleHandler = new StubHandler("oracle");
		registry.register(oracleHandler);
		assertTrue(registry.isRegistered("oracle"));
		assertEquals(oracleHandler, registry.getRequired("oracle"));
	}

	@Test
	void constructor_withEmptyList_createsEmptyRegistry() {
		DatasourceTypeHandlerRegistry emptyRegistry = new DatasourceTypeHandlerRegistry(Collections.emptyList());
		assertFalse(emptyRegistry.isRegistered("mysql"));
	}

	private static class StubHandler implements DatasourceTypeHandler {

		private final String name;

		StubHandler(String name) {
			this.name = name;
		}

		@Override
		public String typeName() {
			return name;
		}

	}

}
