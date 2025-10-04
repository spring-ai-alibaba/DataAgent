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

package com.alibaba.cloud.ai.service.vectorstore;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.service.vectorstore.impls.AnalyticVectorStoreService;
import com.alibaba.cloud.ai.service.vectorstore.impls.SimpleVectorStoreService;
import com.alibaba.cloud.ai.vectorstore.analyticdb.AnalyticDbVectorStore;

@Slf4j
@Component
public class VectorStoreServiceFactory implements FactoryBean<AgentVectorStoreService> {

	// todo: 改为枚举，由用户配置决定实现类
	@Value("${spring.ai.vectorstore.analytic.enabled:false}")
	private Boolean analyticEnabled;

	@Autowired
	private EmbeddingModel embeddingModel;

	@Autowired
	private AccessorFactory accessorFactory;

	@Autowired(required = false)
	private AnalyticDbVectorStore analyticDbVectorStore;

	@Override
	public AgentVectorStoreService getObject() {
		if (Boolean.TRUE.equals(analyticEnabled)) {
			log.info("Using AnalyticDbVectorStoreService");
			return new AnalyticVectorStoreService(embeddingModel, analyticDbVectorStore, accessorFactory);
		}
		else {
			log.info("Using SimpleVectorStoreService");
			return new SimpleVectorStoreService(embeddingModel, accessorFactory);
		}
	}

	@Override
	public Class<?> getObjectType() {
		return AgentVectorStoreService.class;
	}

}
