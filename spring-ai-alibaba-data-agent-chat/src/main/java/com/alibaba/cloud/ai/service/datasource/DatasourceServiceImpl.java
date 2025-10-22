/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.service.datasource;

import com.alibaba.cloud.ai.connector.accessor.Accessor;
import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.connector.bo.DbQueryParameter;
import com.alibaba.cloud.ai.connector.bo.TableInfoBO;
import com.alibaba.cloud.ai.connector.pool.DBConnectionPool;
import com.alibaba.cloud.ai.connector.pool.DBConnectionPoolFactory;
import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.entity.Datasource;
import com.alibaba.cloud.ai.entity.AgentDatasource;
import com.alibaba.cloud.ai.enums.ErrorCodeEnum;
import com.alibaba.cloud.ai.mapper.DatasourceMapper;
import com.alibaba.cloud.ai.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.util.SchemaProcessorUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

// todo: 检查Mapper的返回值，判断是否执行成功（或者对Mapper进行AOP）
@Service
public class DatasourceServiceImpl implements DatasourceService {

	private static final Logger log = LoggerFactory.getLogger(DatasourceServiceImpl.class);

	private final DatasourceMapper datasourceMapper;

	private final AgentDatasourceMapper agentDatasourceMapper;

	private final DBConnectionPoolFactory poolFactory;

	private final AccessorFactory accessorFactory;

	public DatasourceServiceImpl(DatasourceMapper datasourceMapper, AgentDatasourceMapper agentDatasourceMapper,
			DBConnectionPoolFactory poolFactory, AccessorFactory accessorFactory) {
		this.datasourceMapper = datasourceMapper;
		this.agentDatasourceMapper = agentDatasourceMapper;
		this.poolFactory = poolFactory;
		this.accessorFactory = accessorFactory;
	}

	@Override
	public List<Datasource> getAllDatasource() {
		return datasourceMapper.selectAll();
	}

	@Override
	public List<Datasource> getDatasourceByStatus(String status) {
		return datasourceMapper.selectByStatus(status);
	}

	@Override
	public List<Datasource> getDatasourceByType(String type) {
		return datasourceMapper.selectByType(type);
	}

	@Override
	public Datasource getDatasourceById(Integer id) {
		return datasourceMapper.selectById(id);
	}

	@Override
	public Datasource createDatasource(Datasource datasource) {
		// Generate connection URL
		datasource.generateConnectionUrl();

		// Set default values
		if (datasource.getStatus() == null) {
			datasource.setStatus("active");
		}
		if (datasource.getTestStatus() == null) {
			datasource.setTestStatus("unknown");
		}

		datasourceMapper.insert(datasource);
		return datasource;
	}

	@Override
	public Datasource updateDatasource(Integer id, Datasource datasource) {
		// Regenerate connection URL
		datasource.generateConnectionUrl();
		datasource.setId(id);

		datasourceMapper.updateById(datasource);
		return datasource;
	}

	@Override
	@Transactional
	public void deleteDatasource(Integer id) {
		// First, delete the associations
		agentDatasourceMapper.deleteAllByDatasourceId(id);

		// Then, delete the data source
		datasourceMapper.deleteById(id);
	}

	@Override
	public void updateTestStatus(Integer id, String testStatus) {
		datasourceMapper.updateTestStatusById(id, testStatus);
	}

