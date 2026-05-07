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

class CodePoolExecutorEnumTest {

	@Test
	void testValues() {
		assertEquals(5, CodePoolExecutorEnum.values().length);
	}

	@Test
	void testValueOf() {
		assertEquals(CodePoolExecutorEnum.DOCKER, CodePoolExecutorEnum.valueOf("DOCKER"));
		assertEquals(CodePoolExecutorEnum.CONTAINERD, CodePoolExecutorEnum.valueOf("CONTAINERD"));
		assertEquals(CodePoolExecutorEnum.KATA, CodePoolExecutorEnum.valueOf("KATA"));
		assertEquals(CodePoolExecutorEnum.AI_SIMULATION, CodePoolExecutorEnum.valueOf("AI_SIMULATION"));
		assertEquals(CodePoolExecutorEnum.LOCAL, CodePoolExecutorEnum.valueOf("LOCAL"));
	}

	@Test
	void testValueOf_invalid() {
		assertThrows(IllegalArgumentException.class, () -> CodePoolExecutorEnum.valueOf("UNKNOWN"));
	}

}
