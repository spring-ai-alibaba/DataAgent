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
package com.alibaba.cloud.ai.dataagent.management.util;

import com.alibaba.cloud.ai.dataagent.util.ApiKeyUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyUtilTest {

	@Test
	void generate_returnsKeyWithPrefix() {
		String key = ApiKeyUtil.generate();
		assertTrue(key.startsWith("sk-"));
	}

	@Test
	void generate_returnsCorrectLength() {
		String key = ApiKeyUtil.generate();
		assertEquals(35, key.length());
	}

	@Test
	void generate_uniqueEachCall() {
		String key1 = ApiKeyUtil.generate();
		String key2 = ApiKeyUtil.generate();
		assertNotEquals(key1, key2);
	}

	@Test
	void generate_containsOnlyValidChars() {
		String key = ApiKeyUtil.generate();
		String body = key.substring(3);
		assertTrue(body.matches("[a-zA-Z0-9]+"));
	}

	@Test
	void mask_validKey_masksCorrectly() {
		String key = "sk-abcdefghijklmnopqrstuvwxyz123456";
		String masked = ApiKeyUtil.mask(key);
		assertEquals("****3456", masked);
	}

	@Test
	void mask_shortKey_returnsFourStars() {
		String masked = ApiKeyUtil.mask("sk-abc");
		assertEquals("****", masked);
	}

	@Test
	void mask_nullKey_returnsFourStars() {
		String masked = ApiKeyUtil.mask(null);
		assertEquals("****", masked);
	}

}
