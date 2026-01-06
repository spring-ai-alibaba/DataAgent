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
package com.alibaba.cloud.ai.dataagent.connector.impls.h2;

import com.alibaba.cloud.ai.dataagent.connector.pool.AbstractDBConnectionPool;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import org.springframework.stereotype.Service;

import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.DATABASE_NOT_EXIST_42000;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.DATASOURCE_CONNECTION_FAILURE_08S01;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.OTHERS;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.PASSWORD_ERROR_28000;

@Service("h2JdbcConnectionPool")
public class H2JdbcConnectionPool extends AbstractDBConnectionPool {

	@Override
	public String getDriver() {
		return "org.h2.Driver";
	}

	@Override
	public ErrorCodeEnum errorMapping(String sqlState) {
		ErrorCodeEnum ret = ErrorCodeEnum.fromCode(sqlState);
		if (ret != null) {
			return ret;
		}
		return switch (sqlState) {
			case "08S01" -> DATASOURCE_CONNECTION_FAILURE_08S01;
			case "28000" -> PASSWORD_ERROR_28000;
			case "42000" -> DATABASE_NOT_EXIST_42000;
			default -> OTHERS;
		};
	}

	@Override
	public boolean supportedDataSourceType(String type) {
		return BizDataSourceTypeEnum.H2.getTypeName().equals(type);
	}

	@Override
	public String getConnectionPoolType() {
		return BizDataSourceTypeEnum.H2.getTypeName();
	}

}
