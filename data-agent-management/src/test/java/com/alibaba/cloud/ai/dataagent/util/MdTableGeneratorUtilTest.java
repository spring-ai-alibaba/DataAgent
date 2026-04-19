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

import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MdTableGeneratorUtilTest {

	@Test
	void testGenerateTable_fromArray() {
		String[][] data = { { "id", "name" }, { "1", "Alice" }, { "2", "Bob" } };

		String result = MdTableGeneratorUtil.generateTable(data);

		assertTrue(result.contains("| id | name |"));
		assertTrue(result.contains("|---|---|"));
		assertTrue(result.contains("| 1 | Alice |"));
		assertTrue(result.contains("| 2 | Bob |"));
	}

	@Test
	void testGenerateTable_null() {
		assertEquals("", MdTableGeneratorUtil.generateTable((String[][]) null));
	}

	@Test
	void testGenerateTable_empty() {
		assertEquals("", MdTableGeneratorUtil.generateTable(new String[0][]));
	}

	@Test
	void testGenerateTable_headerOnly() {
		String[][] data = { { "id", "name" } };
		String result = MdTableGeneratorUtil.generateTable(data);

		assertTrue(result.contains("| id | name |"));
		assertTrue(result.contains("|---|---|"));
	}

	@Test
	void testGenerateTable_fromResultSetBO() {
		ResultSetBO bo = new ResultSetBO();
		bo.setColumn(List.of("id", "name"));
		bo.setData(List.of(Map.of("id", "1", "name", "Alice")));

		String result = MdTableGeneratorUtil.generateTable(bo);

		assertTrue(result.contains("| id | name |"));
		assertTrue(result.contains("| 1 | Alice |"));
	}

}
