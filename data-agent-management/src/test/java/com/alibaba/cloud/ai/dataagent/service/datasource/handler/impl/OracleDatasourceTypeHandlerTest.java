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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OracleDatasourceTypeHandlerTest {

	private OracleDatasourceTypeHandler handler;

	@BeforeEach
	void setUp() {
		handler = new OracleDatasourceTypeHandler();
	}

	@Test
	void typeName_returnsOracle() {
		assertEquals("oracle", handler.typeName());
	}

	@Test
	void connectionType_returnsJdbc() {
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), handler.connectionType());
	}

	@Test
	void dialectType_returnsOracle() {
		assertEquals("oracle", handler.dialectType());
	}

	@Test
	void supports_matchesCaseInsensitive() {
		assertTrue(handler.supports("oracle"));
		assertTrue(handler.supports("ORACLE"));
		assertTrue(handler.supports("Oracle"));
	}

	@Test
	void supports_rejectsOtherTypes() {
		assertFalse(handler.supports("mysql"));
		assertFalse(handler.supports("sqlserver"));
	}

	@Test
	void hasRequiredConnectionFields_trueWhenAllPresent() {
		Datasource ds = Datasource.builder().host("orahost").port(1521).databaseName("orcl").build();
		assertTrue(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void hasRequiredConnectionFields_falseWhenHostMissing() {
		Datasource ds = Datasource.builder().port(1521).databaseName("orcl").build();
		assertFalse(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void buildConnectionUrl_constructsValidUrl() {
		Datasource ds = Datasource.builder().host("orahost").port(1521).databaseName("orcl").build();
		String url = handler.buildConnectionUrl(ds);
		assertEquals("jdbc:oracle:thin:@orahost:1521/orcl", url);
	}

	@Test
	void buildConnectionUrl_fallsBackToConnectionUrlWhenFieldsMissing() {
		Datasource ds = Datasource.builder().connectionUrl("jdbc:oracle:thin:@fallback:1521/db").build();
		assertEquals("jdbc:oracle:thin:@fallback:1521/db", handler.buildConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_prefersExistingUrl() {
		Datasource ds = Datasource.builder()
			.host("orahost")
			.port(1521)
			.databaseName("orcl")
			.connectionUrl("jdbc:oracle:thin:@explicit:1521/db")
			.build();
		assertEquals("jdbc:oracle:thin:@explicit:1521/db", handler.resolveConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_buildsWhenNoExistingUrl() {
		Datasource ds = Datasource.builder().host("orahost").port(1521).databaseName("orcl").build();
		String url = handler.resolveConnectionUrl(ds);
		assertEquals("jdbc:oracle:thin:@orahost:1521/orcl", url);
	}

	@Test
	void normalizeTestUrl_returnsUrlUnchanged() {
		Datasource ds = Datasource.builder().build();
		String input = "jdbc:oracle:thin:@host:1521/orcl";
		assertEquals(input, handler.normalizeTestUrl(ds, input));
	}

	@Test
	void extractSchemaName_returnsSchemaFromPipeFormat() {
		Datasource ds = Datasource.builder().databaseName("orcl|HR").build();
		assertEquals("HR", handler.extractSchemaName(ds));
	}

	@Test
	void extractSchemaName_returnsNullWhenNoPipe() {
		Datasource ds = Datasource.builder().databaseName("orcl").build();
		assertNull(handler.extractSchemaName(ds));
	}

	@Test
	void extractSchemaName_returnsNullWhenNull() {
		Datasource ds = Datasource.builder().build();
		assertNull(handler.extractSchemaName(ds));
	}

	@Test
	void extractSchemaName_returnsNullWhenPipeButThreeParts() {
		Datasource ds = Datasource.builder().databaseName("orcl|HR|extra").build();
		assertNull(handler.extractSchemaName(ds));
	}

	@Test
	void toDbConfig_usesExtractedSchema() {
		Datasource ds = Datasource.builder()
			.host("orahost")
			.port(1521)
			.databaseName("orcl|HR")
			.username("orauser")
			.password("orapass")
			.build();
		DbConfigBO config = handler.toDbConfig(ds);
		assertNotNull(config.getUrl());
		assertEquals("orauser", config.getUsername());
		assertEquals("orapass", config.getPassword());
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), config.getConnectionType());
		assertEquals("oracle", config.getDialectType());
		assertEquals("HR", config.getSchema());
	}

	@Test
	void toDbConfig_schemaIsNullWhenNoPipe() {
		Datasource ds = Datasource.builder()
			.host("orahost")
			.port(1521)
			.databaseName("orcl")
			.username("orauser")
			.password("orapass")
			.build();
		DbConfigBO config = handler.toDbConfig(ds);
		assertNull(config.getSchema());
	}

}
