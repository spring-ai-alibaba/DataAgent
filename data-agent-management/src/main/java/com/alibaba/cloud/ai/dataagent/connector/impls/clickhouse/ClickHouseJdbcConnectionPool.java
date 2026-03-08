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

import com.alibaba.cloud.ai.dataagent.connector.pool.AbstractDBConnectionPool;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.DATASOURCE_CONNECTION_FAILURE_08S01;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.PASSWORD_ERROR_28000;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.DATABASE_NOT_EXIST_42000;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.OTHERS;

@Slf4j
@Service("clickHouseJdbcConnectionPool")
public class ClickHouseJdbcConnectionPool extends AbstractDBConnectionPool {

	private static final String DRIVER = "com.clickhouse.jdbc.ClickHouseDriver";

	@Override
	public String getDriver() {
		return DRIVER;
	}

	@Override
	public ErrorCodeEnum errorMapping(String sqlState) {

		ErrorCodeEnum ret = ErrorCodeEnum.fromCode(sqlState);
		if (ret != null) {
			return ret;
		}

		return switch (sqlState) {
			case "08S01", "08001", "08004" -> DATASOURCE_CONNECTION_FAILURE_08S01;
			case "28000" -> PASSWORD_ERROR_28000;
			case "42000" -> DATABASE_NOT_EXIST_42000;
			default -> OTHERS;
		};
	}

	@Override
	public boolean supportedDataSourceType(String type) {
		return BizDataSourceTypeEnum.CLICKHOUSE.getTypeName().equals(type);
	}

	@Override
	public String getConnectionPoolType() {
		return BizDataSourceTypeEnum.CLICKHOUSE.getTypeName();
	}

	@Override
	public DataSource createdDataSource(String url, String username, String password) throws Exception {

		String driver = getDriver();

		// Disable Druid wall filter for ClickHouse as its SQL dialect
		// is not fully compatible with the wall filter's SQL parser.
		String filters = "stat";

		java.util.Map<String, String> props = new java.util.HashMap<>();
		props.put(DruidDataSourceFactory.PROP_DRIVERCLASSNAME, driver);
		props.put(DruidDataSourceFactory.PROP_URL, url);
		props.put(DruidDataSourceFactory.PROP_USERNAME, username);
		props.put(DruidDataSourceFactory.PROP_PASSWORD, password);
		props.put(DruidDataSourceFactory.PROP_INITIALSIZE, "5");
		props.put(DruidDataSourceFactory.PROP_MINIDLE, "5");
		props.put(DruidDataSourceFactory.PROP_MAXACTIVE, "20");
		props.put(DruidDataSourceFactory.PROP_MAXWAIT, "10000");
		props.put(DruidDataSourceFactory.PROP_TIMEBETWEENEVICTIONRUNSMILLIS, "60000");
		props.put(DruidDataSourceFactory.PROP_FILTERS, filters);

		DruidDataSource dataSource = (DruidDataSource) DruidDataSourceFactory.createDataSource(props);
		dataSource.setBreakAfterAcquireFailure(Boolean.TRUE);
		dataSource.setConnectionErrorRetryAttempts(2);

		log.info(
				"Created new ClickHouse DataSource with optimized parameters - InitialSize: 5, MinIdle: 5, MaxActive: 20, MaxWait: 10000ms");

		return dataSource;
	}

}
