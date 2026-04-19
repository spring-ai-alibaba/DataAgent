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

class DamengDatasourceTypeHandlerTest {

	private DamengDatasourceTypeHandler handler;

	@BeforeEach
	void setUp() {
		handler = new DamengDatasourceTypeHandler();
	}

	@Test
	void typeName_returnsDameng() {
		assertEquals("dameng", handler.typeName());
	}

	@Test
	void connectionType_returnsJdbc() {
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), handler.connectionType());
	}

	@Test
	void dialectType_returnsDameng() {
		assertEquals("dameng", handler.dialectType());
	}

	@Test
	void supports_matchesCaseInsensitive() {
		assertTrue(handler.supports("dameng"));
		assertTrue(handler.supports("DAMENG"));
		assertTrue(handler.supports("Dameng"));
	}

	@Test
	void supports_rejectsOtherTypes() {
		assertFalse(handler.supports("mysql"));
		assertFalse(handler.supports("oracle"));
	}

	@Test
	void hasRequiredConnectionFields_trueWhenAllPresent() {
		Datasource ds = Datasource.builder().host("dmhost").port(5236).databaseName("SYSDBA").build();
		assertTrue(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void hasRequiredConnectionFields_falseWhenHostMissing() {
		Datasource ds = Datasource.builder().port(5236).databaseName("SYSDBA").build();
		assertFalse(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void buildConnectionUrl_constructsValidUrl() {
		Datasource ds = Datasource.builder().host("dmhost").port(5236).databaseName("SYSDBA").build();
		String url = handler.buildConnectionUrl(ds);
		assertEquals("jdbc:dm://dmhost:5236", url);
	}

	@Test
	void buildConnectionUrl_fallsBackToConnectionUrlWhenFieldsMissing() {
		Datasource ds = Datasource.builder().connectionUrl("jdbc:dm://fallback:5236").build();
		assertEquals("jdbc:dm://fallback:5236", handler.buildConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_prefersExistingUrl() {
		Datasource ds = Datasource.builder()
			.host("dmhost")
			.port(5236)
			.databaseName("SYSDBA")
			.connectionUrl("jdbc:dm://explicit:5236")
			.build();
		assertEquals("jdbc:dm://explicit:5236", handler.resolveConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_buildsWhenNoExistingUrl() {
		Datasource ds = Datasource.builder().host("dmhost").port(5236).databaseName("SYSDBA").build();
		String url = handler.resolveConnectionUrl(ds);
		assertEquals("jdbc:dm://dmhost:5236", url);
	}

	@Test
	void normalizeTestUrl_returnsUrlUnchanged() {
		Datasource ds = Datasource.builder().build();
		String input = "jdbc:dm://host:5236";
		assertEquals(input, handler.normalizeTestUrl(ds, input));
	}

	@Test
	void extractSchemaName_returnsDatabaseName() {
		Datasource ds = Datasource.builder().databaseName("SYSDBA").build();
		assertEquals("SYSDBA", handler.extractSchemaName(ds));
	}

	@Test
	void toDbConfig_populatesAllFields() {
		Datasource ds = Datasource.builder()
			.host("dmhost")
			.port(5236)
			.databaseName("SYSDBA")
			.username("SYSDBA")
			.password("dmpass")
			.build();
		DbConfigBO config = handler.toDbConfig(ds);
		assertNotNull(config.getUrl());
		assertEquals("SYSDBA", config.getUsername());
		assertEquals("dmpass", config.getPassword());
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), config.getConnectionType());
		assertEquals("dameng", config.getDialectType());
		assertEquals("SYSDBA", config.getSchema());
	}

}
