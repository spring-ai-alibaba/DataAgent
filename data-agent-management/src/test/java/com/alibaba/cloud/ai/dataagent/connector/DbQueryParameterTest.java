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
package com.alibaba.cloud.ai.dataagent.connector;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DbQueryParameterTest {

	@Test
	void testDefaultConstructor() {
		DbQueryParameter param = new DbQueryParameter();
		assertNull(param.getAliuid());
		assertNull(param.getDatabase());
	}

	@Test
	void testFullConstructor() {
		DbQueryParameter param = new DbQueryParameter("uid", "ws", "region", "secret", "instance", "db", "schema",
				"table", "pattern", List.of("t1"), "col", "SELECT 1");

		assertEquals("uid", param.getAliuid());
		assertEquals("ws", param.getWorkspaceId());
		assertEquals("region", param.getRegion());
		assertEquals("secret", param.getSecretArn());
		assertEquals("instance", param.getDbInstanceId());
		assertEquals("db", param.getDatabase());
		assertEquals("schema", param.getSchema());
		assertEquals("table", param.getTable());
		assertEquals("pattern", param.getTablePattern());
		assertEquals(List.of("t1"), param.getTables());
		assertEquals("col", param.getColumn());
		assertEquals("SELECT 1", param.getSql());
	}

	@Test
	void testFluentSetters() {
		DbQueryParameter param = new DbQueryParameter().setDatabase("testdb").setSchema("public").setTable("users");

		assertEquals("testdb", param.getDatabase());
		assertEquals("public", param.getSchema());
		assertEquals("users", param.getTable());
	}

	@Test
	void testEquals() {
		DbQueryParameter p1 = new DbQueryParameter().setDatabase("db1").setSchema("s1");
		DbQueryParameter p2 = new DbQueryParameter().setDatabase("db1").setSchema("s1");

		assertEquals(p1, p2);
		assertEquals(p1.hashCode(), p2.hashCode());
	}

	@Test
	void testNotEquals() {
		DbQueryParameter p1 = new DbQueryParameter().setDatabase("db1");
		DbQueryParameter p2 = new DbQueryParameter().setDatabase("db2");

		assertNotEquals(p1, p2);
	}

	@Test
	void testEquals_sameObject() {
		DbQueryParameter p = new DbQueryParameter();
		assertEquals(p, p);
	}

	@Test
	void testEquals_null() {
		DbQueryParameter p = new DbQueryParameter();
		assertNotEquals(null, p);
	}

	@Test
	void testEquals_differentClass() {
		DbQueryParameter p = new DbQueryParameter();
		assertNotEquals("string", p);
	}

	@Test
	void testToString() {
		DbQueryParameter param = new DbQueryParameter().setDatabase("testdb");
		String str = param.toString();
		assertTrue(str.contains("testdb"));
		assertTrue(str.contains("DbQueryParameter"));
	}

	@Test
	void testFrom_dbConfigBO() {
		DbConfigBO config = new DbConfigBO();
		config.setSchema("myschema");
		config.setUrl("jdbc:mysql://localhost/mydb");

		DbQueryParameter param = DbQueryParameter.from(config);
		assertEquals("myschema", param.getSchema());
	}

}
