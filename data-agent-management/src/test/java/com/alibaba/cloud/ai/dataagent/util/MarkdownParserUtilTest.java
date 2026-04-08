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
package com.alibaba.cloud.ai.dataagent.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownParserUtilTest {

	@Test
	void noCodeBlock_returnsOriginal() {
		String input = "SELECT * FROM users";
		assertEquals(input, MarkdownParserUtil.extractRawText(input));
	}

	@Test
	void singleCodeBlock_returnsContent() {
		String input = "```sql\nSELECT * FROM users\n```";
		assertEquals("SELECT * FROM users\n", MarkdownParserUtil.extractRawText(input));
	}

	@Test
	void multipleCodeBlocks_returnsLastBlock() {
		String input = "<think>\n```sql\nSELECT 1\n```\n</think>\n```sql\nSELECT * FROM users\n```";
		assertEquals("SELECT * FROM users\n", MarkdownParserUtil.extractRawText(input));
	}

	@Test
	void thinkTagWithMultipleSql_returnsLastSql() {
		String input = "<think>\n```sql\nSELECT id FROM t\n```\nLet me refine.\n```sql\nSELECT name FROM t\n```\n</think>\n```sql\nSELECT * FROM orders WHERE id = 1\n```";
		assertEquals("SELECT * FROM orders WHERE id = 1\n", MarkdownParserUtil.extractRawText(input));
	}

	@Test
	void multipleThinkTags_returnsCodeBlockAfterLastThink() {
		String input = "<think>\n```sql\nSELECT 1\n```\n</think>\n<think>\n```sql\nSELECT 2\n```\n</think>\n```sql\nSELECT * FROM orders\n```";
		assertEquals("SELECT * FROM orders\n", MarkdownParserUtil.extractRawText(input));
	}

	@Test
	void thinkTagNoCodeBlockAfter_returnsPlainTextAfterThink() {
		String input = "<think>\n```sql\nSELECT 1\n```\n</think>\nSELECT * FROM users";
		assertEquals("SELECT * FROM users", MarkdownParserUtil.extractRawText(input));
	}

	@Test
	void extractText_normalizesNewlines() {
		String input = "```sql\nSELECT *\nFROM users\n```";
		String result = MarkdownParserUtil.extractText(input);
		assertFalse(result.contains("\n"));
		assertTrue(result.contains("SELECT *"));
	}

}
