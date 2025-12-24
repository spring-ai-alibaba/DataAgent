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
package com.alibaba.cloud.ai.dataagent.connector.accessor.impls.sqlserver;

import com.alibaba.cloud.ai.dataagent.bo.schema.*;
import com.alibaba.cloud.ai.dataagent.common.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.common.util.ColumnTypeUtil.wrapType;

/**
 * @author zihen
 * @date 2025/12/14 17:34
 */
@Service
public class SqlServerJdbcDdl extends AbstractJdbcDdl {

	private static final Logger log = LoggerFactory.getLogger(SqlServerJdbcDdl.class);

	@Override
	public List<DatabaseInfoBO> showDatabases(Connection connection) {
		String sql = "SELECT name FROM sys.databases WHERE database_id > 4;";
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
		String sql = "SELECT schema_name FROM INFORMATION_SCHEMA.SCHEMATA WHERE schema_name NOT IN ('sys', 'INFORMATION_SCHEMA', 'guest', 'db_owner', 'db_accessadmin', 'db_securityadmin', 'db_ddladmin', 'db_backupoperator', 'db_datareader', 'db_datawriter', 'db_denydatareader', 'db_denydatawriter');";
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
		String sql = "SELECT t.TABLE_NAME, \n" + "CAST(ep.value AS NVARCHAR(MAX)) AS TABLE_COMMENT \n"
				+ "FROM INFORMATION_SCHEMA.TABLES t \n" + "LEFT JOIN sys.tables st ON t.TABLE_NAME = st.name \n"
				+ "AND SCHEMA_NAME(st.schema_id) = t.TABLE_SCHEMA "
				+ "LEFT JOIN sys.extended_properties ep ON st.object_id = ep.major_id AND ep.minor_id = 0 AND ep.name = 'MS_Description' \n"
				+ "WHERE t.TABLE_SCHEMA = '%s' AND t.TABLE_TYPE = 'BASE TABLE' \n";
		if (StringUtils.isNotBlank(tablePattern)) {
			sql += "AND t.TABLE_NAME LIKE '%%' + '%s' + '%%' \n";
		}
		sql += "ORDER BY t.TABLE_NAME \n";
		sql += "OFFSET 0 ROWS FETCH NEXT 2000 ROWS ONLY;";

		List<TableInfoBO> tableInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection,
					String.format(sql, schema, tablePattern));
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
		String sql = "SELECT t.TABLE_NAME, \n" + "CAST(ep.value AS NVARCHAR(MAX)) AS TABLE_COMMENT \n"
				+ "FROM INFORMATION_SCHEMA.TABLES t \n" + "LEFT JOIN sys.tables st ON t.TABLE_NAME = st.name \n"
				+ "LEFT JOIN sys.extended_properties ep ON st.object_id = ep.major_id AND ep.minor_id = 0 AND ep.name = 'MS_Description' \n"
				+ "WHERE t.TABLE_SCHEMA = '%s' AND t.TABLE_TYPE = 'BASE TABLE' \n" + "AND t.TABLE_NAME IN (%s) \n"
				+ "ORDER BY t.TABLE_NAME;";

