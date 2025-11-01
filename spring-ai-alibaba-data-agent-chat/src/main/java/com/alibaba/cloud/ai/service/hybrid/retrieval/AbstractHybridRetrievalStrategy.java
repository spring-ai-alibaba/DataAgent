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

package com.alibaba.cloud.ai.service.hybrid.retrieval;

import com.alibaba.cloud.ai.constant.Constant;
import com.alibaba.cloud.ai.request.AgentSearchRequest;
import com.alibaba.cloud.ai.service.hybrid.fusion.FusionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@Slf4j
public abstract class AbstractHybridRetrievalStrategy implements HybridRetrievalStrategy {

	protected final ExecutorService executorService;

	protected final VectorStore vectorStore;

	protected final FusionStrategy fusionStrategy;

	protected AbstractHybridRetrievalStrategy(ExecutorService executorService, VectorStore vectorStore,
			FusionStrategy fusionStrategy) {
		this.executorService = executorService;
		this.vectorStore = vectorStore;
		this.fusionStrategy = fusionStrategy;
		log.info(
				"Initialized AbstractHybridRetrievalStrategy with executorService: {}, vectorStore: {}, fusionStrategy: {}",
				executorService, vectorStore, fusionStrategy);
	}

	// 模板方法，先进行向量搜索后进行关键词搜索，最后结果融合。
	// 如果你的向量库天然支持混合检索，如Milvus,Es..你可以在子类直接重写该方法不用它这里的流程走
	// 目前ES实现仍然按照模板流程走，因为ES的付费企业版才能使用它服务端的rrf融合策略
	@Override
	public List<Document> retrieve(AgentSearchRequest request) {
		if (!StringUtils.hasText(request.getAgentId()))
			throw new IllegalArgumentException("agentId cannot be null");

		if (!StringUtils.hasText(request.getDocVectorType()))
			throw new IllegalArgumentException("docVectorType cannot be null");

		SearchRequest vectorSearchRequest = buildVectorSearchRequest(request);

		// 异步执行向量搜索
		CompletableFuture<List<Document>> vectorSearchFuture = CompletableFuture.supplyAsync(() -> {
			List<Document> vectorResults = vectorStore.similaritySearch(vectorSearchRequest);
			log.info("Vector Search completed. Found {} documents for SearchRequest: {}", vectorResults.size(),
					vectorSearchRequest);
			return vectorResults;
		}, executorService);

		// 异步执行关键词搜索
		CompletableFuture<List<Document>> keywordSearchFuture = null;
		if (!CollectionUtils.isEmpty(request.getKeywords())) {
			keywordSearchFuture = CompletableFuture.supplyAsync(() -> {
				List<Document> results = getDocumentsByKeywords(request);
				log.info("Keyword Search completed. Found {} documents, using keywords: {}", results.size(),
						request.getKeywords());
				return results;
			}, executorService);
		}

		try {
			List<Document> vectorResults = vectorSearchFuture.get();
			if (keywordSearchFuture == null) {
				return vectorResults;
			}

			// 等待关键词搜索完成
			List<Document> keywordResults = keywordSearchFuture.get();

			// 融合结果
			List<Document> finalDocuments = fusionStrategy.fuseResults(vectorResults, keywordResults, request.getTopK(),
					request.getSimilarityThreshold());
			log.info("Fusion completed. Found {} documents", finalDocuments.size());
			return finalDocuments;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Search operation interrupted", e);
		}
		catch (ExecutionException e) {
			throw new RuntimeException("Error during parallel search execution", e);
		}

	}

	private SearchRequest buildVectorSearchRequest(AgentSearchRequest request) {
		SearchRequest.Builder builder = SearchRequest.builder();
		if (StringUtils.hasText(request.getQuery()))
			builder.query(request.getQuery());

		if (Objects.nonNull(request.getTopK()))
			builder.topK(request.getTopK() * 2);
		else
			builder.topK(30);

		FilterExpressionBuilder b = new FilterExpressionBuilder();
		Filter.Expression expression = b
			.and(b.eq(Constant.VECTOR_TYPE, request.getDocVectorType()), b.eq(Constant.AGENT_ID, request.getAgentId()))
			.build();
		builder.filterExpression(expression);

		builder.similarityThreshold(request.getSimilarityThreshold());
		return builder.build();
	}

	public abstract List<Document> getDocumentsByKeywords(AgentSearchRequest agentSearchRequest);

}
