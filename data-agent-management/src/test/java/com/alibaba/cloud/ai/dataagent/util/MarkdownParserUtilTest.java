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

class MarkdownParserUtilTest {

	@Test
	void testExtractRawText_codeBlock() {
		String input = "```sql\nSELECT * FROM users;\n```";
		assertEquals("SELECT * FROM users;\n", MarkdownParserUtil.extractRawText(input));
	}

	@Test
	void testExtractRawText_noCodeBlock() {
		String input = "plain text without code block";
		assertEquals("plain text without code block", MarkdownParserUtil.extractRawText(input));
	}

	@Test
	void testExtractRawText_noClosingDelimiter() {
		String input = "```python\nprint('hello')";
		assertEquals("print('hello')", MarkdownParserUtil.extractRawText(input));
	}

	@Test
	void testExtractRawText_moreBackticks() {
		String input = "````\ncode here\n````";
		assertEquals("code here\n", MarkdownParserUtil.extractRawText(input));
	}

	@Test
	void testExtractText_replacesNewlines() {
		String input = "```sql\nSELECT *\nFROM users;\n```";
		String result = MarkdownParserUtil.extractText(input);
		assertFalse(result.contains("\n"));
	}

	@Test
	void testExtractText_noCodeBlock() {
		String input = "line1\nline2";
		String result = MarkdownParserUtil.extractText(input);
		assertEquals("line1 line2", result);
	}

	@Test
	void testExtractText_crlfHandling() {
		String input = "```\nline1\r\nline2\rline3\n```";
		String result = MarkdownParserUtil.extractText(input);
		assertFalse(result.contains("\r"));
		assertFalse(result.contains("\n"));
	}

}
