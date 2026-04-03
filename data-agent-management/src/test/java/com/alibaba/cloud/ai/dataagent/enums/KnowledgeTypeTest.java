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

class KnowledgeTypeTest {

	@Test
	void testGetCodeAndDescription() {
		assertEquals("DOCUMENT", KnowledgeType.DOCUMENT.getCode());
		assertEquals("文档类型", KnowledgeType.DOCUMENT.getDescription());
		assertEquals("QA", KnowledgeType.QA.getCode());
		assertEquals("问答类型", KnowledgeType.QA.getDescription());
		assertEquals("FAQ", KnowledgeType.FAQ.getCode());
		assertEquals("常见问题类型", KnowledgeType.FAQ.getDescription());
	}

	@Test
	void testFromCode_valid() {
		assertEquals(KnowledgeType.DOCUMENT, KnowledgeType.fromCode("DOCUMENT"));
		assertEquals(KnowledgeType.QA, KnowledgeType.fromCode("QA"));
		assertEquals(KnowledgeType.FAQ, KnowledgeType.fromCode("FAQ"));
	}

	@Test
	void testFromCode_invalid() {
		assertThrows(IllegalArgumentException.class, () -> KnowledgeType.fromCode("UNKNOWN"));
	}

	@Test
	void testFromCode_caseSensitive() {
		assertThrows(IllegalArgumentException.class, () -> KnowledgeType.fromCode("document"));
	}

	@Test
	void testValues() {
		assertEquals(3, KnowledgeType.values().length);
	}

}
