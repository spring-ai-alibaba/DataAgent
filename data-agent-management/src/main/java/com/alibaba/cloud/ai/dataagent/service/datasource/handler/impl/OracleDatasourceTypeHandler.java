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
package com.alibaba.cloud.ai.dataagent.service.datasource.handler.impl;

import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import org.springframework.stereotype.Component;

@Component
public class OracleDatasourceTypeHandler implements DatasourceTypeHandler {

	@Override
	public String typeName() {
		return BizDataSourceTypeEnum.ORACLE.getTypeName();
	}

	@Override
	public String buildConnectionUrl(Datasource datasource) {
		if (!hasRequiredConnectionFields(datasource)) {
			return datasource.getConnectionUrl();
		}
		// Oracle JDBC URL format: jdbc:oracle:thin:@host:port/serviceName
		return String.format("jdbc:oracle:thin:@%s:%d/%s", datasource.getHost(), datasource.getPort(),
				datasource.getDatabaseName());
	}

	@Override
	public String normalizeTestUrl(Datasource datasource, String url) {
		// Oracle doesn't require additional parameters for basic connectivity
		return url;
	}

	@Override
	public String extractSchemaName(Datasource datasource) {
		// For Oracle, schema is stored in databaseName as "serviceName|schemaName"
		// Extract the schema part after the | separator
		String databaseName = datasource.getDatabaseName();
		if (databaseName != null && databaseName.contains("|")) {
			String[] parts = databaseName.split("\\|");
			if (parts.length == 2) {
				return parts[1]; // Return the schema name
			}
		}
		// If no schema specified, return null to let OracleJdbcDdl.getSchema() use
		// getUserName()
		return null;
	}

}
