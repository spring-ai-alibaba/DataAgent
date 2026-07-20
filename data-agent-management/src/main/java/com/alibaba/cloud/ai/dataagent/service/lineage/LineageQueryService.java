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
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LineageQueryService {

	private static final Set<String> RESERVED_ALIASES = Set.of("where", "join", "left", "right", "inner",
			"outer", "cross", "full", "on", "group", "having", "order", "limit", "offset", "fetch", "union");

	private final LineageMetadataService metadataService;

	private final DataAgentProperties properties;

	public List<Map<String, String>> querySources(Integer datasourceId, DbConfigBO dbConfig, Accessor accessor,
			String businessSql) {
		if (datasourceId == null || dbConfig == null || accessor == null || StringUtils.isBlank(businessSql)
				|| !"mysql".equalsIgnoreCase(dbConfig.getDialectType())) {
			return List.of();
		}

		DatasourceLineageMetadata metadata = metadataService.getMetadata(datasourceId);
		List<String> lineageQueries = buildLineageQueries(businessSql, metadata);
		if (lineageQueries.isEmpty()) {
			return List.of();
		}

		Map<String, Map<String, String>> deduplicated = new LinkedHashMap<>();
		for (String lineageSql : lineageQueries) {
			try {
				DbQueryParameter parameter = new DbQueryParameter();
				parameter.setSchema(dbConfig.getSchema());
				parameter.setSql(lineageSql);
				ResultSetBO result = accessor.executeSqlAndReturnObject(dbConfig, parameter);
				if (result == null || result.getData() == null) {
					continue;
				}
				for (Map<String, String> row : result.getData()) {
					Map<String, String> source = normalizeSource(row);
					if (StringUtils.isBlank(source.get("source_file_name"))
							|| StringUtils.isBlank(source.get("source_file_sha256"))) {
						continue;
					}
					String key = source.getOrDefault("source_resource_id", "") + "|"
							+ source.get("source_file_sha256") + "|" + source.getOrDefault("source_sheet", "");
					deduplicated.putIfAbsent(key, source);
				}
			}
			catch (Exception exception) {
				log.warn("XLSX lineage query failed for datasource {}, business query remains available. SQL: {}",
						datasourceId, lineageSql, exception);
			}
		}
		return new ArrayList<>(deduplicated.values());
	}

	List<String> buildLineageQueries(String businessSql, DatasourceLineageMetadata metadata) {
		if (metadata == null || metadata.isEmpty() || StringUtils.isBlank(businessSql)) {
			return List.of();
		}
		String sql = stripTerminator(businessSql.trim());
		if (findTopLevelKeyword(sql, "union", 0) >= 0 || findTopLevelKeyword(sql, "having", 0) >= 0) {
			log.debug("Skipping automatic XLSX lineage for compound or HAVING query");
			return List.of();
		}

		int fromIndex = findTopLevelKeyword(sql, "from", 0);
		if (fromIndex < 0) {
			return List.of();
		}
		int endIndex = findFirstTopLevelClause(sql, fromIndex + 4);
		String fromAndFilters = sql.substring(fromIndex, endIndex).trim();
		Map<String, TableOccurrence> occurrences = findTableOccurrences(fromAndFilters, metadata.tables().values());
		if (occurrences.isEmpty()) {
			return List.of();
		}

		Set<String> queries = new LinkedHashSet<>();
		for (TableLineageMetadata table : metadata.tables().values()) {
			TableOccurrence occurrence = occurrences.get(normalize(table.tableName()));
			if (occurrence == null) {
				continue;
			}
			if (table.direct()) {
				queries.add(buildQuery(table, occurrence.qualifier(), fromAndFilters));
				continue;
			}

			TableOccurrence sourceOccurrence = occurrences.get(normalize(table.sourceTableName()));
			if (sourceOccurrence != null) {
				queries.add(buildQuery(table, sourceOccurrence.qualifier(), fromAndFilters));
			}
			else if (StringUtils.isNotBlank(table.localJoinColumn())
					&& StringUtils.isNotBlank(table.sourceJoinColumn())) {
				String sourceAlias = uniqueSourceAlias(occurrences);
				String joinedFrom = addSourceJoin(fromAndFilters, occurrence.qualifier(), table, sourceAlias);
				queries.add(buildQuery(table, sourceAlias, joinedFrom));
			}
		}
		return new ArrayList<>(queries);
	}

	private String buildQuery(TableLineageMetadata table, String sourceQualifier, String fromAndFilters) {
		String qualifier = quoteIdentifier(unquote(sourceQualifier));
		List<String> selections = new ArrayList<>();
		selections.add("'" + table.sourceTableName().replace("'", "''") + "' AS `source_table`");
		selections.add(qualifier + "." + quoteIdentifier(table.fileNameColumn()) + " AS `source_file_name`");
		selections.add(qualifier + "." + quoteIdentifier(table.fileHashColumn()) + " AS `source_file_sha256`");
		selections.add(optionalSelection(qualifier, table.sheetColumn(), "source_sheet"));
		selections.add(optionalSelection(qualifier, table.importedAtColumn(), "source_imported_at"));
		selections.add(optionalSelection(qualifier, table.platformColumn(), "source_platform"));
		selections.add(optionalSelection(qualifier, table.sourceTypeColumn(), "source_type"));
		selections.add(optionalSelection(qualifier, table.uriColumn(), "source_uri"));
		selections.add(optionalSelection(qualifier, table.resourceIdColumn(), "source_resource_id"));
		selections.add(optionalSelection(qualifier, table.versionColumn(), "source_version"));
		selections.add(optionalSelection(qualifier, table.acquiredAtColumn(), "source_acquired_at"));
		selections.add(optionalSelection(qualifier, table.artifactFormatColumn(), "artifact_format"));
		selections.add(optionalSelection(qualifier, table.ingestionToolColumn(), "ingestion_tool"));
		return "SELECT DISTINCT " + String.join(", ", selections) + " " + fromAndFilters + " LIMIT "
				+ Math.max(1, properties.getLineage().getMaxSourceRows());
	}

	private String optionalSelection(String qualifier, String column, String alias) {
		if (StringUtils.isBlank(column)) {
			return "NULL AS `" + alias + "`";
		}
		return qualifier + "." + quoteIdentifier(column) + " AS `" + alias + "`";
	}

	private String addSourceJoin(String fromAndFilters, String childQualifier, TableLineageMetadata table,
			String sourceAlias) {
		int whereIndex = findTopLevelKeyword(fromAndFilters, "where", 0);
		String tablesPart = whereIndex < 0 ? fromAndFilters : fromAndFilters.substring(0, whereIndex).trim();
		String filtersPart = whereIndex < 0 ? "" : " " + fromAndFilters.substring(whereIndex).trim();
		return tablesPart + " JOIN " + quoteIdentifier(table.sourceTableName()) + " " + quoteIdentifier(sourceAlias)
				+ " ON " + quoteIdentifier(unquote(childQualifier)) + "." + quoteIdentifier(table.localJoinColumn())
				+ " = " + quoteIdentifier(sourceAlias) + "." + quoteIdentifier(table.sourceJoinColumn()) + filtersPart;
	}

	private Map<String, TableOccurrence> findTableOccurrences(String sql,
			Iterable<TableLineageMetadata> metadata) {
		Map<String, TableOccurrence> result = new LinkedHashMap<>();
		Set<String> tableNames = new HashSet<>();
		for (TableLineageMetadata table : metadata) {
			tableNames.add(table.tableName());
			tableNames.add(table.sourceTableName());
		}
		String identifier = "[`\"]?[A-Za-z0-9_$]+[`\"]?";
		for (String tableName : tableNames) {
			Pattern pattern = Pattern.compile("(?i)\\b(?:from|join)\\s+(?:" + identifier + "\\s*\\.\\s*)?[`\"]?"
					+ Pattern.quote(tableName) + "[`\"]?(?:\\s+(?:as\\s+)?(" + identifier + "))?");
			Matcher matcher = pattern.matcher(sql);
			while (matcher.find()) {
				if (depthAt(sql, matcher.start()) != 0) {
					continue;
				}
				String alias = matcher.group(1);
				if (alias == null || RESERVED_ALIASES.contains(normalize(unquote(alias)))) {
					alias = tableName;
				}
				result.putIfAbsent(normalize(tableName), new TableOccurrence(tableName, alias));
				break;
			}
		}
		return result;
	}

	private int findFirstTopLevelClause(String sql, int start) {
		int result = sql.length();
		for (String keyword : List.of("group", "order", "limit", "offset", "fetch", "union", "intersect", "except",
				"for")) {
			int index = findTopLevelKeyword(sql, keyword, start);
			if (index >= 0) {
				result = Math.min(result, index);
			}
		}
		return result;
	}

	private int findTopLevelKeyword(String sql, String keyword, int start) {
		int depth = 0;
		char quote = 0;
		for (int index = Math.max(0, start); index <= sql.length() - keyword.length(); index++) {
			char current = sql.charAt(index);
			if (quote != 0) {
				if (current == quote && (index + 1 >= sql.length() || sql.charAt(index + 1) != quote)) {
					quote = 0;
				}
				else if (current == quote && index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
					index++;
				}
				continue;
			}
			if (current == '\'' || current == '"' || current == '`') {
				quote = current;
				continue;
			}
			if (current == '(') {
				depth++;
				continue;
			}
			if (current == ')') {
				depth = Math.max(0, depth - 1);
				continue;
			}
			if (depth == 0 && sql.regionMatches(true, index, keyword, 0, keyword.length())
					&& isWordBoundary(sql, index - 1) && isWordBoundary(sql, index + keyword.length())) {
				return index;
			}
		}
		return -1;
	}

	private int depthAt(String sql, int position) {
		int depth = 0;
		char quote = 0;
		for (int index = 0; index < Math.min(position, sql.length()); index++) {
			char current = sql.charAt(index);
			if (quote != 0) {
				if (current == quote) {
					quote = 0;
				}
				continue;
			}
			if (current == '\'' || current == '"' || current == '`') {
				quote = current;
			}
			else if (current == '(') {
				depth++;
			}
			else if (current == ')') {
				depth = Math.max(0, depth - 1);
			}
		}
		return depth;
	}

	private boolean isWordBoundary(String value, int index) {
		return index < 0 || index >= value.length()
				|| !(Character.isLetterOrDigit(value.charAt(index)) || value.charAt(index) == '_');
	}

	private String uniqueSourceAlias(Map<String, TableOccurrence> occurrences) {
		Set<String> qualifiers = new HashSet<>();
		for (TableOccurrence occurrence : occurrences.values()) {
			qualifiers.add(normalize(unquote(occurrence.qualifier())));
		}
		String alias = "__lineage_source";
		int suffix = 1;
		while (qualifiers.contains(normalize(alias))) {
			alias = "__lineage_source_" + suffix++;
		}
		return alias;
	}

	private Map<String, String> normalizeSource(Map<String, String> row) {
		Map<String, String> normalized = new LinkedHashMap<>();
		for (String key : List.of("source_table", "source_file_name", "source_file_sha256", "source_sheet",
				"source_imported_at", "source_platform", "source_type", "source_uri", "source_resource_id",
				"source_version", "source_acquired_at", "artifact_format", "ingestion_tool")) {
			normalized.put(key, valueIgnoreCase(row, key));
		}
		return normalized;
	}

	private String valueIgnoreCase(Map<String, String> row, String key) {
		return row.entrySet()
			.stream()
			.filter(entry -> entry.getKey().equalsIgnoreCase(key))
			.map(Map.Entry::getValue)
			.findFirst()
			.orElse(null);
	}

	private String stripTerminator(String sql) {
		int end = sql.length();
		while (end > 0 && (Character.isWhitespace(sql.charAt(end - 1)) || sql.charAt(end - 1) == ';')) {
			end--;
		}
		return sql.substring(0, end);
	}

	private String quoteIdentifier(String identifier) {
		return "`" + identifier.replace("`", "``") + "`";
	}

	private String unquote(String identifier) {
		if (identifier == null || identifier.length() < 2) {
			return identifier;
		}
		char first = identifier.charAt(0);
		char last = identifier.charAt(identifier.length() - 1);
		return (first == '`' && last == '`') || (first == '"' && last == '"')
				? identifier.substring(1, identifier.length() - 1) : identifier;
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	private record TableOccurrence(String tableName, String qualifier) {
	}

}
