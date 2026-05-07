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
package com.alibaba.cloud.ai.dataagent.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColumnTypeUtilTest {

	@Test
	void testWrapType_numericTypes() {
		assertEquals("number", ColumnTypeUtil.wrapType("decimal"));
		assertEquals("number", ColumnTypeUtil.wrapType("int"));
		assertEquals("number", ColumnTypeUtil.wrapType("bigint"));
		assertEquals("number", ColumnTypeUtil.wrapType("bool"));
		assertEquals("number", ColumnTypeUtil.wrapType("bit"));
		assertEquals("number", ColumnTypeUtil.wrapType("boolean"));
		assertEquals("number", ColumnTypeUtil.wrapType("double"));
	}

	@Test
	void testWrapType_caseInsensitive() {
		assertEquals("number", ColumnTypeUtil.wrapType("DECIMAL"));
		assertEquals("number", ColumnTypeUtil.wrapType("INT"));
		assertEquals("number", ColumnTypeUtil.wrapType("BigInt"));
	}

	@Test
	void testWrapType_textTypes() {
		assertEquals("text", ColumnTypeUtil.wrapType("varchar(255)"));
		assertEquals("text", ColumnTypeUtil.wrapType("char(10)"));
		assertEquals("text", ColumnTypeUtil.wrapType("varchar"));
	}

	@Test
	void testWrapType_otherTypes() {
		assertEquals("date", ColumnTypeUtil.wrapType("date"));
		assertEquals("timestamp", ColumnTypeUtil.wrapType("timestamp"));
		assertEquals("blob", ColumnTypeUtil.wrapType("blob"));
	}

}
