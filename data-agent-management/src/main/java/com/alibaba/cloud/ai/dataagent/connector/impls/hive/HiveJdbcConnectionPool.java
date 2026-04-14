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
package com.alibaba.cloud.ai.dataagent.connector.impls.hive;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.connector.pool.AbstractDBConnectionPool;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import com.alibaba.druid.pool.DruidDataSourceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.DATABASE_NOT_EXIST_42000;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.DATASOURCE_CONNECTION_FAILURE_08001;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.INSUFFICIENT_PRIVILEGE_42501;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.OTHERS;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.PASSWORD_ERROR_28000;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.SUCCESS;

/**
 * Hive JDBC connection pool implementation.
 */
@Slf4j
@Service("hiveJdbcConnectionPool")
public class HiveJdbcConnectionPool extends AbstractDBConnectionPool {

	private static final String DRIVER = "org.apache.hive.jdbc.HiveDriver";

	@Override
	public String getDriver() {
		return DRIVER;
	}

	@Override
	public ErrorCodeEnum errorMapping(String sqlState) {
		if (sqlState == null) {
			return OTHERS;
		}

		ErrorCodeEnum ret = ErrorCodeEnum.fromCode(sqlState);
		if (ret != OTHERS) {
			return ret;
		}

		switch (sqlState) {
			case "08001":
			case "08S01":
				return DATASOURCE_CONNECTION_FAILURE_08001;
			case "28000":
				return PASSWORD_ERROR_28000;
			case "42000":
				return DATABASE_NOT_EXIST_42000;
			case "42501":
				return INSUFFICIENT_PRIVILEGE_42501;
			default:
				return OTHERS;
		}
	}

	@Override
	public boolean supportedDataSourceType(String type) {
		return BizDataSourceTypeEnum.HIVE.getTypeName().equals(type);
	}

	@Override
	public String getConnectionPoolType() {
		return "Hive_JDBC_Pool";
	}

	@Override
	public DataSource createdDataSource(String url, String username, String password) throws Exception {
		log.info("Creating Hive DataSource with custom configuration");
		String driver = getDriver();
		Map<String, String> props = new HiveDruidProperties(driver, url, username, password, "stat").toMap();
		return DruidDataSourceFactory.createDataSource(props);
	}

	private static final class HiveDruidProperties {

		private final String driver;

		private final String url;

		private final String username;

		private final String password;

		private final String filters;

		private HiveDruidProperties(String driver, String url, String username, String password, String filters) {
			this.driver = driver;
			this.url = url;
			this.username = username;
			this.password = password;
			this.filters = filters;
		}

		private Map<String, String> toMap() {
			Map<String, String> props = new HashMap<>();
			props.put(DruidDataSourceFactory.PROP_DRIVERCLASSNAME, this.driver);
			props.put(DruidDataSourceFactory.PROP_URL, this.url);
			props.put(DruidDataSourceFactory.PROP_USERNAME, this.username);
			props.put(DruidDataSourceFactory.PROP_PASSWORD, this.password);
			props.put(DruidDataSourceFactory.PROP_FILTERS, this.filters);
			props.put(DruidDataSourceFactory.PROP_INITIALSIZE, "5");
			props.put(DruidDataSourceFactory.PROP_MINIDLE, "5");
			props.put(DruidDataSourceFactory.PROP_MAXACTIVE, "20");
			props.put(DruidDataSourceFactory.PROP_MAXWAIT, "60000");
			props.put(DruidDataSourceFactory.PROP_TIMEBETWEENEVICTIONRUNSMILLIS, "60000");
			props.put(DruidDataSourceFactory.PROP_MINEVICTABLEIDLETIMEMILLIS, "300000");
			props.put(DruidDataSourceFactory.PROP_VALIDATIONQUERY, "SELECT 1");
			props.put(DruidDataSourceFactory.PROP_TESTWHILEIDLE, "true");
			props.put(DruidDataSourceFactory.PROP_TESTONBORROW, "false");
			props.put(DruidDataSourceFactory.PROP_TESTONRETURN, "false");
			return props;
		}

	}

	@Override
	public ErrorCodeEnum ping(DbConfigBO config) {
		log.info("Hive ping method called, url: {}", config.getUrl());
		try (Connection connection = getConnection(config); Statement stmt = connection.createStatement()) {
			log.info("Hive connection obtained, executing SELECT 1");
			ResultSet rs = stmt.executeQuery("SELECT 1");
			if (rs.next()) {
				rs.close();
				return SUCCESS;
			}
			rs.close();
			return DATASOURCE_CONNECTION_FAILURE_08001;
		}
		catch (SQLException e) {
			log.error("Hive connection test failed, url:{}, state:{}, message:{}", config.getUrl(), e.getSQLState(),
					e.getMessage());
			return errorMapping(e.getSQLState());
		}
		catch (Exception e) {
			log.error("Hive connection test failed with unexpected error, url:{}, message:{}", config.getUrl(),
					e.getMessage());
			return DATASOURCE_CONNECTION_FAILURE_08001;
		}
	}

}
