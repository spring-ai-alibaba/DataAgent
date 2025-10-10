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

import com.alibaba.cloud.ai.service.schema.impls.AnalyticSchemaService;
import com.alibaba.cloud.ai.service.schema.impls.SimpleSchemaService;
import com.alibaba.cloud.ai.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.service.vectorstore.VectorStoreType;
import com.alibaba.cloud.ai.service.vectorstore.impls.AnalyticAgentVectorStoreService;
import com.alibaba.cloud.ai.service.vectorstore.impls.SimpleAgentVectorStoreService;
import com.alibaba.cloud.ai.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@DependsOn("agentVectorStoreServiceFactory")
public class SchemaServiceFactory implements FactoryBean<SchemaService> {

	@Value("${spring.ai.vectorstore.type:SIMPLE}")
	private VectorStoreType vectorStoreType;

	@Autowired(required = false)
	private AgentVectorStoreService agentVectorStoreService;

	@FunctionalInterface
	private interface SchemaServiceCreator {

		SchemaService create();

	}

	private final Map<VectorStoreType, SchemaServiceCreator> serviceCreators = new HashMap<>();

	public SchemaServiceFactory() {
		// 初始化各种向量存储类型的创建策略
		serviceCreators.put(VectorStoreType.ANALYTIC_DB, this::createAnalyticSchemaService);
		serviceCreators.put(VectorStoreType.SIMPLE, this::createSimpleSchemaService);

	}

	@Override
	public SchemaService getObject() {
		if (agentVectorStoreService == null) {
			throw new IllegalStateException("AgentVectorStoreService is not initialized.");
		}

		// 根据配置的向量存储类型获取对应的创建策略
		SchemaServiceCreator creator = serviceCreators.get(vectorStoreType);
		if (creator == null) {
			log.warn("Unsupported vector store type: {}, falling back to SIMPLE", vectorStoreType);
			creator = serviceCreators.get(VectorStoreType.SIMPLE);
		}

		// 使用选定的策略创建SchemaService实例
		return creator.create();
	}

	@Override
	public Class<?> getObjectType() {
		return SchemaService.class;
	}

	/**
	 * 创建分析型数据库的SchemaService
	 * @return AnalyticSchemaService实例
	 */
	private SchemaService createAnalyticSchemaService() {
		log.info("Using AnalyticSchemaService");
		if (!(agentVectorStoreService instanceof AnalyticAgentVectorStoreService)) {
			throw new IllegalStateException(
					"AgentVectorStoreService is not an instance of AnalyticAgentVectorStoreService");
		}
		return new AnalyticSchemaService(JsonUtil.getObjectMapper(),
				(AnalyticAgentVectorStoreService) agentVectorStoreService);
	}

	/**
	 * 创建简单内存存储的SchemaService
	 * @return SimpleSchemaService实例
	 */
	private SchemaService createSimpleSchemaService() {
		log.info("Using SimpleSchemaService");
		if (!(agentVectorStoreService instanceof SimpleAgentVectorStoreService)) {
			throw new IllegalStateException(
					"AgentVectorStoreService is not an instance of SimpleAgentVectorStoreService");
		}
		return new SimpleSchemaService(JsonUtil.getObjectMapper(),
				(SimpleAgentVectorStoreService) agentVectorStoreService);
	}

}
