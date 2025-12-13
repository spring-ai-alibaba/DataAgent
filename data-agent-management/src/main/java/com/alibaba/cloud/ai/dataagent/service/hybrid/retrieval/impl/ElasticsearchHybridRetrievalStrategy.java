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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.alibaba.cloud.ai.dataagent.dto.search.HybridSearchRequest;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.AbstractHybridRetrievalStrategy;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchAiSearchFilterExpressionConverter;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Setter
@Slf4j
public class ElasticsearchHybridRetrievalStrategy extends AbstractHybridRetrievalStrategy {

	// content
	private static final String CONTENT = "content";

	private final FilterExpressionConverter filterConverter = new ElasticsearchAiSearchFilterExpressionConverter();

	/**
	 * 设置Elasticsearch最小分数
	 * @param minScore 最小分数
	 */
	private Double minScore;

	/**
	 * -- SETTER -- 设置Elasticsearch索引名称
	 * @param indexName 索引名称
	 */
	private String indexName;

	public ElasticsearchHybridRetrievalStrategy(ExecutorService executorService, VectorStore vectorStore,
			FusionStrategy fusionStrategy) {
		super(executorService, vectorStore, fusionStrategy);
		this.indexName = "spring-ai-document-index"; // 默认索引名称
	}

	@Override
	public List<Document> getDocumentsByKeywords(HybridSearchRequest request) {
		if (!StringUtils.hasText(request.getQuery())) {
			return Collections.emptyList();
		}

		ElasticsearchVectorStore vectorStore = (ElasticsearchVectorStore) this.vectorStore;
		Optional<ElasticsearchClient> nativeClient = vectorStore.getNativeClient();
		if (nativeClient.isEmpty())
			throw new RuntimeException("ElasticsearchClient is not available.");
		ElasticsearchClient client = nativeClient.get();

		String queryText = request.getQuery();
		int targetTopK = request.getTopK() * 2;
		String filterString = null;
		if (request.getFilterExpression() != null) {
			filterString = filterConverter.convertExpression(request.getFilterExpression());
			log.debug("Using filter: {}", filterString);
		}

		// 执行搜索
		try {
			SearchRequest searchRequest = buildSearchRequest(queryText, targetTopK, minScore, filterString);

			// 执行搜索
			SearchResponse<Document> response = client.search(searchRequest, Document.class);

			if (response == null || response.hits() == null) {
				return Collections.emptyList();
			}

			// 结果转换
			return response.hits()
				.hits()
				.stream()
				.map(Hit::source)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		}
		catch (IOException e) {
			log.error("ElasticsearchClient search error", e);
			// 关键词搜索失败不应该阻断整个流程，返回空列表让向量搜索兜底
			return Collections.emptyList();
		}
	}

	private SearchRequest buildSearchRequest(String queryText, int topK, Double minScore, String filterString) {
		log.debug("Building ES request with query: [{}], filter: [{}]", queryText, filterString);

		// A. 构建内容匹配查询 (Match Query)
		Query matchQuery = Query.of(q -> q.match(m -> m.field(CONTENT).query(queryText)));

		// B. 构建布尔查询 (Bool Query)
		Query finalQuery = Query.of(q -> q.bool(b -> {
			// 1. must: 必须匹配内容
			b.must(matchQuery);

			// 2. filter: 如果有过滤条件，注入 QueryStringQuery
			if (StringUtils.hasText(filterString)) {
				b.filter(f -> f.queryString(qs -> qs.query(filterString) // <---
																			// 这里直接使用翻译好的字符串
				));
			}
			return b;
		}));

		// C. 组装最终请求
		return SearchRequest.of(s -> s.index(indexName)
			.query(finalQuery)
			.size(topK) // 注意：这里用了传入的 topK
			.minScore(minScore) // 如果 minScore 为 null，ES 会忽略此参数
			.source(src -> src.fetch(true)) // 确保返回 _source
		);
	}

}
