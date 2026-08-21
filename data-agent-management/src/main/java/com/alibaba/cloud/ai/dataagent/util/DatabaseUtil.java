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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Utility class for processing database.
 */
@Component
@AllArgsConstructor
public class DatabaseUtil {

	private final AccessorFactory accessorFactory;

	private final DatasourceService datasourceService;

	/**
	 * Resolves one immutable connection snapshot only when it still belongs to the schema
	 * revision captured by the graph. Reading the revision and connection fields from the
	 * same datasource row prevents a mid-run connection update from combining old schema
	 * documents with a new physical database.
	 */
	public DbConfigBO getDatasourceDbConfig(Integer datasourceId, String expectedSchemaRevision) {
		if (StringUtils.isBlank(expectedSchemaRevision)) {
			throw new IllegalStateException("Pinned schema revision cannot be empty for datasource " + datasourceId);
		}
		Datasource datasource = datasourceService.getDatasourceById(datasourceId);
		if (datasource == null) {
			throw new IllegalStateException("Datasource not found: " + datasourceId);
		}
		if (!expectedSchemaRevision.equals(datasource.getSchemaRevision())) {
			throw new IllegalStateException(
					"Datasource schema changed during graph execution; restart the query for datasource "
							+ datasourceId);
		}
		return datasourceService.getDbConfig(datasource);
	}

	public Accessor getAccessor(DbConfigBO dbConfig) {
		return accessorFactory.getAccessorByDbConfig(dbConfig);
	}

}
