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

import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
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
public class ClickHouseJdbcDdl extends AbstractJdbcDdl {

    @Override
    public List<DatabaseInfoBO> showDatabases(Connection connection) {
        String sql = "SELECT name FROM system.databases ORDER BY name";
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
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return databaseInfoList;
    }

    @Override
    public List<SchemaInfoBO> showSchemas(Connection connection) {
        // ClickHouse does not have the concept of schemas, return empty list
        return Collections.emptyList();
    }

    @Override
    public List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern) {
        String sql = "SELECT name, comment FROM system.tables WHERE database = '%s' \n";
        if (StringUtils.isNotBlank(tablePattern)) {
            sql += "AND name LIKE '%%%s%%' \n";
        }
        sql += "ORDER BY name LIMIT 2000";
        List<TableInfoBO> tableInfoList = Lists.newArrayList();
        try {
            String formattedSql;
            if (StringUtils.isNotBlank(tablePattern)) {
                formattedSql = String.format(sql, connection.getSchema(), tablePattern);
            } else {
                formattedSql = String.format(sql, connection.getSchema());
            }
            String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection, formattedSql);
            if (resultArr.length <= 1) {
                return Lists.newArrayList();
            }

            for (int i = 1; i < resultArr.length; i++) {
                if (resultArr[i].length == 0) {
                    continue;
                }
                String tableName = resultArr[i][0];
                String tableDesc = resultArr[i].length > 1 ? resultArr[i][1] : "";
                tableInfoList.add(TableInfoBO.builder().name(tableName).description(tableDesc).build());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return tableInfoList;
    }

    @Override
    public List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables) {
        String sql = "SELECT name, comment FROM system.tables "
                + "WHERE database = '%s' AND name IN (%s) ORDER BY name LIMIT 200";
        List<TableInfoBO> tableInfoList = Lists.newArrayList();
        String tableListStr = String.join(", ", tables.stream().map(x -> "'" + x + "'").collect(Collectors.toList()));
        try {
            String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection,
                    String.format(sql, connection.getCatalog(), tableListStr));
            if (resultArr.length <= 1) {
                return Lists.newArrayList();
            }

            for (int i = 1; i < resultArr.length; i++) {
                if (resultArr[i].length == 0) {
                    continue;
                }
                String tableName = resultArr[i][0];
                String tableDesc = resultArr[i].length > 1 ? resultArr[i][1] : "";
                tableInfoList.add(TableInfoBO.builder().name(tableName).description(tableDesc).build());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return tableInfoList;
    }

    @Override
    public List<ColumnInfoBO> showColumns(Connection connection, String schema, String table) {
        String sql = "SELECT name, comment, type, "
                + "if(is_in_primary_key = 1, 'true', 'false') AS is_primary, "
                + "if(position(type, 'Nullable') = 0, 'true', 'false') AS notnull "
                + "FROM system.columns WHERE database = '%s' AND table = '%s'";
        List<ColumnInfoBO> columnInfoList = Lists.newArrayList();
        try {
            String[][] resultArr = SqlExecutor.executeSqlAndReturnArr(connection,
                    String.format(sql, connection.getCatalog(), table));
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
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return columnInfoList;
    }

    @Override
    public List<ForeignKeyInfoBO> showForeignKeys(Connection connection, String schema, List<String> tables) {
        // ClickHouse does not support foreign keys, return empty list
        return Collections.emptyList();
    }

    @Override
    public List<String> sampleColumn(Connection connection, String schema, String table, String column) {
        String sql = "SELECT `%s` FROM `%s` LIMIT 99";
        List<String> sampleInfo = Lists.newArrayList();
        try {
            sql = String.format(sql, column, table);
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
        } catch (SQLException e) {
            // Silently handle sampling errors
        }

        Set<String> siSet = sampleInfo.stream().collect(Collectors.toSet());
        sampleInfo = siSet.stream().collect(Collectors.toList());
        return sampleInfo;
    }

    @Override
    public ResultSetBO scanTable(Connection connection, String schema, String table) {
        String sql = "SELECT * FROM `%s` LIMIT 20";
        ResultSetBO resultSet = ResultSetBO.builder().build();
        try {
            resultSet = SqlExecutor.executeSqlAndReturnObject(connection, schema, String.format(sql, table));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return resultSet;
    }

    @Override
    public BizDataSourceTypeEnum getDataSourceType() {
        return BizDataSourceTypeEnum.CLICKHOUSE;
    }

}
