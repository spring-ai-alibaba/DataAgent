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

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StateUtilSimpleTest {

	@Test
	void getStringValue_keyExists_returnsValue() {
		OverAllState state = new OverAllState();
		state.updateState(Map.of("test-key", "test-value"));

		String result = StateUtil.getStringValue(state, "test-key");
		assertEquals("test-value", result);
	}

	@Test
	void getStringValue_keyNotExists_throwsException() {
		OverAllState state = new OverAllState();

		assertThrows(IllegalStateException.class, () -> {
			StateUtil.getStringValue(state, "non-existent-key");
		});
	}

	@Test
	void getStringValue_keyNotExists_returnsDefault() {
		OverAllState state = new OverAllState();

		String result = StateUtil.getStringValue(state, "non-existent-key", "default");
		assertEquals("default", result);
	}

	@Test
	void hasValue_keyExists_returnsTrue() {
		OverAllState state = new OverAllState();
		state.updateState(Map.of("test-key", "test-value"));

		assertTrue(StateUtil.hasValue(state, "test-key"));
	}

	@Test
	void hasValue_keyNotExists_returnsFalse() {
		OverAllState state = new OverAllState();

		assertFalse(StateUtil.hasValue(state, "non-existent-key"));
	}

	@Test
	void getObjectValue_keyExists_returnsValue() {
		OverAllState state = new OverAllState();
		Map<String, Object> testMap = new HashMap<>();
		testMap.put("name", "test");
		state.updateState(Map.of("test-object", testMap));

		Object result = StateUtil.getObjectValue(state, "test-object", Object.class);
		assertNotNull(result);
	}

	@Test
	void getObjectValue_keyNotExists_returnsDefault() {
		OverAllState state = new OverAllState();

		String result = StateUtil.getObjectValue(state, "non-existent", String.class, "default");
		assertEquals("default", result);
	}

}
