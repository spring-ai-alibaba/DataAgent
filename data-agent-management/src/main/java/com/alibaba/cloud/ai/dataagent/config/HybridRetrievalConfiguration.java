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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.impl.RrfFusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.HybridRetrievalStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.impl.ElasticsearchHybridRetrievalStrategy;
import java.util.concurrent.ExecutorService;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.alibaba.data-agent.vector-store.enable-hybrid-search", havingValue = "true")
public class HybridRetrievalConfiguration {

	@Bean
	@ConditionalOnMissingBean(FusionStrategy.class)
	FusionStrategy rrfFusionStrategy() {
		return new RrfFusionStrategy();
	}

	@Bean
	@ConditionalOnMissingBean(HybridRetrievalStrategy.class)
	@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "elasticsearch")
	HybridRetrievalStrategy elasticsearchHybridRetrievalStrategy(
			@Qualifier("dbOperationExecutor") ExecutorService executorService, VectorStore vectorStore,
			FusionStrategy fusionStrategy, DataAgentProperties properties,
			@Value("${spring.ai.vectorstore.elasticsearch.index-name:spring-ai-document-index}") String indexName) {
		ElasticsearchHybridRetrievalStrategy strategy = new ElasticsearchHybridRetrievalStrategy(executorService,
				vectorStore, fusionStrategy, properties.getVectorStore().getHybridSearchTimeoutMs());
		strategy.setIndexName(indexName);
		strategy.setMinScore(properties.getVectorStore().getElasticsearchMinScore());
		return strategy;
	}

}