	@Override
	public boolean testConnection(Integer id) {
		Datasource datasource = getDatasourceById(id);
		if (datasource == null) {
			return false;
		}
		try {
			// ping测试
			boolean connectionSuccess = realConnectionTest(datasource);
			log.info(datasource.getName() + " test connection result: " + connectionSuccess);
			// Update test status
			updateTestStatus(id, connectionSuccess ? "success" : "failed");

			return connectionSuccess;
		}
		catch (Exception e) {
			updateTestStatus(id, "failed");
			log.error("Error testing connection for datasource ID " + id + ": " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Actual connection test method
	 */
	private boolean realConnectionTest(Datasource datasource) {
		// Convert Datasource to DbConfig
		DbConfig config = new DbConfig();
		String originalUrl = datasource.getConnectionUrl();

		// Check if URL contains serverTimezone parameter, add default timezone if not,
		// otherwise it will throw an exception
		if (StringUtils.isNotBlank(originalUrl)) {
			String lowerUrl = originalUrl.toLowerCase();

			if (!lowerUrl.contains("servertimezone=")) {
				if (originalUrl.contains("?")) {
					originalUrl += "&serverTimezone=Asia/Shanghai";
				}
				else {
					originalUrl += "?serverTimezone=Asia/Shanghai";
				}
			}

			// Check if it contains useSSL parameter, add useSSL=false if not
			if (!lowerUrl.contains("usessl=")) {
				if (originalUrl.contains("?")) {
					originalUrl += "&useSSL=false";
				}
				else {
					originalUrl += "?useSSL=false";
				}
			}
		}
		config.setUrl(originalUrl);
		config.setUsername(datasource.getUsername());
		config.setPassword(datasource.getPassword());

		DBConnectionPool pool = poolFactory.getPoolByType(datasource.getType());
		if (pool == null) {
			return false;
		}

		ErrorCodeEnum result = pool.ping(config);
		return result == ErrorCodeEnum.SUCCESS;

	}

	@Override
	public List<AgentDatasource> getAgentDatasource(Integer agentId) {
		List<AgentDatasource> adentDatasources = agentDatasourceMapper.selectByAgentIdWithDatasource(agentId);

		// Manually fill in the data source information (since MyBatis Plus does not
		// directly support complex join query result mapping)
		for (AgentDatasource agentDatasource : adentDatasources) {
			if (agentDatasource.getDatasourceId() != null) {
				Datasource datasource = datasourceMapper.selectById(agentDatasource.getDatasourceId());
				agentDatasource.setDatasource(datasource);
			}
		}

		return adentDatasources;
	}

	@Override
	public List<String> getDatasourceTables(Integer datasourceId) throws Exception {
		log.info("Getting tables for datasource: {}", datasourceId);

		// Get data source information
		com.alibaba.cloud.ai.entity.Datasource datasource = this.getDatasourceById(datasourceId);
		if (datasource == null) {
			throw new RuntimeException("Datasource not found with id: " + datasourceId);
		}

		// Create database configuration
		DbConfig dbConfig = SchemaProcessorUtil.createDbConfigFromDatasource(datasource);

		// Create query parameters
		DbQueryParameter queryParam = DbQueryParameter.from(dbConfig);
		queryParam.setSchema(datasource.getDatabaseName());

		// Query table list
		Accessor dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		List<TableInfoBO> tableInfoList = dbAccessor.showTables(dbConfig, queryParam);

		// Extract table names
		List<String> tableNames = tableInfoList.stream()
			.map(TableInfoBO::getName)
			.filter(name -> name != null && !name.trim().isEmpty())
			.sorted()
			.toList();

		log.info("Found {} tables for datasource: {}", tableNames.size(), datasourceId);
		return tableNames;
	}

	@Override
	public Map<String, Object> getDatasourceStats() {
		Map<String, Object> stats = new HashMap<>();

		// Total count statistics
		Long total = datasourceMapper.selectCount();
		stats.put("total", total);

		// Statistics by status
		List<Map<String, Object>> statusStats = datasourceMapper.selectStatusStats();
		stats.put("byStatus", statusStats);

		// Statistics by type
		List<Map<String, Object>> typeStats = datasourceMapper.selectTypeStats();
		stats.put("byType", typeStats);

		// Connection status statistics
		List<Map<String, Object>> testStats = datasourceMapper.selectTestStatusStats();
		stats.put("byTestStatus", testStats);

		return stats;
	}

	@Override
	public Datasource getActiveDatasourceByAgentId(Integer agentId) {
		AgentDatasource agentDatasource = getAgentDatasource(agentId).stream()
			.filter(a -> a.getIsActive() == 1)
			.findFirst()
			.orElse(null);
		if (agentDatasource == null) {
			return null;
		}
		return agentDatasource.getDatasource();
	}

}
