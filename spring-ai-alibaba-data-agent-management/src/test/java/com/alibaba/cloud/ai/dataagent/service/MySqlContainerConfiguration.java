/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.dataagent.service;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MySQL 测试Container配置类
 *
 * @author vlsmb
 * @since 2025/9/26
 */
@Configuration
@Testcontainers
public class MySqlContainerConfiguration {

	public static final int MYSQL_PORT = 3306;

	public static final String DATABASE_NAME = "data_agent";

	public static final String USER_PWD = "root";

	@Container
	@ServiceConnection
	static MySQLContainer<?> container = new MySQLContainer<>("mysql:8.0.36").withUsername(USER_PWD)
		.withPassword(USER_PWD)
		.withDatabaseName(DATABASE_NAME)
		.withExposedPorts(MYSQL_PORT)
		.withInitScript("sql/schema.sql");

	public static String getJdbcUrl() {
		return "jdbc:mysql://" + container.getHost() + ":" + container.getMappedPort(MYSQL_PORT) + "/" + DATABASE_NAME;
	}

}
