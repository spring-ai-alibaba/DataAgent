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

package com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.impl;

import com.alibaba.cloud.ai.dataagent.common.request.HybridSearchRequest;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.AbstractHybridRetrievalStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 适合测试以及没有继承实现AbstractHybridRetrievalStrategy的向量库（如Pg,milvus）使用，无关键词搜索能力
 */
@Slf4j
public class DefaultHybridRetrievalStrategy extends AbstractHybridRetrievalStrategy {

	public DefaultHybridRetrievalStrategy(ExecutorService executorService, VectorStore vectorStore,
			FusionStrategy fusionStrategy) {
		super(executorService, vectorStore, fusionStrategy);
	}

	@Override
	public List<Document> getDocumentsByKeywords(HybridSearchRequest agentSearchRequest) {
		// keyword默认不操作
		return Collections.emptyList();
	}

}
