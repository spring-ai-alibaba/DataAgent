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

package com.alibaba.cloud.ai.service.schema;

import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.service.schema.impls.AnalyticSchemaService;
import com.alibaba.cloud.ai.service.schema.impls.SimpleSchemaService;
import com.alibaba.cloud.ai.service.vectorstore.VectorStoreService;
import com.alibaba.cloud.ai.util.JsonUtil;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SchemaServiceFactory implements FactoryBean<SchemaService> {

	// todo: 改为枚举，由用户配置决定实现类
	@Value("${spring.ai.vectorstore.analytic.enabled:false}")
	private Boolean analyticEnabled;

	@Autowired
	private DbConfig dbConfig;

	@Autowired
	private VectorStoreService vectorStoreService;

	@Override
	public SchemaService getObject() {
		if (Boolean.TRUE.equals(analyticEnabled)) {
			return new AnalyticSchemaService(dbConfig, JsonUtil.getObjectMapper(), vectorStoreService);
		}
		else {
			return new SimpleSchemaService(dbConfig, JsonUtil.getObjectMapper(), vectorStoreService);
		}
	}

	@Override
	public Class<?> getObjectType() {
		return SchemaService.class;
	}

}
