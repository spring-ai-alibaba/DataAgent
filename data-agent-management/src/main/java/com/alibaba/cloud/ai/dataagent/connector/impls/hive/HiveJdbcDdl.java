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
package com.alibaba.cloud.ai.dataagent.connector.impls.hive;

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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.util.ColumnTypeUtil.wrapType;

/**
 * Hive JDBC DDL 执行器实现
 */
@Service
public class HiveJdbcDdl extends AbstractJdbcDdl {

	@Override
	public List<DatabaseInfoBO> showDatabases(Connection connection) {
		String sql = "SHOW DATABASES";
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
		StringBuilder sql = new StringBuilder("SHOW TABLES");

		if (StringUtils.isNotBlank(schema)) {
			sql.append(" IN ").append(schema);
		}

		if (StringUtils.isNotBlank(tablePattern)) {
			sql.append(" LIKE '").append(tablePattern).append("'");
		}

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
				tableInfoList.add(TableInfoBO.builder().name(tableName).build());
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}

		return tableInfoList;
	}

	@Override
	public List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables) {
		List<TableInfoBO> tableInfoList = Lists.newArrayList();

		for (String tableName : tables) {
			try {
				String sql = "DESCRIBE FORMATTED " + (StringUtils.isNotBlank(schema) ? schema + "." : "") + tableName;
				String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, sql);

				String tableComment = "";
				for (int i = 1; i < resultArr.length; i++) {
					if (resultArr[i].length >= 2 && "comment".equalsIgnoreCase(resultArr[i][0].trim())) {
						tableComment = resultArr[i][1];
						break;
					}
				}

				tableInfoList.add(TableInfoBO.builder().name(tableName).description(tableComment).build());
			}
			catch (SQLException e) {
				tableInfoList.add(TableInfoBO.builder().name(tableName).build());
			}
		}

		return tableInfoList;
	}

	@Override
	public List<ColumnInfoBO> showColumns(Connection connection, String schema, String table) {
		String fullTableName = StringUtils.isNotBlank(schema) ? schema + "." + table : table;
		String sql = "DESCRIBE " + fullTableName;

		List<ColumnInfoBO> columnInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, sql);
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}

			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length < 2) {
					continue;
				}

				String colName = resultArr[i][0];
				String dataType = resultArr[i][1];
				String comment = resultArr[i].length >= 3 ? resultArr[i][2] : "";

				if (StringUtils.isBlank(colName) || colName.startsWith("#")) {
					continue;
				}

				columnInfoList.add(ColumnInfoBO.builder()
					.name(colName)
					.description(comment)
					.type(wrapType(dataType))
					.primary(false) // Hive 不支持主键
					.notnull(false) // Hive 不强制非空约束
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
		String fullTableName = StringUtils.isNotBlank(schema) ? schema + "." + table : table;
		String sql = String.format("SELECT `%s` FROM %s LIMIT 99", column, fullTableName);

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
		catch (SQLException e) {
		}

		// 去重
		Set<String> siSet = sampleInfo.stream().collect(Collectors.toSet());
		sampleInfo = siSet.stream().collect(Collectors.toList());
		return sampleInfo;
	}

	@Override
	public ResultSetBO scanTable(Connection connection, String schema, String table) {
		String fullTableName = StringUtils.isNotBlank(schema) ? schema + "." + table : table;
		String sql = String.format("SELECT * FROM %s LIMIT 20", fullTableName);

		ResultSetBO resultSet = ResultSetBO.builder().build();
		try {
			resultSet = SqlExecutor.executeSqlAndReturnObject(connection, schema, sql);
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return resultSet;
	}

	@Override
	public BizDataSourceTypeEnum getDataSourceType() {
		return BizDataSourceTypeEnum.HIVE;
	}

}
