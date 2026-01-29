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
package com.alibaba.cloud.ai.dataagent.connector.impls.oracle;

import com.alibaba.cloud.ai.dataagent.bo.schema.*;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

/**
 * Oracle 连接器简单测试 测试虚拟机: 192.168.3.136
 */
public class OracleConnectorTest {

	// 修改这里的配置以匹配你的虚拟机设置
	private static final String JDBC_URL = "jdbc:oracle:thin:@192.168.3.136:1521:ORCL";

	private static final String USERNAME = "system"; // 修改为你的用户名

	private static final String PASSWORD = "oracle"; // 修改为你的密码

	private static final String TEST_SCHEMA = "SYSTEM"; // 修改为你要测试的 schema

	@Test
	public void testOracleConnection() {
		System.out.println("=".repeat(60));
		System.out.println("开始测试 Oracle 连接器");
		System.out.println("=".repeat(60));

		Connection connection = null;
		try {
			// 1. 测试驱动
			OracleJdbcConnectionPool pool = new OracleJdbcConnectionPool();
			String driver = pool.getDriver();
			System.out.println("✅ 驱动: " + driver);

			if (!"oracle.jdbc.OracleDriver".equals(driver)) {
				throw new RuntimeException("❌ 驱动错误！应该是 oracle.jdbc.OracleDriver");
			}

			// 2. 测试连接
			Class.forName(driver);
			connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
			System.out.println("✅ 连接成功: " + JDBC_URL);

			// 3. 测试查询表
			OracleJdbcDdl ddl = new OracleJdbcDdl();
			List<TableInfoBO> tables = ddl.showTables(connection, TEST_SCHEMA, null);
			System.out.println("✅ 查询到 " + tables.size() + " 个表");

			if (!tables.isEmpty()) {
				System.out.println("   示例表: " + tables.get(0).getName());

				// 4. 测试查询列
				String tableName = tables.get(0).getName();
				List<ColumnInfoBO> columns = ddl.showColumns(connection, TEST_SCHEMA, tableName);
				System.out.println("✅ 表 " + tableName + " 有 " + columns.size() + " 列");
			}

			System.out.println("=".repeat(60));
			System.out.println("✅ 所有测试通过！");
			System.out.println("=".repeat(60));

		}
		catch (Exception e) {
			System.err.println("❌ 测试失败: " + e.getMessage());
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		finally {
			if (connection != null) {
				try {
					connection.close();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

}
