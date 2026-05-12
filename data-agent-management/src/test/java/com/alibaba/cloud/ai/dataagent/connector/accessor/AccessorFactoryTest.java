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
package com.alibaba.cloud.ai.dataagent.connector.accessor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessorFactoryTest {

	private AccessorFactory factory;

	@BeforeEach
	void setUp() {
		Accessor accessor1 = mock(Accessor.class);
		when(accessor1.getAccessorType()).thenReturn("mysql_jdbc");

		Accessor accessor2 = mock(Accessor.class);
		when(accessor2.getAccessorType()).thenReturn("postgresql_jdbc");

		factory = new AccessorFactory(List.of(accessor1, accessor2));
	}

	@Test
	void testIsRegistered_true() {
		assertTrue(factory.isRegistered("mysql_jdbc"));
	}

	@Test
	void testIsRegistered_false() {
		assertFalse(factory.isRegistered("unknown"));
	}

	@Test
	void testGetAccessorByType() {
		assertNotNull(factory.getAccessorByType("mysql_jdbc"));
		assertNotNull(factory.getAccessorByType("postgresql_jdbc"));
		assertNull(factory.getAccessorByType("nonexistent"));
	}

	@Test
	void testGetAccessorByDbConfig_nullThrows() {
		assertThrows(IllegalArgumentException.class, () -> factory.getAccessorByDbConfig(null));
	}

	@Test
	void testRegister() {
		Accessor newAccessor = mock(Accessor.class);
		when(newAccessor.getAccessorType()).thenReturn("oracle_jdbc");

		factory.register(newAccessor);
		assertTrue(factory.isRegistered("oracle_jdbc"));
	}

}
