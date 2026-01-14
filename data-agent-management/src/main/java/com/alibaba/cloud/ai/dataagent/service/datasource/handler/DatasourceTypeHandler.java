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
package com.alibaba.cloud.ai.dataagent.service.datasource.handler;

import com.alibaba.cloud.ai.dataagent.enums.DbAccessTypeEnum;
import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import org.springframework.util.StringUtils;

public interface DatasourceTypeHandler {

	String typeName();

	default String connectionType() {
		return DbAccessTypeEnum.JDBC.getCode();
	}

	default String dialectType() {
		return typeName();
	}

	default boolean supports(String type) {
		return typeName().equalsIgnoreCase(type);
	}

	default boolean hasRequiredConnectionFields(Datasource datasource) {
		return datasource.getHost() != null && datasource.getPort() != null && datasource.getDatabaseName() != null;
	}

	default String buildConnectionUrl(Datasource datasource) {
		return datasource.getConnectionUrl();
	}

	default String resolveConnectionUrl(Datasource datasource) {
		String existing = datasource.getConnectionUrl();
		if (StringUtils.hasText(existing)) {
			return existing;
		}
		return buildConnectionUrl(datasource);
	}

	default DbConfigBO toDbConfig(Datasource datasource) {
		DbConfigBO config = new DbConfigBO();
		config.setUrl(resolveConnectionUrl(datasource));
		config.setUsername(datasource.getUsername());
		config.setPassword(datasource.getPassword());
		config.setConnectionType(connectionType());
		config.setDialectType(dialectType());
		config.setSchema(datasource.getDatabaseName());
		return config;
	}

	default String normalizeTestUrl(Datasource datasource, String url) {
		return url;
	}

}
