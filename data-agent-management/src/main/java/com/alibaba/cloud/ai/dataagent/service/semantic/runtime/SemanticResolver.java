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
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticColumn;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticRelation;
import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticTable;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class SemanticResolver {

	private final SemanticManager semanticManager;

	public SemanticResolver(SemanticManager semanticManager) {
		this.semanticManager = semanticManager;
	}

	public ResolvedSemanticTable resolveTable(TableInfoBO physicalTable, SemanticTable semanticTable) {
		String tableName = physicalTable != null ? physicalTable.getName()
				: semanticTable != null ? semanticTable.getTableName() : null;
		String physicalDescription = physicalTable != null ? StringUtils.trimToNull(physicalTable.getDescription()) : null;
		String description = firstNonBlank(semanticTable != null ? semanticTable.getBusinessDescription() : null,
				semanticTable != null ? semanticTable.getTableComment() : null, physicalDescription);
		return new ResolvedSemanticTable(semanticTable != null ? semanticTable.getId() : null, tableName,
				firstNonBlank(semanticTable != null ? semanticTable.getBusinessName() : null, tableName),
				semanticTable != null ? semanticTable.getSynonyms() : null, description, physicalDescription,
				physicalTable != null && physicalTable.getPrimaryKeys() != null ? List.copyOf(physicalTable.getPrimaryKeys())
						: List.of(),
				semanticManager.isTableVisible(semanticTable));
	}

	public ResolvedSemanticColumn resolveColumn(String tableName, ColumnInfoBO physicalColumn, SemanticColumn semanticColumn) {
		String columnName = physicalColumn != null ? physicalColumn.getName()
				: semanticColumn != null ? semanticColumn.getColumnName() : null;
		String physicalDescription = physicalColumn != null ? StringUtils.trimToNull(physicalColumn.getDescription()) : null;
		String description = firstNonBlank(semanticColumn != null ? semanticColumn.getBusinessDescription() : null,
				semanticColumn != null ? semanticColumn.getColumnComment() : null, physicalDescription);
		String dataType = firstNonBlank(semanticColumn != null ? semanticColumn.getDataType() : null,
				physicalColumn != null ? physicalColumn.getType() : null);
		return new ResolvedSemanticColumn(semanticColumn != null ? semanticColumn.getId() : null, tableName, columnName,
				firstNonBlank(semanticColumn != null ? semanticColumn.getBusinessName() : null, columnName),
				semanticColumn != null ? semanticColumn.getSynonyms() : null, description, physicalDescription, dataType,
				physicalColumn != null && physicalColumn.isPrimary(),
				physicalColumn != null && physicalColumn.isNotnull(),
				physicalColumn != null ? physicalColumn.getSamples() : null, semanticManager.isColumnVisible(semanticColumn));
	}

	public ResolvedSemanticRelation resolvePhysicalRelation(ForeignKeyInfoBO relation) {
		return new ResolvedSemanticRelation(relation.getTable(), List.of(relation.getColumn()), relation.getReferencedTable(),
				List.of(relation.getReferencedColumn()), StringUtils.EMPTY, StringUtils.EMPTY, "physical", false, true);
	}

	public ResolvedSemanticRelation resolveSemanticRelation(SemanticRelation relation) {
		return new ResolvedSemanticRelation(relation.getSourceTableName(),
				semanticManager.parseColumnNames(relation.getSourceColumnNames()), relation.getTargetTableName(),
				semanticManager.parseColumnNames(relation.getTargetColumnNames()),
				StringUtils.defaultString(relation.getRelationType()), StringUtils.defaultString(relation.getDescription()),
				"semantic", true, false);
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			String normalized = StringUtils.trimToNull(value);
			if (normalized != null) {
				return normalized;
			}
		}
		return null;
	}

}
