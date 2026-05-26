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

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SemanticContext {

	private final List<ResolvedSemanticTable> tables;

	private final Map<String, ResolvedSemanticTable> tablesByName;

	private final Map<String, List<ResolvedSemanticColumn>> columnsByTable;

	private final Map<String, List<ResolvedSemanticRelation>> relationsByTable;

	public SemanticContext(List<ResolvedSemanticTable> tables, Map<String, ResolvedSemanticTable> tablesByName,
			Map<String, List<ResolvedSemanticColumn>> columnsByTable,
			Map<String, List<ResolvedSemanticRelation>> relationsByTable) {
		this.tables = List.copyOf(tables);
		this.tablesByName = Map.copyOf(tablesByName);
		this.columnsByTable = Map.copyOf(columnsByTable);
		this.relationsByTable = Map.copyOf(relationsByTable);
	}

	public List<ResolvedSemanticTable> listTables() {
		return tables;
	}

	public ResolvedSemanticTable findTable(String normalizedTableName) {
		return tablesByName.get(normalizedTableName);
	}

	public List<ResolvedSemanticColumn> listColumns(String normalizedTableName) {
		return columnsByTable.getOrDefault(normalizedTableName, List.of());
	}

	public List<ResolvedSemanticRelation> listRelations(String normalizedTableName) {
		return relationsByTable.getOrDefault(normalizedTableName, List.of());
	}

	public Map<String, List<ResolvedSemanticRelation>> relationsByTable() {
		return Collections.unmodifiableMap(relationsByTable);
	}

}
