/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.enums;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseDialectEnumTest {

	@Test
	void testGetCode() {
		assertEquals("MySQL", DatabaseDialectEnum.MYSQL.getCode());
		assertEquals("PostgreSQL", DatabaseDialectEnum.POSTGRESQL.getCode());
		assertEquals("H2", DatabaseDialectEnum.H2.getCode());
		assertEquals("Dameng", DatabaseDialectEnum.DAMENG.getCode());
		assertEquals("SqlServer", DatabaseDialectEnum.SQL_SERVER.getCode());
		assertEquals("Oracle", DatabaseDialectEnum.ORACLE.getCode());
		assertEquals("Hive", DatabaseDialectEnum.HIVE.getCode());
		assertEquals("SQLite", DatabaseDialectEnum.SQLite.getCode());
	}

	@Test
	void testGetByCode_found() {
		Optional<DatabaseDialectEnum> result = DatabaseDialectEnum.getByCode("MySQL");
		assertTrue(result.isPresent());
		assertEquals(DatabaseDialectEnum.MYSQL, result.get());
	}

	@Test
	void testGetByCode_notFound() {
		Optional<DatabaseDialectEnum> result = DatabaseDialectEnum.getByCode("NonExistent");
		assertTrue(result.isEmpty());
	}

	@Test
	void testGetByCode_caseSensitive() {
		Optional<DatabaseDialectEnum> result = DatabaseDialectEnum.getByCode("mysql");
		assertTrue(result.isEmpty());
	}

	@Test
	void testValues() {
		assertEquals(8, DatabaseDialectEnum.values().length);
	}

	@Test
	void testValueOf() {
		assertEquals(DatabaseDialectEnum.MYSQL, DatabaseDialectEnum.valueOf("MYSQL"));
		assertEquals(DatabaseDialectEnum.POSTGRESQL, DatabaseDialectEnum.valueOf("POSTGRESQL"));
	}

}
