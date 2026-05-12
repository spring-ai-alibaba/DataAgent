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

class PostgreSqlDatasourceTypeHandlerTest {

	private PostgreSqlDatasourceTypeHandler handler;

	@BeforeEach
	void setUp() {
		handler = new PostgreSqlDatasourceTypeHandler();
	}

	@Test
	void typeName_returnsPostgresql() {
		assertEquals("postgresql", handler.typeName());
	}

	@Test
	void connectionType_returnsJdbc() {
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), handler.connectionType());
	}

	@Test
	void dialectType_returnsPostgresql() {
		assertEquals("postgresql", handler.dialectType());
	}

	@Test
	void supports_matchesCaseInsensitive() {
		assertTrue(handler.supports("postgresql"));
		assertTrue(handler.supports("POSTGRESQL"));
		assertTrue(handler.supports("PostgreSQL"));
	}

	@Test
	void supports_rejectsOtherTypes() {
		assertFalse(handler.supports("mysql"));
		assertFalse(handler.supports("postgres"));
	}

	@Test
	void hasRequiredConnectionFields_trueWhenAllPresent() {
		Datasource ds = Datasource.builder().host("localhost").port(5432).databaseName("testdb").build();
		assertTrue(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void hasRequiredConnectionFields_falseWhenPortMissing() {
		Datasource ds = Datasource.builder().host("localhost").databaseName("testdb").build();
		assertFalse(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void buildConnectionUrl_constructsValidUrl() {
		Datasource ds = Datasource.builder().host("pghost").port(5432).databaseName("mydb").build();
		String url = handler.buildConnectionUrl(ds);
		assertTrue(url.startsWith("jdbc:postgresql://pghost:5432/mydb?"));
		assertTrue(url.contains("useUnicode=true"));
		assertTrue(url.contains("useSSL=false"));
	}

	@Test
	void buildConnectionUrl_extractsDatabaseFromPipeFormat() {
		Datasource ds = Datasource.builder().host("pghost").port(5432).databaseName("mydb|public").build();
		String url = handler.buildConnectionUrl(ds);
		assertTrue(url.contains("/mydb?"));
		assertFalse(url.contains("public"));
	}

	@Test
	void buildConnectionUrl_fallsBackToConnectionUrlWhenFieldsMissing() {
		Datasource ds = Datasource.builder().connectionUrl("jdbc:postgresql://fallback:5432/db").build();
		assertEquals("jdbc:postgresql://fallback:5432/db", handler.buildConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_prefersExistingUrl() {
		Datasource ds = Datasource.builder()
			.host("localhost")
			.port(5432)
			.databaseName("mydb")
			.connectionUrl("jdbc:postgresql://explicit:5432/db")
			.build();
		assertEquals("jdbc:postgresql://explicit:5432/db", handler.resolveConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_buildsWhenNoExistingUrl() {
		Datasource ds = Datasource.builder().host("localhost").port(5432).databaseName("mydb").build();
		String url = handler.resolveConnectionUrl(ds);
		assertTrue(url.startsWith("jdbc:postgresql://localhost:5432/mydb?"));
	}

	@Test
	void extractSchemaName_returnsSchemaFromPipeFormat() {
		Datasource ds = Datasource.builder().databaseName("mydb|myschema").build();
		assertEquals("myschema", handler.extractSchemaName(ds));
	}

	@Test
	void extractSchemaName_returnsDatabaseNameWhenNoPipe() {
		Datasource ds = Datasource.builder().databaseName("mydb").build();
		assertEquals("mydb", handler.extractSchemaName(ds));
	}

	@Test
	void extractSchemaName_returnsNullWhenNull() {
		Datasource ds = Datasource.builder().build();
		assertNull(handler.extractSchemaName(ds));
	}

	@Test
	void extractSchemaName_returnsDatabasePartWhenPipeButNoSchema() {
		Datasource ds = Datasource.builder().databaseName("mydb|").build();
		assertEquals("mydb", handler.extractSchemaName(ds));
	}

	@Test
	void toDbConfig_usesExtractedSchema() {
		Datasource ds = Datasource.builder()
			.host("localhost")
			.port(5432)
			.databaseName("mydb|myschema")
			.username("pguser")
			.password("pgpass")
			.build();
		DbConfigBO config = handler.toDbConfig(ds);
		assertNotNull(config.getUrl());
		assertEquals("pguser", config.getUsername());
		assertEquals("pgpass", config.getPassword());
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), config.getConnectionType());
		assertEquals("postgresql", config.getDialectType());
		assertEquals("myschema", config.getSchema());
	}

}
