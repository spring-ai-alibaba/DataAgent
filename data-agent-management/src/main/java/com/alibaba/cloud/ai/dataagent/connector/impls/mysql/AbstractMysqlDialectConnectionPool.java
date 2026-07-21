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
package com.alibaba.cloud.ai.dataagent.connector.impls.mysql;

import com.alibaba.cloud.ai.dataagent.connector.pool.AbstractDBConnectionPool;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.DATABASE_NOT_EXIST_3D000;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.OTHERS;

@Slf4j
public abstract class AbstractMysqlDialectConnectionPool extends AbstractDBConnectionPool {

	private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

	@Override
	public String getDriver() {
		return DRIVER;
	}

	@Override
	public ErrorCodeEnum errorMapping(String sqlState) {
		if (sqlState == null) {
			return OTHERS;
		}

		ErrorCodeEnum ret = ErrorCodeEnum.fromCode(sqlState);
		if (ret != OTHERS) {
			return ret;
		}

		switch (sqlState) {
			case "3F000":
				return DATABASE_NOT_EXIST_3D000;
			default:
				return OTHERS;
		}
	}

	@Override
	protected boolean useWallFilter() {
		return false;
	}

}
