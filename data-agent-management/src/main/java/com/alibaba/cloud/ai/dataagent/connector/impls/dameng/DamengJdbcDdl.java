/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.connector.impls.dameng;

import com.alibaba.cloud.ai.dataagent.bo.schema.*;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.util.ColumnTypeUtil.wrapType;

@Service
public class DamengJdbcDdl extends AbstractJdbcDdl {

	@Override
	public List<DatabaseInfoBO> showDatabases(Connection connection) {
		// 达梦通常以实例+用户作为schema，数据库枚举意义不大，这里返回空集
		return Collections.emptyList();
	}

	@Override
	public List<SchemaInfoBO> showSchemas(Connection connection) {
		String sql = "SELECT USERNAME FROM SYS.ALL_USERS";
		List<SchemaInfoBO> schemaInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, sql);
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}
			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				String schema = resultArr[i][0];
				schemaInfoList.add(SchemaInfoBO.builder().name(schema).build());
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return schemaInfoList;
	}

	@Override
	public List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern) {
		String sql = "SELECT TABLE_NAME FROM USER_TABLES";
		if (StringUtils.isNotBlank(tablePattern)) {
			sql = "SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME LIKE '%' || '" + tablePattern + "' || '%'";
		}
		List<TableInfoBO> tableInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, sql);
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}
			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				String tableName = resultArr[i][0];
				tableInfoList.add(TableInfoBO.builder().name(tableName).description("").build());
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
		String tableListStr = String.join(", ", tables.stream().map(x -> "'" + x + "'").collect(Collectors.toList()));
		String sql = "SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME in(" + tableListStr + ")";
		List<TableInfoBO> tableInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, sql);
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}
			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				String tableName = resultArr[i][0];
				tableInfoList.add(TableInfoBO.builder().name(tableName).description("").build());
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return tableInfoList;
	}

	@Override
	public List<ColumnInfoBO> showColumns(Connection connection, String schema, String table) {
		String sql = "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE FROM USER_TAB_COLUMNS WHERE TABLE_NAME='"
				+ table + "'";
		List<ColumnInfoBO> columnInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, null, sql);
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}
			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				columnInfoList.add(ColumnInfoBO.builder()
					.name(resultArr[i][0])
					.description("")
					.type(wrapType(resultArr[i][1]))
					.primary(false)
					.notnull(BooleanUtils.toBoolean("N".equalsIgnoreCase(resultArr[i][3]) ? "true" : "false"))
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
		if (tables == null || tables.isEmpty()) {
			return Lists.newArrayList();
		}
		String tableListStr = String.join(", ", tables.stream().map(x -> "'" + x + "'").collect(Collectors.toList()));
		String sql = "SELECT uc.TABLE_NAME, ucc.COLUMN_NAME, uc.CONSTRAINT_NAME, uc.R_OWNER, uc.R_CONSTRAINT_NAME "
				+ "FROM USER_CONSTRAINTS uc JOIN USER_CONS_COLUMNS ucc ON uc.CONSTRAINT_NAME = ucc.CONSTRAINT_NAME "
				+ "WHERE uc.CONSTRAINT_TYPE='R' AND uc.TABLE_NAME IN (" + tableListStr + ")";
		List<ForeignKeyInfoBO> foreignKeyInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, null, sql);
			if (resultArr.length <= 1) {
				return Lists.newArrayList();
			}
			for (int i = 1; i < resultArr.length; i++) {
				if (resultArr[i].length == 0) {
					continue;
				}
				foreignKeyInfoList.add(ForeignKeyInfoBO.builder()
					.table(resultArr[i][0])
					.column(resultArr[i][1])
					.referencedTable("")
					.referencedColumn("")
					.build());
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return foreignKeyInfoList;
	}

	@Override
	public List<String> sampleColumn(Connection connection, String schema, String table, String column) {
		String sql = "SELECT " + column + " FROM " + table + " FETCH FIRST 99 ROWS ONLY";
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
			// ignore
		}
		Set<String> siSet = sampleInfo.stream().collect(Collectors.toSet());
		sampleInfo = siSet.stream().collect(Collectors.toList());
		return sampleInfo;
	}

	@Override
	public ResultSetBO scanTable(Connection connection, String schema, String table) {
		String sql = "SELECT * FROM " + table + " FETCH FIRST 20 ROWS ONLY";
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
		return BizDataSourceTypeEnum.DAMENG;
	}

}
