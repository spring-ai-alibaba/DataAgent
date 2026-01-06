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

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DB connection pool factory
 */
@Component
public class DBConnectionPoolFactory {

	private final Map<String, DBConnectionPool> poolMap = new ConcurrentHashMap<>();

	public DBConnectionPoolFactory(List<DBConnectionPool> pools) {
		pools.forEach(this::register);
	}

	public void register(DBConnectionPool pool) {
		poolMap.put(pool.getConnectionPoolType(), pool);
	}

	public boolean isRegistered(String type) {
		return poolMap.containsKey(type);
	}

	/**
	 * Get corresponding DB connection pool based on database type
	 * @param type database type
	 * @return DB connection pool
	 */
	public DBConnectionPool getPoolByType(String type) {
		// if (type == null || type.trim().isEmpty()) {
		// return null;
		// }
		// return switch (type.toLowerCase()) {
		// case "mysql", "mysqljdbcconnectionpool" ->
		// poolMap.get("mysqlJdbcConnectionPool");
		// case "postgresql", "postgres", "postgresqljdbcconnectionpool" ->
		// poolMap.get("postgreSqlJdbcConnectionPool");
		// case "h2", "h2jdbcconnectionpool" -> poolMap.get("h2JdbcConnectionPool");
		// default -> null;
		// };
		return poolMap.get(type);
	}

	// todo: 写一层缓存
	public DBConnectionPool getPoolByDbType(String type) {
		return poolMap.values()
			.stream()
			.filter(p -> p.supportedDataSourceType(type))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No DB connection pool found for type: " + type));
	}

}
