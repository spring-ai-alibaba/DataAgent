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

class H2DatasourceTypeHandlerTest {

	private H2DatasourceTypeHandler handler;

	@BeforeEach
	void setUp() {
		handler = new H2DatasourceTypeHandler();
	}

	@Test
	void typeName_returnsH2() {
		assertEquals("h2", handler.typeName());
	}

	@Test
	void connectionType_returnsJdbc() {
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), handler.connectionType());
	}

	@Test
	void dialectType_returnsH2() {
		assertEquals("h2", handler.dialectType());
	}

	@Test
	void supports_matchesCaseInsensitive() {
		assertTrue(handler.supports("h2"));
		assertTrue(handler.supports("H2"));
	}

	@Test
	void supports_rejectsOtherTypes() {
		assertFalse(handler.supports("mysql"));
		assertFalse(handler.supports("postgresql"));
	}

	@Test
	void hasRequiredConnectionFields_trueWhenAllPresent() {
		Datasource ds = Datasource.builder().host("localhost").port(9092).databaseName("testdb").build();
		assertTrue(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void hasRequiredConnectionFields_falseWhenHostMissing() {
		Datasource ds = Datasource.builder().port(9092).databaseName("testdb").build();
		assertFalse(handler.hasRequiredConnectionFields(ds));
	}

	@Test
	void buildConnectionUrl_constructsInMemoryUrl() {
		Datasource ds = Datasource.builder().host("localhost").port(9092).databaseName("testdb").build();
		String url = handler.buildConnectionUrl(ds);
		assertEquals("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=true;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
				url);
	}

	@Test
	void buildConnectionUrl_fallsBackToConnectionUrlWhenFieldsMissing() {
		Datasource ds = Datasource.builder().connectionUrl("jdbc:h2:mem:fallback").build();
		assertEquals("jdbc:h2:mem:fallback", handler.buildConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_prefersExistingUrl() {
		Datasource ds = Datasource.builder()
			.host("localhost")
			.port(9092)
			.databaseName("testdb")
			.connectionUrl("jdbc:h2:mem:explicit")
			.build();
		assertEquals("jdbc:h2:mem:explicit", handler.resolveConnectionUrl(ds));
	}

	@Test
	void resolveConnectionUrl_buildsWhenNoExistingUrl() {
		Datasource ds = Datasource.builder().host("localhost").port(9092).databaseName("mydb").build();
		String url = handler.resolveConnectionUrl(ds);
		assertTrue(url.startsWith("jdbc:h2:mem:mydb"));
	}

	@Test
	void normalizeTestUrl_returnsUrlUnchanged() {
		Datasource ds = Datasource.builder().build();
		String input = "jdbc:h2:mem:testdb";
		assertEquals(input, handler.normalizeTestUrl(ds, input));
	}

	@Test
	void toDbConfig_populatesAllFields() {
		Datasource ds = Datasource.builder()
			.host("localhost")
			.port(9092)
			.databaseName("mydb")
			.username("sa")
			.password("")
			.build();
		DbConfigBO config = handler.toDbConfig(ds);
		assertNotNull(config.getUrl());
		assertEquals("sa", config.getUsername());
		assertEquals("", config.getPassword());
		assertEquals(DbAccessTypeEnum.JDBC.getCode(), config.getConnectionType());
		assertEquals("h2", config.getDialectType());
		assertEquals("mydb", config.getSchema());
	}

}
