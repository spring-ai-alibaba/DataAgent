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
package com.alibaba.cloud.ai.dataagent;

import com.alibaba.cloud.ai.dataagent.service.MySqlContainerConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * 数据库初始化测试
 *
 * @author vlsmb
 * @since 2025/9/26
 */
@MybatisTest
@ImportTestcontainers(com.alibaba.cloud.ai.dataagent.service.MySqlContainerConfiguration.class)
@ImportAutoConfiguration(com.alibaba.cloud.ai.dataagent.service.MySqlContainerConfiguration.class)
public class DatabaseSchemaTest {

	@Autowired
	private MySQLContainer<?> container;

	private static final int DATABASE_COUNT = 11;

	@Test
	public void testDatabaseSchema() {
		Assertions.assertNotNull(container);

		// 查询数据表是否符合预期数量
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(
					com.alibaba.cloud.ai.dataagent.service.MySqlContainerConfiguration.getJdbcUrl(),
					com.alibaba.cloud.ai.dataagent.service.MySqlContainerConfiguration.USER_PWD,
					com.alibaba.cloud.ai.dataagent.service.MySqlContainerConfiguration.USER_PWD);
			DatabaseMetaData metaData = conn.getMetaData();
			ResultSet tables = metaData.getTables(MySqlContainerConfiguration.DATABASE_NAME, null, "%",
					new String[] { "TABLE" });
			int count = 0;
			while (tables.next()) {
				count++;
			}
			Assertions.assertEquals(DATABASE_COUNT, count);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		finally {
			Optional.ofNullable(conn).ifPresent(c -> {
				try {
					c.close();
				}
				catch (SQLException e) {
					throw new RuntimeException(e);
				}
			});
		}

	}

}
