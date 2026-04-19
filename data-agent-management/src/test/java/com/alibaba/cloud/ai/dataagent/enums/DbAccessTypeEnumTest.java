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

import static org.junit.jupiter.api.Assertions.*;

class DbAccessTypeEnumTest {

	@Test
	void testGetCode() {
		assertEquals("jdbc", DbAccessTypeEnum.JDBC.getCode());
		assertEquals("sdk", DbAccessTypeEnum.SDK.getCode());
		assertEquals("data-api", DbAccessTypeEnum.DATA_API.getCode());
		assertEquals("fc-http", DbAccessTypeEnum.FC_HTTP.getCode());
		assertEquals("in-memory", DbAccessTypeEnum.MEMORY.getCode());
	}

	@Test
	void testOf_validCode() {
		assertEquals(DbAccessTypeEnum.JDBC, DbAccessTypeEnum.of("jdbc"));
		assertEquals(DbAccessTypeEnum.SDK, DbAccessTypeEnum.of("sdk"));
		assertEquals(DbAccessTypeEnum.DATA_API, DbAccessTypeEnum.of("data-api"));
		assertEquals(DbAccessTypeEnum.FC_HTTP, DbAccessTypeEnum.of("fc-http"));
		assertEquals(DbAccessTypeEnum.MEMORY, DbAccessTypeEnum.of("in-memory"));
	}

	@Test
	void testOf_invalidCode() {
		assertNull(DbAccessTypeEnum.of("unknown"));
	}

	@Test
	void testOf_nullCode() {
		assertNull(DbAccessTypeEnum.of(null));
	}

	@Test
	void testOf_blankCode() {
		assertNull(DbAccessTypeEnum.of(""));
		assertNull(DbAccessTypeEnum.of("  "));
	}

	@Test
	void testValues() {
		assertEquals(5, DbAccessTypeEnum.values().length);
	}

}
