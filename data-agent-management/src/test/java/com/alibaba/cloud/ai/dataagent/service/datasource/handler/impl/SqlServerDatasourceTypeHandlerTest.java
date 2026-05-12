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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerDatasourceTypeHandlerTest {

	private SqlServerDatasourceTypeHandler handler;

	@BeforeEach
	void setUp() {
		handler = new SqlServerDatasourceTypeHandler();
	}

	@Test
	void typeName_returnsSqlserver() {
		assertEquals("sqlserver", handler.typeName());
	}

	@Test
	void connectionType_returnsJdbc() {
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), handler.connectionType());
	}

	@Test
	void dialectType_returnsSqlserver() {
		assertEquals("sqlserver", handler.dialectType());
	}

	@Test
	void supports_matchesCaseInsensitive() {
		assertTrue(handler.supports("sqlserver"));
		assertTrue(handler.supports("SQLSERVER"));
		assertTrue(handler.supports("SqlServer"));
	}

	@Test
	void supports_rejectsOtherTypes() {
		assertFalse(handler.supports("mysql"));
		assertFalse(handler.supports("oracle"));
		assertFalse(handler.supports("sql_server"));
	}

	@Test
	void hasRequiredConnectionFields_trueWhenAllPresent() {
		Datasource ds = Datasource.builder().host("sqlhost").port(1433).databaseName("master").build();
		assertTrue(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void hasRequiredConnectionFields_falseWhenDatabaseNameMissing() {
		Datasource ds = Datasource.builder().host("sqlhost").port(1433).build();
		assertFalse(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void buildConnectionUrl_returnsConnectionUrlFromDatasource() {
		Datasource ds = Datasource.builder().connectionUrl("jdbc:sqlserver://host:1433;databaseName=db").build();
		assertEquals("jdbc:sqlserver://host:1433;databaseName=db", handler.buildConnectionUrl(ds));
	}

	@Test
	void buildConnectionUrl_returnsNullWhenNoConnectionUrl() {
		Datasource ds = Datasource.builder().host("sqlhost").port(1433).databaseName("master").build();
		assertNull(handler.buildConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_prefersExistingUrl() {
		Datasource ds = Datasource.builder().connectionUrl("jdbc:sqlserver://explicit:1433;databaseName=db").build();
		assertEquals("jdbc:sqlserver://explicit:1433;databaseName=db", handler.resolveConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_returnsNullWhenNothingProvided() {
		Datasource ds = Datasource.builder().host("sqlhost").port(1433).databaseName("master").build();
		assertNull(handler.resolveConnectionUrl(ds));
	}

	@Test
	void normalizeTestUrl_returnsUrlUnchanged() {
		Datasource ds = Datasource.builder().build();
		String input = "jdbc:sqlserver://host:1433;databaseName=db";
		assertEquals(input, handler.normalizeTestUrl(ds, input));
	}

	@Test
	void extractSchemaName_returnsDatabaseName() {
		Datasource ds = Datasource.builder().databaseName("master").build();
		assertEquals("master", handler.extractSchemaName(ds));
	}

	@Test
	void toDbConfig_populatesAllFields() {
		Datasource ds = Datasource.builder()
			.connectionUrl("jdbc:sqlserver://host:1433;databaseName=db")
			.databaseName("master")
			.username("sa")
			.password("pass")
			.build();
		DbConfigBO config = handler.toDbConfig(ds);
		assertEquals("jdbc:sqlserver://host:1433;databaseName=db", config.getUrl());
		assertEquals("sa", config.getUsername());
		assertEquals("pass", config.getPassword());
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), config.getConnectionType());
		assertEquals("sqlserver", config.getDialectType());
		assertEquals("master", config.getSchema());
	}

}
