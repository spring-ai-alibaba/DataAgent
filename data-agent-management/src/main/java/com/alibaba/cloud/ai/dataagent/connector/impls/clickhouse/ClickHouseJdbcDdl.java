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
package com.alibaba.cloud.ai.dataagent.connector.impls.clickhouse;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.util.ColumnTypeUtil.wrapType;

@Service
public class ClickHouseJdbcDdl extends AbstractJdbcDdl {

	@Override
	public List<DatabaseInfoBO> showDatabases(Connection connection) {
		String sql = "SELECT name FROM system.databases;";
		List<DatabaseInfoBO> databaseInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, sql);
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}

			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				String database = resultArr[i][0];
				databaseInfoList.add(DatabaseInfoBO.builder().name(database).build());
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return databaseInfoList;
	}

	@Override
	public List<SchemaInfoBO> showSchemas(Connection connection) {
		return Collections.emptyList();
	}

	@Override
	public List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern) {
		StringBuilder sql = new StringBuilder(
				"SELECT name, comment FROM system.tables WHERE database = '" + schema + "'");
		if (StringUtils.isNotBlank(tablePattern)) {
			sql.append(" AND name LIKE '%").append(tablePattern).append("%'");
		}
		sql.append(" LIMIT 2000;");
		List<TableInfoBO> tableInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, sql.toString());
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}

			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				String tableName = resultArr[i][0];
				String tableDesc = resultArr[i][1];
				tableInfoList.add(TableInfoBO.builder().name(tableName).description(tableDesc).build());
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return tableInfoList;
	}

	@Override
	public List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables) {
		if (tables == null || tables.isEmpty()) {
			return Lists.newArrayList();
		}

		String tableListStr = tables.stream().map(table -> "'" + table + "'").collect(Collectors.joining(", "));
		String sql = "SELECT name, comment FROM system.tables WHERE database = '%s' AND name IN (%s) LIMIT 2000";

		List<TableInfoBO> tableInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection,
					String.format(sql, schema, tableListStr));
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}

			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				String tableName = resultArr[i][0];
				String tableDesc = resultArr[i][1];
				tableInfoList.add(TableInfoBO.builder().name(tableName).description(tableDesc).build());
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return tableInfoList;
	}

	@Override
	public List<ColumnInfoBO> showColumns(Connection connection, String schema, String table) {
		String sql = "SELECT name, comment, type, " + "if(is_in_primary_key = 1, 'true', 'false') AS is_primary, "
				+ "if(startsWith(type, 'Nullable('), 'false', 'true') AS is_not_null "
				+ "FROM system.columns WHERE database = '%s' AND table = '%s' ORDER BY position";

		List<ColumnInfoBO> columnInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, String.format(sql, schema, table));
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}

			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				columnInfoList.add(ColumnInfoBO.builder()
					.name(resultArr[i][0])
					.description(resultArr[i][1])
					.type(wrapType(resultArr[i][2]))
					.primary(BooleanUtils.toBoolean(resultArr[i][3]))
					.notnull(BooleanUtils.toBoolean(resultArr[i][4]))
					.build());
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return columnInfoList;
	}

	@Override
	public List<ForeignKeyInfoBO> showForeignKeys(Connection connection, String schema, List<String> tables) {
		return Collections.emptyList();
	}

	@Override
	public List<String> sampleColumn(Connection connection, String schema, String table, String column) {
		String qualifiedTable = quoteIdentifier(schema) + "." + quoteIdentifier(table);
		String sql = String.format("SELECT %s FROM %s LIMIT 99", quoteIdentifier(column), qualifiedTable);

		List<String> sampleInfo = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, null, sql);
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}

			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0 || column.equalsIgnoreCase(resultArr[i][0])) {
					continue;
				}
				sampleInfo.add(resultArr[i][0]);
			}
		}
		catch (SQLException ignored) {
		}

		Set<String> siSet = new HashSet<>(sampleInfo);
		sampleInfo = new ArrayList<>(siSet);
		return sampleInfo;
	}

	@Override
	public ResultSetBO scanTable(Connection connection, String schema, String table) {
		String qualifiedTable = quoteIdentifier(schema) + "." + quoteIdentifier(table);
		String sql = String.format("SELECT * FROM %s LIMIT 20", qualifiedTable);

		ResultSetBO resultSet;
		try {
			resultSet = SqlExecutor.executeSqlAndReturnObject(connection, null, sql);
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return resultSet;
	}

	@Override
	public BizDataSourceTypeEnum getDataSourceType() {
		return BizDataSourceTypeEnum.CLICKHOUSE;
	}

	private String quoteIdentifier(String value) {
		return "`" + value + "`";
	}

}
