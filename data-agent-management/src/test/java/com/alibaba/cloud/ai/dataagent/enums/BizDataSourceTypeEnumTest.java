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
package com.alibaba.cloud.ai.dataagent.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BizDataSourceTypeEnumTest {

	@Test
	void mysql_hasCorrectProperties() {
		assertEquals(1, BizDataSourceTypeEnum.MYSQL.getCode());
		assertEquals("mysql", BizDataSourceTypeEnum.MYSQL.getTypeName());
		assertEquals("MySQL", BizDataSourceTypeEnum.MYSQL.getDialect());
		assertEquals("jdbc", BizDataSourceTypeEnum.MYSQL.getProtocol());
	}

	@Test
	void postgresql_hasCorrectProperties() {
		assertEquals(2, BizDataSourceTypeEnum.POSTGRESQL.getCode());
		assertEquals("postgresql", BizDataSourceTypeEnum.POSTGRESQL.getTypeName());
		assertEquals("PostgreSQL", BizDataSourceTypeEnum.POSTGRESQL.getDialect());
	}

	@Test
	void h2_hasCorrectProperties() {
		assertEquals(4, BizDataSourceTypeEnum.H2.getCode());
		assertEquals("h2", BizDataSourceTypeEnum.H2.getTypeName());
		assertEquals("H2", BizDataSourceTypeEnum.H2.getDialect());
	}

	@Test
	void getTypeNameByCode_existingCode() {
		assertEquals("mysql", BizDataSourceTypeEnum.getTypeNameByCode(1));
		assertEquals("postgresql", BizDataSourceTypeEnum.getTypeNameByCode(2));
		assertEquals("h2", BizDataSourceTypeEnum.getTypeNameByCode(4));
	}

	@Test
	void getTypeNameByCode_unknownCode_returnsNull() {
		assertNull(BizDataSourceTypeEnum.getTypeNameByCode(999));
	}

	@Test
	void getDialectByCode_existingCode() {
		assertEquals("MySQL", BizDataSourceTypeEnum.getDialectByCode(1));
		assertEquals("PostgreSQL", BizDataSourceTypeEnum.getDialectByCode(2));
	}

	@Test
	void getDialectByCode_unknownCode_returnsNull() {
		assertNull(BizDataSourceTypeEnum.getDialectByCode(999));
	}

	@Test
	void getProtocolByCode_existingCode() {
		assertEquals("jdbc", BizDataSourceTypeEnum.getProtocolByCode(1));
		assertEquals("data-api", BizDataSourceTypeEnum.getProtocolByCode(21));
	}

	@Test
	void getProtocolByCode_unknownCode_returnsNull() {
		assertNull(BizDataSourceTypeEnum.getProtocolByCode(999));
	}

	@Test
	void fromCode_existingCode() {
		assertEquals(BizDataSourceTypeEnum.MYSQL, BizDataSourceTypeEnum.fromCode(1));
		assertEquals(BizDataSourceTypeEnum.POSTGRESQL, BizDataSourceTypeEnum.fromCode(2));
		assertEquals(BizDataSourceTypeEnum.H2, BizDataSourceTypeEnum.fromCode(4));
	}

	@Test
	void fromCode_unknownCode_returnsNull() {
		assertNull(BizDataSourceTypeEnum.fromCode(999));
	}

	@Test
	void fromTypeName_existingType() {
		assertEquals(BizDataSourceTypeEnum.MYSQL, BizDataSourceTypeEnum.fromTypeName("mysql"));
		assertEquals(BizDataSourceTypeEnum.POSTGRESQL, BizDataSourceTypeEnum.fromTypeName("postgresql"));
	}

	@Test
	void fromTypeName_unknownType_returnsNull() {
		assertNull(BizDataSourceTypeEnum.fromTypeName("unknown"));
	}

	@Test
	void isMysqlDialect_trueForMysqlTypes() {
		assertTrue(BizDataSourceTypeEnum.isMysqlDialect("mysql"));
		assertTrue(BizDataSourceTypeEnum.isMysqlDialect("mysql-vpc"));
		assertTrue(BizDataSourceTypeEnum.isMysqlDialect("mysql-virtual"));
	}

	@Test
	void isMysqlDialect_falseForNonMysql() {
		assertFalse(BizDataSourceTypeEnum.isMysqlDialect("postgresql"));
		assertFalse(BizDataSourceTypeEnum.isMysqlDialect("unknown"));
	}

	@Test
	void isPgDialect_trueForPgTypes() {
		assertTrue(BizDataSourceTypeEnum.isPgDialect("postgresql"));
		assertTrue(BizDataSourceTypeEnum.isPgDialect("postgresql-vpc"));
		assertTrue(BizDataSourceTypeEnum.isPgDialect("hologress"));
		assertTrue(BizDataSourceTypeEnum.isPgDialect("adg_pg"));
	}

	@Test
	void isPgDialect_falseForNonPg() {
		assertFalse(BizDataSourceTypeEnum.isPgDialect("mysql"));
	}

	@Test
	void isSqlServerDialect_trueForSqlServer() {
		assertTrue(BizDataSourceTypeEnum.isSqlServerDialect("sqlserver"));
	}

	@Test
	void isSqlServerDialect_falseForOthers() {
		assertFalse(BizDataSourceTypeEnum.isSqlServerDialect("mysql"));
	}

	@Test
	void isOracleDialect_trueForOracle() {
		assertTrue(BizDataSourceTypeEnum.isOracleDialect("oracle"));
	}

	@Test
	void isOracleDialect_falseForOthers() {
		assertFalse(BizDataSourceTypeEnum.isOracleDialect("mysql"));
	}

	@Test
	void isAdbPg_trueForAdgPg() {
		assertTrue(BizDataSourceTypeEnum.isAdbPg("adg_pg"));
	}

	@Test
	void isAdbPg_falseForPostgresqlJdbc() {
		assertFalse(BizDataSourceTypeEnum.isAdbPg("postgresql"));
	}

	@Test
	void isAdbPg_falseForUnknown() {
		assertFalse(BizDataSourceTypeEnum.isAdbPg("unknown"));
	}

	@Test
	void isDialect_nullTypeName_returnsFalse() {
		assertFalse(BizDataSourceTypeEnum.isDialect("nonexistent", "MySQL"));
	}

	@Test
	void allEnumValues_haveNonNullFields() {
		for (BizDataSourceTypeEnum type : BizDataSourceTypeEnum.values()) {
			assertNotNull(type.getCode());
			assertNotNull(type.getTypeName());
			assertNotNull(type.getDialect());
			assertNotNull(type.getProtocol());
		}
	}

	@Test
	void sqlite_hasCorrectCode() {
		assertEquals(3, BizDataSourceTypeEnum.SQLITE.getCode());
	}

	@Test
	void dameng_hasCorrectDialect() {
		assertEquals("Dameng", BizDataSourceTypeEnum.DAMENG.getDialect());
	}

	@Test
	void hive_hasCorrectDialect() {
		assertEquals("Hive", BizDataSourceTypeEnum.HIVE.getDialect());
	}

	@Test
	void fcMemoryDb_hasFcHttpProtocol() {
		assertEquals("fc-http", BizDataSourceTypeEnum.FC_MEMORY_DB.getProtocol());
	}

	@Test
	void virtualTypes_haveMemoryProtocol() {
		assertEquals("in-memory", BizDataSourceTypeEnum.MYSQL_VIRTUAL.getProtocol());
		assertEquals("in-memory", BizDataSourceTypeEnum.POSTGRESQL_VIRTUAL.getProtocol());
	}

}
