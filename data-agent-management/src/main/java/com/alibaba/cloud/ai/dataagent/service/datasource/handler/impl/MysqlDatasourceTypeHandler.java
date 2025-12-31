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

package com.alibaba.cloud.ai.dataagent.service.datasource.handler.impl;

import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MysqlDatasourceTypeHandler implements DatasourceTypeHandler {

	@Override
	public String typeName() {
		return BizDataSourceTypeEnum.MYSQL.getTypeName();
	}

	@Override
	public String buildConnectionUrl(Datasource datasource) {
		if (!hasRequiredConnectionFields(datasource)) {
			return datasource.getConnectionUrl();
		}
		return String.format(
				"jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&transformedBitIsBoolean=true&allowMultiQueries=true&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai",
				datasource.getHost(), datasource.getPort(), datasource.getDatabaseName());
	}

	@Override
	public String normalizeTestUrl(Datasource datasource, String url) {
		String updated = url;
		String lowerUrl = updated.toLowerCase(Locale.ROOT);
		if (!lowerUrl.contains("servertimezone=")) {
			updated = appendParam(updated, "serverTimezone", "Asia/Shanghai");
			lowerUrl = updated.toLowerCase(Locale.ROOT);
		}
		if (!lowerUrl.contains("usessl=")) {
			updated = appendParam(updated, "useSSL", "false");
		}
		return updated;
	}

	private String appendParam(String url, String key, String value) {
		return url + (url.contains("?") ? "&" : "?") + key + "=" + value;
	}

}
