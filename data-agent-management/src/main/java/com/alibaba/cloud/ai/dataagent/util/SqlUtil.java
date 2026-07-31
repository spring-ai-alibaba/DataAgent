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

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * SQL 工具类
 *
 * @author Yang Yufeng
 * @version 1.0
 */
@Slf4j
@UtilityClass
public class SqlUtil {

	private static final String UNRESOLVED_PLACEHOLDER_REASON = "Generated SQL contains an unresolved '?' parameter "
			+ "placeholder. Replace every placeholder with a concrete value from the current instruction or previous "
			+ "step results.";

	/**
	 * 构建SELECT SQL语句
	 * @param typeName 数据源类型
	 * @param tableName 表名
	 * @param columnNames 列名
	 * @param limit 查询数量限制
	 * @return SELECT SQL语句
	 */
	public static String buildSelectSql(String typeName, String tableName, String columnNames, int limit) {
		if (tableName == null || tableName.isEmpty()) {
			throw new IllegalArgumentException("Table name cannot be empty");
		}
		if (columnNames == null || columnNames.isEmpty()) {
			columnNames = "*";
		}

		if (BizDataSourceTypeEnum.isSqlServerDialect(typeName)) {
			// SQL Server 使用 TOP
			return String.format("SELECT TOP %d %s FROM %s", limit, columnNames, tableName);
		}
		else if (BizDataSourceTypeEnum.isOracleDialect(typeName)) {
			// Oracle 使用 FETCH FIRST (Oracle 12c+)
			return String.format("SELECT %s FROM %s FETCH FIRST %d ROWS ONLY", columnNames, tableName, limit);
		}
		else {
			// MySQL, PostgreSQL, H2, SQLite 通用 LIMIT
			return String.format("SELECT %s FROM %s LIMIT %d", columnNames, tableName, limit);
		}
	}

	/**
	 * Validate structural constraints that must hold before generated SQL reaches the
	 * database.
	 * @param sql generated SQL
	 * @param dialect database dialect
	 * @return a regeneration reason when a deterministic constraint fails
	 */
	public static Optional<String> findGeneratedSqlValidationError(String sql, String dialect) {
		try {
			List<SQLStatement> statements = SQLUtils.parseStatements(sql, resolveDbType(dialect));
			if (statements.size() != 1) {
				return Optional.of("Generated SQL contains " + statements.size()
						+ " executable statements. Return exactly one SQL statement for the current plan step.");
			}
			if (containsUnresolvedPlaceholder(statements.get(0))) {
				return Optional.of(UNRESOLVED_PLACEHOLDER_REASON);
			}
		}
		catch (RuntimeException ex) {
			// Syntax and dialect errors continue through the existing semantic/execution
			// retry path. This guard only rejects the two deterministic conditions above.
			log.debug("Unable to parse generated SQL for structural validation; using existing validation flow", ex);
		}
		return Optional.empty();
	}

	private static boolean containsUnresolvedPlaceholder(SQLStatement statement) {
		boolean[] found = { false };
		statement.accept(new SQLASTVisitorAdapter() {
			@Override
			public boolean visit(SQLVariantRefExpr variant) {
				if ("?".equals(variant.getName())) {
					found[0] = true;
				}
				return !found[0];
			}
		});
		return found[0];
	}

	private static DbType resolveDbType(String dialect) {
		String normalizedDialect = dialect == null ? "" : dialect.toLowerCase(Locale.ROOT);
		if ("dameng".equals(normalizedDialect)) {
			return DbType.dm;
		}
		DbType dbType = DbType.of(normalizedDialect);
		return dbType == null ? DbType.other : dbType;
	}

}
