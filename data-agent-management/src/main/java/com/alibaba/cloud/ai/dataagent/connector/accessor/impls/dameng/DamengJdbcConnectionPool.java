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
package com.alibaba.cloud.ai.dataagent.connector.accessor.impls.dameng;

import com.alibaba.cloud.ai.dataagent.connector.pool.AbstractDBConnectionPool;
import com.alibaba.cloud.ai.dataagent.common.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.common.enums.ErrorCodeEnum;
import org.springframework.stereotype.Service;

import static com.alibaba.cloud.ai.dataagent.common.enums.ErrorCodeEnum.OTHERS;

@Service("damengJdbcConnectionPool")
public class DamengJdbcConnectionPool extends AbstractDBConnectionPool {

	private static final String DRIVER = "dm.jdbc.driver.DmDriver";

	@Override
	public String getDriver() {
		return DRIVER;
	}

	@Override
	public ErrorCodeEnum errorMapping(String sqlState) {
		ErrorCodeEnum ret = ErrorCodeEnum.fromCode(sqlState);
		if (ret != null && ret != OTHERS) {
			return ret;
		}
		return OTHERS;
	}

	@Override
	public boolean supportedDataSourceType(String type) {
		return BizDataSourceTypeEnum.DAMENG.getTypeName().equals(type);
	}

	@Override
	public String getConnectionPoolType() {
		return BizDataSourceTypeEnum.DAMENG.getTypeName();
	}

}
