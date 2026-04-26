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

import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * SQL utilities.
 */
@UtilityClass
public class SqlUtil {

	public static String buildSelectSql(String typeName, String tableName, String columnNames, int limit) {
		if (tableName == null || tableName.isEmpty()) {
			throw new IllegalArgumentException("Table name cannot be empty");
		}
		if (columnNames == null || columnNames.isEmpty()) {
			columnNames = "*";
		}

		if (BizDataSourceTypeEnum.isSqlServerDialect(typeName)) {
			return String.format("SELECT TOP %d %s FROM %s", limit, columnNames, tableName);
		}
		if (BizDataSourceTypeEnum.isOracleDialect(typeName)) {
			return String.format("SELECT %s FROM %s FETCH FIRST %d ROWS ONLY", columnNames, tableName, limit);
		}
		return String.format("SELECT %s FROM %s LIMIT %d", columnNames, tableName, limit);
	}

	public static String quoteIdentifier(String typeName, String identifier) {
		if (identifier == null || identifier.isBlank()) {
			throw new IllegalArgumentException("Identifier cannot be empty");
		}
		String trimmed = identifier.trim();
		if ("*".equals(trimmed)) {
			return trimmed;
		}
		String normalizedType = typeName == null ? "" : typeName.toLowerCase(Locale.ROOT);
		boolean mysqlLikeDialect = BizDataSourceTypeEnum.isMysqlDialect(normalizedType);
		String quoteStart = mysqlLikeDialect ? "`" : "\"";
		String quoteEnd = mysqlLikeDialect ? "`" : "\"";
		return Arrays.stream(trimmed.split("\\."))
			.map(String::trim)
			.filter(part -> !part.isEmpty())
			.map(part -> wrapIdentifierPart(part, quoteStart, quoteEnd))
			.collect(Collectors.joining("."));
	}

	private static String wrapIdentifierPart(String identifierPart, String quoteStart, String quoteEnd) {
		String normalizedPart = identifierPart;
		if ((normalizedPart.startsWith("`") && normalizedPart.endsWith("`"))
				|| (normalizedPart.startsWith("\"") && normalizedPart.endsWith("\""))
				|| (normalizedPart.startsWith("[") && normalizedPart.endsWith("]"))) {
			normalizedPart = normalizedPart.substring(1, normalizedPart.length() - 1);
		}
		String escapedPart = normalizedPart.replace(quoteEnd, quoteEnd + quoteEnd);
		return quoteStart + escapedPart + quoteEnd;
	}

}
