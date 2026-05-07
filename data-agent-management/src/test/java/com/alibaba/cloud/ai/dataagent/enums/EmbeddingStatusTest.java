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

class EmbeddingStatusTest {

	@Test
	void testGetValue() {
		assertEquals("PENDING", EmbeddingStatus.PENDING.getValue());
		assertEquals("PROCESSING", EmbeddingStatus.PROCESSING.getValue());
		assertEquals("COMPLETED", EmbeddingStatus.COMPLETED.getValue());
		assertEquals("FAILED", EmbeddingStatus.FAILED.getValue());
	}

	@Test
	void testFromValue_valid() {
		assertEquals(EmbeddingStatus.PENDING, EmbeddingStatus.fromValue("PENDING"));
		assertEquals(EmbeddingStatus.PROCESSING, EmbeddingStatus.fromValue("PROCESSING"));
		assertEquals(EmbeddingStatus.COMPLETED, EmbeddingStatus.fromValue("COMPLETED"));
		assertEquals(EmbeddingStatus.FAILED, EmbeddingStatus.fromValue("FAILED"));
	}

	@Test
	void testFromValue_invalid() {
		assertThrows(IllegalArgumentException.class, () -> EmbeddingStatus.fromValue("UNKNOWN"));
	}

	@Test
	void testFromValue_caseSensitive() {
		assertThrows(IllegalArgumentException.class, () -> EmbeddingStatus.fromValue("pending"));
	}

	@Test
	void testValues() {
		assertEquals(4, EmbeddingStatus.values().length);
	}

}
