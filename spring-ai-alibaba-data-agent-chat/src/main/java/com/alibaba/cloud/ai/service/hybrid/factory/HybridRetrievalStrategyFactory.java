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

package com.alibaba.cloud.ai.service.hybrid.factory;

import com.alibaba.cloud.ai.constant.Constant;
import com.alibaba.cloud.ai.service.hybrid.fusion.FusionStrategy;
import com.alibaba.cloud.ai.service.hybrid.retrieval.HybridRetrievalStrategy;
import com.alibaba.cloud.ai.service.hybrid.retrieval.impl.DefaultHybridRetrievalStrategy;
import com.alibaba.cloud.ai.service.hybrid.retrieval.impl.ElasticsearchHybridRetrievalStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

/**
 * 混合检索策略工厂类 根据配置条件创建并注册相应的 HybridRetrievalStrategy 实现类
 */
@Slf4j
@Configuration
public class HybridRetrievalStrategyFactory {

	@Value("${spring.ai.vectorstore.type:simple}")
	private String vectorStoreType;

	@Value("${spring.ai.vectorstore.elasticsearch.index-name:spring-ai-document-index}")
	private String elasticsearchIndexName;

	@Value("${" + Constant.PROJECT_PROPERTIES_PREFIX + ".elasticsearch-min-score:0.5}")
	private Double minScore;

	/**
	 * 创建并注册 HybridRetrievalStrategy Bean
	 * @param executorService 线程池执行器
	 * @param vectorStore 向量存储
	 * @param fusionStrategy 融合策略
	 * @return HybridRetrievalStrategy 实例
	 */
	@Bean
	@ConditionalOnMissingBean(HybridRetrievalStrategy.class)
	public HybridRetrievalStrategy hybridRetrievalStrategy(ExecutorService executorService, VectorStore vectorStore,
			FusionStrategy fusionStrategy) {
		log.info("Creating HybridRetrievalStrategy with vectorStore type: {}", vectorStoreType);

		if ("elasticsearch".equalsIgnoreCase(vectorStoreType)) {
			log.info("Creating ElasticsearchHybridRetrievalStrategy with index: {}", elasticsearchIndexName);
			ElasticsearchHybridRetrievalStrategy strategy = new ElasticsearchHybridRetrievalStrategy(executorService,
					vectorStore, fusionStrategy);
			// 设置索引名称
			strategy.setIndexName(elasticsearchIndexName);
			strategy.setMinScore(minScore);
			return strategy;
		}
		else {
			log.warn("Creating DefaultHybridRetrievalStrategy (default) without keyword-search ability");
			return new DefaultHybridRetrievalStrategy(executorService, vectorStore, fusionStrategy);
		}
	}

}