		List<TableInfoBO> tableInfoList = Lists.newArrayList();
		String tableListStr = String.join(", ", tables.stream().map(x -> "'" + x + "'").collect(Collectors.toList()));
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
		String sql = "SELECT \n" + "c.COLUMN_NAME, \n" + "CAST(ep.value AS NVARCHAR(MAX)) AS COLUMN_COMMENT, \n"
				+ "c.DATA_TYPE, \n"
				+ "CASE WHEN pk.COLUMN_NAME IS NOT NULL THEN 'true' ELSE 'false' END AS IS_PRIMARY_KEY, \n"
				+ "CASE WHEN c.IS_NULLABLE = 'NO' THEN 'true' ELSE 'false' END AS IS_NOT_NULL \n"
				+ "FROM INFORMATION_SCHEMA.COLUMNS c \n"
				+ "LEFT JOIN sys.columns sc ON OBJECT_ID(c.TABLE_SCHEMA + '.' + c.TABLE_NAME) = sc.object_id AND c.COLUMN_NAME = sc.name \n"
				+ "LEFT JOIN sys.extended_properties ep ON sc.object_id = ep.major_id AND sc.column_id = ep.minor_id AND ep.name = 'MS_Description' \n"
				+ "LEFT JOIN ( \n" + "    SELECT ku.TABLE_SCHEMA, ku.TABLE_NAME, ku.COLUMN_NAME \n"
				+ "    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc \n"
				+ "    JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE ku ON tc.CONSTRAINT_NAME = ku.CONSTRAINT_NAME \n"
				+ "    WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY' \n"
				+ ") pk ON c.TABLE_SCHEMA = pk.TABLE_SCHEMA AND c.TABLE_NAME = pk.TABLE_NAME AND c.COLUMN_NAME = pk.COLUMN_NAME \n"
				+ "WHERE c.TABLE_SCHEMA = '%s' AND c.TABLE_NAME = '%s' \n" + "ORDER BY c.ORDINAL_POSITION;";

		List<ColumnInfoBO> columnInfoList = Lists.newArrayList();
		try {
			String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, null,
					String.format(sql, schema, table));
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
		String sql = "SELECT \n" + "FK.TABLE_NAME AS 'Table', \n" + "CU.COLUMN_NAME AS 'Column', \n"
				+ "PK.TABLE_NAME AS 'Referenced_Table', \n" + "PT.COLUMN_NAME AS 'Referenced_Column' \n"
				+ "FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS C \n"
				+ "INNER JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS FK ON C.CONSTRAINT_NAME = FK.CONSTRAINT_NAME \n"
				+ "INNER JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS PK ON C.UNIQUE_CONSTRAINT_NAME = PK.CONSTRAINT_NAME \n"
				+ "INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE CU ON C.CONSTRAINT_NAME = CU.CONSTRAINT_NAME \n"
				+ "INNER JOIN (SELECT i1.TABLE_NAME, i2.COLUMN_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS i1 \n"
				+ "INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE i2 ON i1.CONSTRAINT_NAME = i2.CONSTRAINT_NAME \n"
				+ "WHERE i1.CONSTRAINT_TYPE = 'PRIMARY KEY') PT ON PT.TABLE_NAME = PK.TABLE_NAME \n"
				+ "WHERE FK.TABLE_SCHEMA = '%s' AND FK.TABLE_NAME IN (%s);";

		List<ForeignKeyInfoBO> foreignKeyInfoList = Lists.newArrayList();
		String tableListStr = String.join(", ", tables.stream().map(x -> "'" + x + "'").collect(Collectors.toList()));

		try {
			sql = String.format(sql, schema, tableListStr);
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
					.referencedTable(resultArr[i][2])
					.referencedColumn(resultArr[i][3])
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
		String sql = super.getSelectSql(BizDataSourceTypeEnum.SQL_SERVER.getTypeName(), table, column, 99);
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
			log.error("sampleColumn error, sql:{}", sql);
			log.error("sampleColumn error", e);
		}

		return new ArrayList<>(new HashSet<>(sampleInfo));
	}

	@Override
	public ResultSetBO scanTable(Connection connection, String schema, String table) {
		String sql = super.getSelectSql(BizDataSourceTypeEnum.SQL_SERVER.getTypeName(), table, "*", 20);
		ResultSetBO resultSet;
		try {
			resultSet = SqlExecutor.executeSqlAndReturnObject(connection, schema, String.format(sql, schema, table));
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return resultSet;
	}

	@Override
	public BizDataSourceTypeEnum getDataSourceType() {
		return BizDataSourceTypeEnum.SQL_SERVER;
	}

}
