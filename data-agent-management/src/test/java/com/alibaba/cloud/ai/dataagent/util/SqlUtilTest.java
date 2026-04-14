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
package com.alibaba.cloud.ai.dataagent.management.util;

import com.alibaba.cloud.ai.dataagent.util.SqlUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlUtilTest {

	@Test
	void buildSelectSql_mysql_appendsLimit() {
		String sql = SqlUtil.buildSelectSql("mysql", "users", "id, name", 10);
		assertEquals("SELECT id, name FROM users LIMIT 10", sql);
	}

	@Test
	void buildSelectSql_postgresql_appendsLimit() {
		String sql = SqlUtil.buildSelectSql("postgresql", "users", "*", 5);
		assertEquals("SELECT * FROM users LIMIT 5", sql);
	}

	@Test
	void buildSelectSql_h2_appendsLimit() {
		String sql = SqlUtil.buildSelectSql("h2", "users", "*", 100);
		assertEquals("SELECT * FROM users LIMIT 100", sql);
	}

	@Test
	void buildSelectSql_sqlserver_prependsTop() {
		String sql = SqlUtil.buildSelectSql("sqlserver", "users", "id, name", 10);
		assertEquals("SELECT TOP 10 id, name FROM users", sql);
	}

	@Test
	void buildSelectSql_oracle_appendsFetchFirst() {
		String sql = SqlUtil.buildSelectSql("oracle", "users", "id, name", 10);
		assertEquals("SELECT id, name FROM users FETCH FIRST 10 ROWS ONLY", sql);
	}

	@Test
	void buildSelectSql_nullColumnNames_defaultsToStar() {
		String sql = SqlUtil.buildSelectSql("mysql", "users", null, 10);
		assertEquals("SELECT * FROM users LIMIT 10", sql);
	}

	@Test
	void buildSelectSql_emptyColumnNames_defaultsToStar() {
		String sql = SqlUtil.buildSelectSql("mysql", "users", "", 10);
		assertEquals("SELECT * FROM users LIMIT 10", sql);
	}

	@Test
	void buildSelectSql_nullTableName_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> SqlUtil.buildSelectSql("mysql", null, "*", 10));
	}

	@Test
	void buildSelectSql_emptyTableName_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> SqlUtil.buildSelectSql("mysql", "", "*", 10));
	}

}
