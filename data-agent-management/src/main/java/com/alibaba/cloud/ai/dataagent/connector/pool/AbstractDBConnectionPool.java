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
package com.alibaba.cloud.ai.dataagent.connector.pool;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class AbstractDBConnectionPool implements DBConnectionPool {

	/**
	 * DataSource cache to ensure that each configuration creates DataSource only once.
	 */
	private static final ConcurrentHashMap<DataSourceCacheKey, DataSource> DATA_SOURCE_CACHE = new ConcurrentHashMap<>();

	private record DataSourceCacheKey(String url, String username, String password, String driver) {
	}

	private final ConnectionRetryPolicy retryPolicy;

	protected AbstractDBConnectionPool() {
		this(ConnectionRetryPolicy.defaults());
	}

	protected AbstractDBConnectionPool(ConnectionRetryPolicy retryPolicy) {
		this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
	}

	/**
	 * Driver
	 */
	public abstract String getDriver();

	/**
	 * Error message mapping
	 */
	public abstract ErrorCodeEnum errorMapping(String sqlState);

	protected String getSelectSchemaSQL(String schema) {
		return String.format("SELECT count(*) FROM information_schema.schemata WHERE schema_name = '%s'", schema);
	}

	public ErrorCodeEnum ping(DbConfigBO config) {
		String jdbcUrl = config.getUrl();
		// H2 内嵌数据库允许空密码，其他数据库类型必须配置密码
		boolean isH2 = "h2".equalsIgnoreCase(config.getConnectionType());
		if (!isH2 && (config.getPassword() == null || config.getPassword().isEmpty())) {
			log.error("test db connection skipped: password is empty, url:{}", jdbcUrl);
			return ErrorCodeEnum.PASSWORD_EMPTY;
		}
		try (Connection connection = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword());
				Statement stmt = connection.createStatement();) {
			if (BizDataSourceTypeEnum.isPgDialect(config.getConnectionType())) {
				ResultSet rs = stmt.executeQuery(getSelectSchemaSQL(config.getSchema()));
				if (rs.next()) {
					int count = rs.getInt(1);
					rs.close();
					if (count == 0) {
						log.info("the specified schema '{}' does not exist.", config.getSchema());
						return ErrorCodeEnum.SCHEMA_NOT_EXIST_3D070;
					}
				}
				rs.close();
			}
			return ErrorCodeEnum.SUCCESS;
		}
		catch (SQLException e) {
			log.error("test db connection error, url:{}, state:{}, message:{}", jdbcUrl, e.getSQLState(),
					e.getMessage());
			return errorMapping(e.getSQLState());
		}
	}

	public Connection getConnection(DbConfigBO config) {

		String jdbcUrl = config.getUrl();
		int maxAttempts = retryPolicy.maxAttempts();

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				// Generate cache key based on connection parameters
				DataSourceCacheKey cacheKey = generateCacheKey(jdbcUrl, config.getUsername(), config.getPassword());

				// Use computeIfAbsent to ensure thread safety and avoid duplicate
				// DataSource
				// creation
				DataSource dataSource = DATA_SOURCE_CACHE.computeIfAbsent(cacheKey, key -> {
					try {
						log.debug("Creating new DataSource for URL: {}, username: {}", jdbcUrl, config.getUsername());
						return createdDataSource(jdbcUrl, config.getUsername(), config.getPassword());
					}
					catch (Exception e) {
						log.error("Failed to create DataSource for URL: {}, username: {}", jdbcUrl,
								config.getUsername(), e);
						throw new RuntimeException("Failed to create DataSource", e);
					}
				});

				// 记录连接池状态
				if (dataSource instanceof DruidDataSource druidDataSource) {
					log.debug("Connection pool status - Active: {}, Idle: {}, Total: {}, WaitCount: {}",
							druidDataSource.getActiveCount(), druidDataSource.getPoolingCount(),
							druidDataSource.getActiveCount() + druidDataSource.getPoolingCount(),
							druidDataSource.getWaitThreadCount());
				}

				return dataSource.getConnection();
			}
			catch (Exception e) {
				log.warn("Attempt {} to get database connection failed: {}", attempt, e.getMessage());

				if (attempt == maxAttempts) {
					log.error("Failed to get database connection after {} attempts, URL: {}", maxAttempts, jdbcUrl, e);
					throw new RuntimeException("Failed to get database connection after " + maxAttempts + " attempts",
							e);
				}

				// Wait before retry with incremental backoff
				try {
					retryPolicy.pauseAfterFailure(attempt);
				}
				catch (InterruptedException interruptedException) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Interrupted while retrying database connection",
							interruptedException);
				}
			}
		}
		return null;
	}

	/**
	 * Generate cache key based on connection parameters.
	 * @param url the database URL
	 * @param username the database username
	 * @param password the database password
	 * @return the cache key
	 */
	private DataSourceCacheKey generateCacheKey(String url, String username, String password) {
		return new DataSourceCacheKey(url, username, password, getDriver());
	}

	@Override
	public void close() {
		DATA_SOURCE_CACHE.values().forEach(dataSource -> {
			if (dataSource instanceof DruidDataSource) {
				((DruidDataSource) dataSource).close();
			}
		});
		DATA_SOURCE_CACHE.clear();
		log.info("DataSource cache cleared");
	}

	/**
	 * Clear DataSource cache and close all cached DataSource instances. This method is
	 * useful for resource cleanup in special scenarios.
	 */

	protected boolean useWallFilter() {
		return true;
	}

	protected String getValidationQuery() {
		return "SELECT 1";
	}

	public DataSource createdDataSource(String url, String username, String password) throws Exception {

		String driver = getDriver();

		String filters = useWallFilter() ? "wall,stat" : "stat";

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
		props.put(DruidDataSourceFactory.PROP_VALIDATIONQUERY, getValidationQuery());

		DruidDataSource dataSource = (DruidDataSource) DruidDataSourceFactory.createDataSource(props);
		dataSource.setBreakAfterAcquireFailure(Boolean.TRUE);
		dataSource.setConnectionErrorRetryAttempts(2);

		// 记录数据源创建信息
		log.info(
				"Created new DataSource with optimized parameters - InitialSize: 5, MinIdle: 5, MaxActive: 20, MaxWait: 10000ms");

		return dataSource;
	}

}
