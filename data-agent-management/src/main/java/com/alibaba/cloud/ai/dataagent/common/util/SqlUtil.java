/*
 * Copyright 2025 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.common.util;

import com.alibaba.cloud.ai.dataagent.common.enums.BizDataSourceTypeEnum;
import lombok.experimental.UtilityClass;

/**
 * SQL 工具类
 *
 * @author Yang Yufeng
 * @version 1.0
 */
@UtilityClass
public class SqlUtil {

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
		else {
			// MySQL, PostgreSQL, H2, SQLite 通用 LIMIT
			return String.format("SELECT %s FROM %s LIMIT %d", columnNames, tableName, limit);
		}
	}

}
