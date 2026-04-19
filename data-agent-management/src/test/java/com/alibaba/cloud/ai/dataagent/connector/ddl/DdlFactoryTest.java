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
package com.alibaba.cloud.ai.dataagent.connector.ddl;

import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DdlFactoryTest {

	private DdlFactory factory;

	private Ddl mysqlDdl;

	@BeforeEach
	void setUp() {
		mysqlDdl = mock(Ddl.class);
		when(mysqlDdl.getDdlType()).thenReturn("mysql_jdbc");

		factory = new DdlFactory(List.of(mysqlDdl));
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
	void testGetDdlExecutorByType() {
		assertNotNull(factory.getDdlExecutorByType("mysql_jdbc"));
		assertNull(factory.getDdlExecutorByType("nonexistent"));
	}

	@Test
	void testRegistry() {
		Ddl newDdl = mock(Ddl.class);
		when(newDdl.getDdlType()).thenReturn("oracle_jdbc");

		factory.registry(newDdl);
		assertTrue(factory.isRegistered("oracle_jdbc"));
	}

	@Test
	void testGetDdlExecutorByDbType_notFound() {
		assertThrows(IllegalStateException.class, () -> factory.getDdlExecutorByDbType(BizDataSourceTypeEnum.MYSQL));
	}

}
