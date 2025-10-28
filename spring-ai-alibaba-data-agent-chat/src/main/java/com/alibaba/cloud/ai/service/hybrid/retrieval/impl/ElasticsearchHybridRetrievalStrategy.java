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

package com.alibaba.cloud.ai.service.hybrid.retrieval.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.alibaba.cloud.ai.request.AgentSearchRequest;
import com.alibaba.cloud.ai.service.hybrid.fusion.FusionStrategy;
import com.alibaba.cloud.ai.service.hybrid.retrieval.AbstractHybridRetrievalStrategy;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Setter
@Slf4j
public class ElasticsearchHybridRetrievalStrategy extends AbstractHybridRetrievalStrategy {

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
	public List<Document> getDocumentsByKeywords(AgentSearchRequest agentSearchRequest) {

		/*
		 * JSON 请求
		 *
		 * POST custom-index/_search { "query": { "bool": { "must": [ { "match": {
		 * "content": "test" } } ], "filter": [ { "term": { "metadata.agentId": "2" } }, {
		 * "term": { "metadata.vectorType": "table" } } ] } }, "size": 20, "_source": true
		 * }
		 */
		if (CollectionUtils.isEmpty(agentSearchRequest.getKeywords()))
			return Collections.emptyList();

		ElasticsearchVectorStore vectorStore = (ElasticsearchVectorStore) this.vectorStore;
		Optional<ElasticsearchClient> nativeClient = vectorStore.getNativeClient();
		if (nativeClient.isEmpty())
			throw new RuntimeException("ElasticsearchClient is not available.");
		ElasticsearchClient client = nativeClient.get();

		SearchRequest searchRequest = buildSearchRequest(agentSearchRequest);
		// 执行搜索
		SearchResponse<Document> search = null;
		try {
			search = client.search(searchRequest, Document.class);
		}
		catch (IOException e) {
			log.error("ElasticsearchClient search error", e);
		}
		if (null == search)
			return Collections.emptyList();
		return search.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
	}

	private SearchRequest buildSearchRequest(AgentSearchRequest agentSearchRequest) {
		// 拼接keywords 通过空格连接
		String keywords = String.join(" ", agentSearchRequest.getKeywords());
		log.debug("ElasticsearchClient search keywords: {}", keywords);

		Query matchQuery = MatchQuery.of(m -> m.field("content").query(keywords))._toQuery();

		// 创建元数据过滤条件
		Query agentIdFilter = TermQuery.of(t -> t.field("metadata.agentId").value(agentSearchRequest.getAgentId()))
			._toQuery();

		Query vectorTypeFilter = TermQuery
			.of(t -> t.field("metadata.vectorType").value(agentSearchRequest.getDocVectorType()))
			._toQuery();

		// 创建布尔查询，组合匹配查询和过滤条件
		Query boolQuery = Query
			.of(q -> q.bool(BoolQuery.of(b -> b.must(matchQuery).filter(agentIdFilter, vectorTypeFilter))));

		// 创建搜索请求
		return SearchRequest.of(s -> s.index(indexName)
			.query(boolQuery)
			.size(agentSearchRequest.getTopK() * 2)
			.minScore(minScore)
			.source(src -> src.fetch(true)));

	}

}
