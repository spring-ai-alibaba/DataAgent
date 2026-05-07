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

class MysqlDatasourceTypeHandlerTest {

	private MysqlDatasourceTypeHandler handler;

	@BeforeEach
	void setUp() {
		handler = new MysqlDatasourceTypeHandler();
	}

	@Test
	void typeName_returnsMyql() {
		assertEquals("mysql", handler.typeName());
	}

	@Test
	void connectionType_returnsJdbc() {
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), handler.connectionType());
	}

	@Test
	void dialectType_returnsMysql() {
		assertEquals("mysql", handler.dialectType());
	}

	@Test
	void supports_matchesCaseInsensitive() {
		assertTrue(handler.supports("mysql"));
		assertTrue(handler.supports("MYSQL"));
		assertTrue(handler.supports("MySQL"));
	}

	@Test
	void supports_rejectsOtherTypes() {
		assertFalse(handler.supports("postgresql"));
		assertFalse(handler.supports("oracle"));
		assertFalse(handler.supports(""));
	}

	@Test
	void hasRequiredConnectionFields_trueWhenAllPresent() {
		Datasource ds = Datasource.builder().host("localhost").port(3306).databaseName("testdb").build();
		assertTrue(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void hasRequiredConnectionFields_falseWhenHostMissing() {
		Datasource ds = Datasource.builder().port(3306).databaseName("testdb").build();
		assertFalse(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void hasRequiredConnectionFields_falseWhenPortMissing() {
		Datasource ds = Datasource.builder().host("localhost").databaseName("testdb").build();
		assertFalse(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void hasRequiredConnectionFields_falseWhenDatabaseNameMissing() {
		Datasource ds = Datasource.builder().host("localhost").port(3306).build();
		assertFalse(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void buildConnectionUrl_constructsValidUrl() {
		Datasource ds = Datasource.builder().host("db.example.com").port(3306).databaseName("mydb").build();
		String url = handler.buildConnectionUrl(ds);
		assertTrue(url.startsWith("jdbc:mysql://db.example.com:3306/mydb?"));
		assertTrue(url.contains("useUnicode=true"));
		assertTrue(url.contains("characterEncoding=utf-8"));
		assertTrue(url.contains("useSSL=false"));
		assertTrue(url.contains("serverTimezone=Asia/Shanghai"));
	}

	@Test
	void buildConnectionUrl_fallsBackToConnectionUrlWhenFieldsMissing() {
		Datasource ds = Datasource.builder().connectionUrl("jdbc:mysql://fallback:3306/db").build();
		assertEquals("jdbc:mysql://fallback:3306/db", handler.buildConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_prefersExistingUrl() {
		Datasource ds = Datasource.builder()
			.host("localhost")
			.port(3306)
			.databaseName("mydb")
			.connectionUrl("jdbc:mysql://explicit:3306/db")
			.build();
		assertEquals("jdbc:mysql://explicit:3306/db", handler.resolveConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_buildsWhenNoExistingUrl() {
		Datasource ds = Datasource.builder().host("localhost").port(3306).databaseName("mydb").build();
		String url = handler.resolveConnectionUrl(ds);
		assertTrue(url.startsWith("jdbc:mysql://localhost:3306/mydb?"));
	}

	@Test
	void normalizeTestUrl_addsServerTimezoneIfMissing() {
		Datasource ds = Datasource.builder().build();
		String result = handler.normalizeTestUrl(ds, "jdbc:mysql://host:3306/db");
		assertTrue(result.contains("serverTimezone=Asia/Shanghai"));
	}

	@Test
	void normalizeTestUrl_addsUseSslIfMissing() {
		Datasource ds = Datasource.builder().build();
		String result = handler.normalizeTestUrl(ds, "jdbc:mysql://host:3306/db");
		assertTrue(result.contains("useSSL=false"));
	}

	@Test
	void normalizeTestUrl_doesNotDuplicateServerTimezone() {
		Datasource ds = Datasource.builder().build();
		String input = "jdbc:mysql://host:3306/db?serverTimezone=UTC";
		String result = handler.normalizeTestUrl(ds, input);
		assertFalse(result.contains("serverTimezone=Asia/Shanghai"));
		assertTrue(result.contains("serverTimezone=UTC"));
	}

	@Test
	void normalizeTestUrl_doesNotDuplicateUseSsl() {
		Datasource ds = Datasource.builder().build();
		String input = "jdbc:mysql://host:3306/db?useSSL=true";
		String result = handler.normalizeTestUrl(ds, input);
		assertTrue(result.contains("useSSL=true"));
	}

	@Test
	void normalizeTestUrl_appendsWithAmpersandWhenQuestionMarkExists() {
		Datasource ds = Datasource.builder().build();
		String input = "jdbc:mysql://host:3306/db?foo=bar";
		String result = handler.normalizeTestUrl(ds, input);
		assertTrue(result.contains("&serverTimezone="));
		assertTrue(result.contains("&useSSL="));
	}

	@Test
	void toDbConfig_populatesAllFields() {
		Datasource ds = Datasource.builder()
			.host("localhost")
			.port(3306)
			.databaseName("mydb")
			.username("user")
			.password("pass")
			.build();
		DbConfigBO config = handler.toDbConfig(ds);
		assertNotNull(config.getUrl());
		assertEquals("user", config.getUsername());
		assertEquals("pass", config.getPassword());
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), config.getConnectionType());
		assertEquals("mysql", config.getDialectType());
		assertEquals("mydb", config.getSchema());
	}

	@Test
	void extractSchemaName_returnsDatabaseName() {
		Datasource ds = Datasource.builder().databaseName("mydb").build();
		assertEquals("mydb", handler.extractSchemaName(ds));
	}

}
