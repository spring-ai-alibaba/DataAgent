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
package com.alibaba.cloud.ai.dataagent.service.semantic.runtime;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticColumn;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticTable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class SemanticManager {

	private static final Pattern COLUMN_SPLITTER = Pattern.compile("\\s*,\\s*");

	public String normalizeName(String value) {
		String normalized = StringUtils.trimToEmpty(value);
		normalized = StringUtils.removeStart(normalized, "`");
		normalized = StringUtils.removeEnd(normalized, "`");
		normalized = StringUtils.removeStart(normalized, "\"");
		normalized = StringUtils.removeEnd(normalized, "\"");
		normalized = StringUtils.removeStart(normalized, "[");
		normalized = StringUtils.removeEnd(normalized, "]");
		return normalized.toLowerCase(Locale.ROOT);
	}

	public String normalizeLeafName(String value) {
		String normalized = normalizeName(value);
		int lastDot = normalized.lastIndexOf('.');
		return lastDot >= 0 ? normalized.substring(lastDot + 1) : normalized;
	}

	public boolean isTableVisible(SemanticTable semanticTable) {
		return semanticTable == null || ((semanticTable.getStatus() == null || semanticTable.getStatus() == 1)
				&& (semanticTable.getIsVisible() == null || semanticTable.getIsVisible() == 1));
	}

	public boolean isColumnVisible(SemanticColumn semanticColumn) {
		return semanticColumn == null || ((semanticColumn.getStatus() == null || semanticColumn.getStatus() == 1)
				&& (semanticColumn.getIsVisible() == null || semanticColumn.getIsVisible() == 1));
	}

	public List<String> parseColumnNames(String columnNames) {
		if (StringUtils.isBlank(columnNames)) {
			return List.of();
		}
		return Arrays.stream(COLUMN_SPLITTER.split(columnNames.trim()))
			.map(String::trim)
			.filter(StringUtils::isNotBlank)
			.distinct()
			.toList();
	}

	public Map<String, Set<String>> normalizeVisibleColumnRestrictions(Map<String, List<String>> visibleColumnsByTable) {
		Map<String, Set<String>> normalizedRestrictions = new LinkedHashMap<>();
		Optional.ofNullable(visibleColumnsByTable).orElse(Map.of()).forEach((tableName, columns) -> {
			String normalizedTableName = normalizeName(tableName);
			if (StringUtils.isBlank(normalizedTableName)) {
				return;
			}
			Set<String> normalizedColumns = Optional.ofNullable(columns)
				.orElse(List.of())
				.stream()
				.map(this::normalizeName)
				.filter(StringUtils::isNotBlank)
				.collect(Collectors.toCollection(LinkedHashSet::new));
			if (!normalizedColumns.isEmpty()) {
				normalizedRestrictions.put(normalizedTableName, Set.copyOf(normalizedColumns));
			}
		});
		return normalizedRestrictions;
	}

	public void applyVisibleColumnRestrictions(List<TableInfoBO> tables, Map<String, List<String>> visibleColumnsByTable) {
		Map<String, Set<String>> normalizedRestrictions = normalizeVisibleColumnRestrictions(visibleColumnsByTable);
		if (normalizedRestrictions.isEmpty()) {
			return;
		}
		for (TableInfoBO table : Optional.ofNullable(tables).orElse(List.of())) {
			Set<String> visibleColumns = resolveVisibleColumns(normalizedRestrictions, table.getName());
			if (visibleColumns == null) {
				continue;
			}
			List<ColumnInfoBO> filteredColumns = Optional.ofNullable(table.getColumns())
				.orElse(List.of())
				.stream()
				.filter(column -> visibleColumns.contains(normalizeName(column.getName())))
				.toList();
			table.setColumns(filteredColumns);
			List<String> filteredPrimaryKeys = Optional.ofNullable(table.getPrimaryKeys())
				.orElse(List.of())
				.stream()
				.filter(primaryKey -> visibleColumns.contains(normalizeName(primaryKey)))
				.toList();
			table.setPrimaryKeys(filteredPrimaryKeys);
		}
	}

	public Set<String> resolveVisibleColumns(Map<String, Set<String>> visibleColumnsByTable, String tableName) {
		String normalizedTableName = normalizeName(tableName);
		Set<String> exactMatch = visibleColumnsByTable.get(normalizedTableName);
		if (exactMatch != null) {
			return exactMatch;
		}
		String normalizedLeafTableName = normalizeLeafName(normalizedTableName);
		List<Set<String>> leafMatches = visibleColumnsByTable.entrySet()
			.stream()
			.filter(entry -> normalizeLeafName(entry.getKey()).equals(normalizedLeafTableName))
			.map(Map.Entry::getValue)
			.distinct()
			.toList();
		if (leafMatches.size() == 1) {
			return leafMatches.get(0);
		}
		return null;
	}

	public String summarizeRelations(List<ResolvedSemanticRelation> relations) {
		return relations.stream().map(ResolvedSemanticRelation::foreignKeyText).distinct().collect(Collectors.joining("、"));
	}

	public List<String> mergeColumnNames(List<String> physicalNames, List<String> semanticNames) {
		List<String> merged = new ArrayList<>(physicalNames);
		Set<String> normalized = physicalNames.stream()
			.map(this::normalizeName)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		for (String semanticName : semanticNames) {
			if (normalized.add(normalizeName(semanticName))) {
				merged.add(semanticName);
			}
		}
		return merged;
	}

}
