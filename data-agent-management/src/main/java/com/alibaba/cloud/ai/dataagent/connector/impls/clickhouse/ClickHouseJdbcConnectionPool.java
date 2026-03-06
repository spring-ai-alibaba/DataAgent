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
package com.alibaba.cloud.ai.dataagent.connector.impls.clickhouse;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.connector.pool.AbstractDBConnectionPool;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.DATABASE_NOT_EXIST_3D000;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.DATASOURCE_CONNECTION_FAILURE_08001;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.INSUFFICIENT_PRIVILEGE_42501;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.OTHERS;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.PASSWORD_ERROR_28000;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.SUCCESS;

@Slf4j
@Service("clickHouseJdbcConnectionPool")
public class ClickHouseJdbcConnectionPool extends AbstractDBConnectionPool {

	private static final String DRIVER = "com.clickhouse.jdbc.ClickHouseDriver";

	private static final Pattern CLICKHOUSE_CODE_PATTERN = Pattern.compile("Code:\\s*(\\d+)");

	@Override
	public String getDriver() {
		return DRIVER;
	}

	@Override
	public ErrorCodeEnum errorMapping(String sqlState) {
		return ErrorCodeEnum.fromCode(sqlState);
	}

	private ErrorCodeEnum mapClickHouseException(SQLException exception) {
		if (exception == null) {
			return OTHERS;
		}
		if (!"22000".equals(exception.getSQLState())) {
			return errorMapping(exception.getSQLState());
		}

		return switch (parseClickHouseErrorCode(exception.getMessage())) {
			case 81 -> DATABASE_NOT_EXIST_3D000; // UNKNOWN_DATABASE
			case 497 -> INSUFFICIENT_PRIVILEGE_42501; // ACCESS_DENIED
			case 516 -> PASSWORD_ERROR_28000; // AUTHENTICATION_FAILED
			default -> OTHERS;
		};
	}

	private int parseClickHouseErrorCode(String errorMessage) {
		if (errorMessage == null) {
			return -1;
		}
		Matcher matcher = CLICKHOUSE_CODE_PATTERN.matcher(errorMessage);
		if (!matcher.find()) {
			return -1;
		}
		try {
			return Integer.parseInt(matcher.group(1));
		}
		catch (NumberFormatException ignore) {
			return -1;
		}
	}

	@Override
	protected boolean useWallFilter() {
		return false;
	}

	@Override
	public boolean supportedDataSourceType(String type) {
		return BizDataSourceTypeEnum.CLICKHOUSE.getTypeName().equalsIgnoreCase(type);
	}

	@Override
	public String getConnectionPoolType() {
		return BizDataSourceTypeEnum.CLICKHOUSE.getTypeName();
	}

	@Override
	public ErrorCodeEnum ping(DbConfigBO config) {
		String jdbcUrl = config.getUrl();
		String database = config.getDatabaseName();
		try (Connection connection = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword());
				Statement stmt = connection.createStatement()) {
			try (ResultSet rs = stmt.executeQuery("EXISTS DATABASE `" + database + "`")) {
				if (!rs.next() || rs.getInt(1) != 1) {
					log.warn("ClickHouse connection test failed, url:{}, reason:database not exists", jdbcUrl);
					return DATABASE_NOT_EXIST_3D000;
				}
			}
			return SUCCESS;
		}
		catch (SQLException e) {
			log.error("ClickHouse connection test failed, url:{}, state:{}, message:{}", jdbcUrl, e.getSQLState(),
					e.getMessage());
			return mapClickHouseException(e);
		}
		catch (Exception e) {
			log.error("ClickHouse connection test failed with unexpected error, url:{}, message:{}", jdbcUrl,
					e.getMessage());
			return DATASOURCE_CONNECTION_FAILURE_08001;
		}
	}

}
