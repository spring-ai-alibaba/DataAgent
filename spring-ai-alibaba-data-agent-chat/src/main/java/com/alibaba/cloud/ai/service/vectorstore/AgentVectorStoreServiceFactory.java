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

import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.service.vectorstore.impls.AnalyticAgentVectorStoreService;
import com.alibaba.cloud.ai.service.vectorstore.impls.SimpleAgentVectorStoreService;
import com.alibaba.cloud.ai.vectorstore.analyticdb.AnalyticDbVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AgentVectorStoreServiceFactory implements FactoryBean<AgentVectorStoreService> {

	// 使用枚举配置向量存储类型，由用户配置决定实现类
	@Value("${spring.ai.vectorstore.type:SIMPLE}")
	private VectorStoreType vectorStoreType;

	@Autowired
	private EmbeddingModel embeddingModel;

	@Autowired
	private AccessorFactory accessorFactory;

	// 通用的向量存储bean，由Spring AI自动配置
	@Autowired(required = false)
	private VectorStore vectorStore;

	@FunctionalInterface
	private interface AgentVectorStoreServiceCreator {

		AgentVectorStoreService create();

	}

	/**
	 * 存储不同向量存储类型对应的创建策略 使用Map可以方便地扩展新的向量存储类型
	 */
	private final Map<VectorStoreType, AgentVectorStoreServiceCreator> serviceCreators = new HashMap<>();

	public AgentVectorStoreServiceFactory() {
		// 初始化各种向量存储类型的创建策略
		// 这里使用显式的匿名类实现而不是方法引用，以提高代码可读性
		serviceCreators.put(VectorStoreType.ANALYTIC_DB, this::createAnalyticAgentVectorStoreService);

		serviceCreators.put(VectorStoreType.SIMPLE, this::createSimpleAgentVectorStoreService);

		// TODO 后续其他向量存储类型扩展处

	}

	@Override
	public AgentVectorStoreService getObject() {
		// 根据配置的向量存储类型获取对应的创建策略
		AgentVectorStoreServiceCreator creator = serviceCreators.get(vectorStoreType);
		if (creator == null) {
			log.warn("Unsupported vector store type: {}, falling back to SIMPLE", vectorStoreType);
			creator = serviceCreators.get(VectorStoreType.SIMPLE);
		}

		// 使用选定的策略创建AgentVectorStoreService实例
		return creator.create();
	}

	@Override
	public Class<?> getObjectType() {
		return AgentVectorStoreService.class;
	}

	private AgentVectorStoreService createAnalyticAgentVectorStoreService() {
		if (vectorStore == null) {
			throw new IllegalStateException(
					"AnalyticDbVectorStore is not configured. Please check your configuration.");
		}
		log.info("Using AnalyticDbVectorStoreService");
		if (!(vectorStore instanceof AnalyticDbVectorStore)) {
			throw new IllegalStateException("VectorStore is not an instance of AnalyticDbVectorStore");
		}
		return new AnalyticAgentVectorStoreService(embeddingModel, (AnalyticDbVectorStore) vectorStore,
				accessorFactory);
	}

	/**
	 * 创建简单内存存储的AgentVectorStoreService
	 * @return SimpleAgentVectorStoreService实例
	 */
	private AgentVectorStoreService createSimpleAgentVectorStoreService() {
		log.info("Using SimpleVectorStoreService");
		return new SimpleAgentVectorStoreService(embeddingModel, accessorFactory);
	}

}
