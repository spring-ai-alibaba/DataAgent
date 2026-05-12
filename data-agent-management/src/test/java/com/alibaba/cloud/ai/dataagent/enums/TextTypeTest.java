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
package com.alibaba.cloud.ai.dataagent.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextTypeTest {

	@Test
	void enumValues_haveCorrectCount() {
		assertEquals(6, TextType.values().length);
	}

	@Test
	void json_hasCorrectSigns() {
		assertEquals("$$$json", TextType.JSON.getStartSign());
		assertEquals("$$$", TextType.JSON.getEndSign());
	}

	@Test
	void python_hasCorrectSigns() {
		assertEquals("$$$python", TextType.PYTHON.getStartSign());
		assertEquals("$$$", TextType.PYTHON.getEndSign());
	}

	@Test
	void sql_hasCorrectSigns() {
		assertEquals("$$$sql", TextType.SQL.getStartSign());
		assertEquals("$$$", TextType.SQL.getEndSign());
	}

	@Test
	void markDown_hasCorrectSigns() {
		assertEquals("$$$markdown-report", TextType.MARK_DOWN.getStartSign());
		assertEquals("$$$/markdown-report", TextType.MARK_DOWN.getEndSign());
	}

	@Test
	void resultSet_hasCorrectSigns() {
		assertEquals("$$$result_set", TextType.RESULT_SET.getStartSign());
		assertEquals("$$$", TextType.RESULT_SET.getEndSign());
	}

	@Test
	void text_hasNullSigns() {
		assertNull(TextType.TEXT.getStartSign());
		assertNull(TextType.TEXT.getEndSign());
	}

	@Test
	void getType_fromText_matchesStartSign() {
		assertEquals(TextType.JSON, TextType.getType(TextType.TEXT, "$$$json"));
		assertEquals(TextType.PYTHON, TextType.getType(TextType.TEXT, "$$$python"));
		assertEquals(TextType.SQL, TextType.getType(TextType.TEXT, "$$$sql"));
		assertEquals(TextType.MARK_DOWN, TextType.getType(TextType.TEXT, "$$$markdown-report"));
		assertEquals(TextType.RESULT_SET, TextType.getType(TextType.TEXT, "$$$result_set"));
	}

	@Test
	void getType_fromText_noMatch_returnsText() {
		assertEquals(TextType.TEXT, TextType.getType(TextType.TEXT, "random content"));
	}

	@Test
	void getType_fromNonText_matchesEndSign_returnsText() {
		assertEquals(TextType.TEXT, TextType.getType(TextType.JSON, "$$$"));
		assertEquals(TextType.TEXT, TextType.getType(TextType.PYTHON, "$$$"));
		assertEquals(TextType.TEXT, TextType.getType(TextType.SQL, "$$$"));
		assertEquals(TextType.TEXT, TextType.getType(TextType.MARK_DOWN, "$$$/markdown-report"));
		assertEquals(TextType.TEXT, TextType.getType(TextType.RESULT_SET, "$$$"));
	}

	@Test
	void getType_fromNonText_noMatch_returnsOriginal() {
		assertEquals(TextType.JSON, TextType.getType(TextType.JSON, "some data"));
		assertEquals(TextType.PYTHON, TextType.getType(TextType.PYTHON, "some code"));
	}

	@Test
	void getTypeByStartSign_matchesKnownSigns() {
		assertEquals(TextType.JSON, TextType.getTypeByStratSign("$$$json"));
		assertEquals(TextType.PYTHON, TextType.getTypeByStratSign("$$$python"));
		assertEquals(TextType.SQL, TextType.getTypeByStratSign("$$$sql"));
		assertEquals(TextType.MARK_DOWN, TextType.getTypeByStratSign("$$$markdown-report"));
		assertEquals(TextType.RESULT_SET, TextType.getTypeByStratSign("$$$result_set"));
	}

	@Test
	void getTypeByStartSign_unknown_returnsText() {
		assertEquals(TextType.TEXT, TextType.getTypeByStratSign("unknown"));
	}

}
