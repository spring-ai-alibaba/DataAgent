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
package com.alibaba.cloud.ai.dataagent.service.datasource.handler.impl;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.enums.DbAccessTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveDatasourceTypeHandlerTest {

	private HiveDatasourceTypeHandler handler;

	@BeforeEach
	void setUp() {
		handler = new HiveDatasourceTypeHandler();
	}

	@Test
	void typeName_returnsHive() {
		assertEquals("hive", handler.typeName());
	}

	@Test
	void connectionType_returnsJdbc() {
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), handler.connectionType());
	}

	@Test
	void dialectType_returnsHive() {
		assertEquals("hive", handler.dialectType());
	}

	@Test
	void supports_matchesCaseInsensitive() {
		assertTrue(handler.supports("hive"));
		assertTrue(handler.supports("HIVE"));
		assertTrue(handler.supports("Hive"));
	}

	@Test
	void supports_rejectsOtherTypes() {
		assertFalse(handler.supports("mysql"));
		assertFalse(handler.supports("oracle"));
	}

	@Test
	void hasRequiredConnectionFields_trueWhenAllPresent() {
		Datasource ds = Datasource.builder().host("hivehost").port(10000).databaseName("default").build();
		assertTrue(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void hasRequiredConnectionFields_falseWhenHostMissing() {
		Datasource ds = Datasource.builder().port(10000).databaseName("default").build();
		assertFalse(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void buildConnectionUrl_constructsValidUrl() {
		Datasource ds = Datasource.builder().host("hivehost").port(10000).databaseName("default").build();
		String url = handler.buildConnectionUrl(ds);
		assertEquals("jdbc:hive2://hivehost:10000/default", url);
	}

	@Test
	void buildConnectionUrl_fallsBackToConnectionUrlWhenFieldsMissing() {
		Datasource ds = Datasource.builder().connectionUrl("jdbc:hive2://fallback:10000/db").build();
		assertEquals("jdbc:hive2://fallback:10000/db", handler.buildConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_prefersExistingUrl() {
		Datasource ds = Datasource.builder()
			.host("hivehost")
			.port(10000)
			.databaseName("default")
			.connectionUrl("jdbc:hive2://explicit:10000/db")
			.build();
		assertEquals("jdbc:hive2://explicit:10000/db", handler.resolveConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_buildsWhenNoExistingUrl() {
		Datasource ds = Datasource.builder().host("hivehost").port(10000).databaseName("default").build();
		String url = handler.resolveConnectionUrl(ds);
		assertEquals("jdbc:hive2://hivehost:10000/default", url);
	}

	@Test
	void normalizeTestUrl_returnsUrlUnchanged() {
		Datasource ds = Datasource.builder().build();
		String input = "jdbc:hive2://host:10000/default";
		assertEquals(input, handler.normalizeTestUrl(ds, input));
	}

	@Test
	void extractSchemaName_returnsDatabaseName() {
		Datasource ds = Datasource.builder().databaseName("default").build();
		assertEquals("default", handler.extractSchemaName(ds));
	}

	@Test
	void toDbConfig_populatesAllFields() {
		Datasource ds = Datasource.builder()
			.host("hivehost")
			.port(10000)
			.databaseName("default")
			.username("hiveuser")
			.password("hivepass")
			.build();
		DbConfigBO config = handler.toDbConfig(ds);
		assertNotNull(config.getUrl());
		assertEquals("hiveuser", config.getUsername());
		assertEquals("hivepass", config.getPassword());
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), config.getConnectionType());
		assertEquals("hive", config.getDialectType());
		assertEquals("default", config.getSchema());
	}

}
