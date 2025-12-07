/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.common.enums.BizDataSourceTypeEnum;

public class DatabaseDialectHelper {

	/**
	 * 获取数据库特定的SQL函数指南
	 * @param dialectType 数据库方言类型（如 "mysql", "postgresql"）
	 * @return 该数据库的SQL语法指南文本
	 */
	public static String getDialectSpecificGuidelines(String dialectType) {
		BizDataSourceTypeEnum typeEnum = BizDataSourceTypeEnum.fromTypeName(dialectType);

		return switch (typeEnum) {
			case MYSQL -> """
					## MySQL特定语法要求：
					- **日期格式化**：必须使用 DATE_FORMAT(date, format)
					  正确：DATE_FORMAT(created_time, '%Y-%m-%d')
					  错误：strftime('%Y-%m-%d', created_time)  ← SQLite语法

					- **字符串拼接**：使用 CONCAT(str1, str2, ...)
					  正确：CONCAT(first_name, ' ', last_name)
					  错误：first_name || ' ' || last_name  ← PostgreSQL语法

					- **日期计算**：DATE_ADD() 或 DATE_SUB()
					  正确：DATE_ADD(order_date, INTERVAL 7 DAY)
					  错误：order_date + INTERVAL '7 days'  ← PostgreSQL语法

					- **限制行数**：LIMIT n [OFFSET m]
					- **分组拼接**：GROUP_CONCAT(column SEPARATOR ',')
					""";

			case POSTGRESQL -> """
					## PostgreSQL特定语法要求：
					- **日期格式化**：必须使用 TO_CHAR(timestamp, text)
					  正确：TO_CHAR(created_time, 'YYYY-MM-DD')
					  错误：DATE_FORMAT(created_time, '%Y-%m-%d')  ← MySQL语法

					- **字符串拼接**：使用 || 运算符
					  正确：first_name || ' ' || last_name

					- **日期计算**：使用 INTERVAL
					  正确：order_date + INTERVAL '7 days'

					- **分组拼接**：STRING_AGG(column, ',')
					""";

			default -> """
					## 通用SQL语法要求：
					- 使用标准ANSI SQL语法
					- 避免使用特定数据库的专有函数
					""";
		};
	}

	// 私有构造函数，防止实例化工具类
	private DatabaseDialectHelper() {
		throw new AssertionError("Utility class should not be instantiated");
	}

}
