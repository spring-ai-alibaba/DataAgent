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
package com.alibaba.cloud.ai.dataagent.service.lineage;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LineageMetadataService {

	private final DatasourceService datasourceService;

	private final AccessorFactory accessorFactory;

	private final DataAgentProperties properties;

	private final ConcurrentMap<Integer, DatasourceLineageMetadata> cache = new ConcurrentHashMap<>();

	public DatasourceLineageMetadata getMetadata(Integer datasourceId) {
		if (datasourceId == null || !properties.getLineage().isEnabled()) {
			return DatasourceLineageMetadata.empty(datasourceId);
		}
		DatasourceLineageMetadata cached = cache.get(datasourceId);
		if (cached != null) {
			return cached;
		}
		try {
			DatasourceLineageMetadata loaded = loadMetadata(datasourceId);
			cache.put(datasourceId, loaded);
			return loaded;
		}
		catch (Exception exception) {
			log.warn("Failed to discover XLSX lineage metadata for datasource {}", datasourceId, exception);
			return DatasourceLineageMetadata.empty(datasourceId);
		}
	}

	public void invalidate(Integer datasourceId) {
		if (datasourceId != null) {
			cache.remove(datasourceId);
		}
	}

	int cacheSize() {
		return cache.size();
	}

	private DatasourceLineageMetadata loadMetadata(Integer datasourceId) throws Exception {
		Datasource datasource = datasourceService.getDatasourceById(datasourceId);
		if (datasource == null || !"mysql".equalsIgnoreCase(datasource.getType())) {
			return DatasourceLineageMetadata.empty(datasourceId);
		}

		List<String> tableNames = datasourceService.getDatasourceTables(datasourceId);
		Map<String, List<String>> columnsByTable = new LinkedHashMap<>();
		for (String tableName : tableNames) {
			columnsByTable.put(tableName, datasourceService.getTableColumns(datasourceId, tableName));
		}

		Map<String, TableLineageMetadata> detected = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> entry : columnsByTable.entrySet()) {
			TableLineageMetadata direct = detectDirect(entry.getKey(), entry.getValue());
			if (direct != null) {
				detected.put(normalize(entry.getKey()), direct);
			}
		}

		DbConfigBO dbConfig = datasourceService.getDbConfig(datasource);
		Accessor accessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		DbQueryParameter parameter = DbQueryParameter.from(dbConfig)
			.setSchema(dbConfig.getSchema())
			.setTables(tableNames);
		List<ForeignKeyInfoBO> foreignKeys = accessor.showForeignKeys(dbConfig, parameter);
		for (ForeignKeyInfoBO foreignKey : foreignKeys) {
			addInheritedMetadata(detected, columnsByTable, foreignKey.getTable(), foreignKey.getColumn(),
					foreignKey.getReferencedTable(), foreignKey.getReferencedColumn());
		}

		log.info("Discovered XLSX lineage metadata for datasource {}: {} of {} tables", datasourceId,
				detected.size(), tableNames.size());
		return new DatasourceLineageMetadata(datasourceId, detected);
	}

	private TableLineageMetadata detectDirect(String tableName, List<String> columns) {
		DataAgentProperties.LineageProperties config = properties.getLineage();
		String fileName = firstMatchingColumn(columns, config.getFileNameColumns());
		String fileHash = firstMatchingColumn(columns, config.getFileHashColumns());
		if (fileName == null || fileHash == null) {
			return null;
		}
		return new TableLineageMetadata(tableName, tableName, null, null, fileName, fileHash,
				firstMatchingColumn(columns, config.getSheetColumns()),
				firstMatchingColumn(columns, config.getImportedAtColumns()),
				firstMatchingColumn(columns, config.getPlatformColumns()),
				firstMatchingColumn(columns, config.getSourceTypeColumns()),
				firstMatchingColumn(columns, config.getUriColumns()),
				firstMatchingColumn(columns, config.getResourceIdColumns()),
				firstMatchingColumn(columns, config.getVersionColumns()),
				firstMatchingColumn(columns, config.getAcquiredAtColumns()),
				firstMatchingColumn(columns, config.getArtifactFormatColumns()),
				firstMatchingColumn(columns, config.getIngestionToolColumns()));
	}

	private void addInheritedMetadata(Map<String, TableLineageMetadata> detected,
			Map<String, List<String>> columnsByTable, String childTable, String childColumn, String parentTable,
			String parentColumn) {
		if (childTable == null || parentTable == null || detected.containsKey(normalize(childTable))) {
			return;
		}
		TableLineageMetadata parent = detected.get(normalize(parentTable));
		if (parent == null || !containsTable(columnsByTable, childTable)) {
			return;
		}
		detected.put(normalize(childTable),
				new TableLineageMetadata(actualTableName(columnsByTable, childTable), parent.sourceTableName(),
						childColumn, parentColumn, parent.fileNameColumn(), parent.fileHashColumn(),
						parent.sheetColumn(), parent.importedAtColumn(), parent.platformColumn(),
						parent.sourceTypeColumn(), parent.uriColumn(), parent.resourceIdColumn(), parent.versionColumn(),
						parent.acquiredAtColumn(), parent.artifactFormatColumn(), parent.ingestionToolColumn()));
	}

	private String firstMatchingColumn(List<String> actualColumns, List<String> candidates) {
		Map<String, String> normalized = new LinkedHashMap<>();
		for (String column : actualColumns) {
			normalized.put(normalize(column), column);
		}
		for (String candidate : candidates) {
			String actual = normalized.get(normalize(candidate));
			if (actual != null) {
				return actual;
			}
		}
		return null;
	}

	private boolean containsTable(Map<String, List<String>> columnsByTable, String tableName) {
		return columnsByTable.keySet().stream().anyMatch(name -> name.equalsIgnoreCase(tableName));
	}

	private String actualTableName(Map<String, List<String>> columnsByTable, String tableName) {
		return columnsByTable.keySet()
			.stream()
			.filter(name -> name.equalsIgnoreCase(tableName))
			.findFirst()
			.orElse(tableName);
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

}
