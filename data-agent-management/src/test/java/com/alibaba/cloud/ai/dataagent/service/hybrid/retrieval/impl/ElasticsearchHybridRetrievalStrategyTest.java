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
package com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.alibaba.cloud.ai.dataagent.dto.search.HybridSearchRequest;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElasticsearchHybridRetrievalStrategyTest {

	@Mock
	private ElasticsearchVectorStore vectorStore;

	@Mock
	private FusionStrategy fusionStrategy;

	@Mock
	private ElasticsearchClient esClient;

	private ElasticsearchHybridRetrievalStrategy strategy;

	private ExecutorService executorService;

	@BeforeEach
	void setUp() {
		executorService = Executors.newSingleThreadExecutor();
		strategy = new ElasticsearchHybridRetrievalStrategy(executorService, vectorStore, fusionStrategy);
	}

	@Test
	void getDocumentsByKeywords_emptyQuery_returnsEmpty() {
		HybridSearchRequest request = HybridSearchRequest.builder().query("").topK(10).build();

		List<Document> result = strategy.getDocumentsByKeywords(request);

		assertTrue(result.isEmpty());
	}

	@Test
	void getDocumentsByKeywords_nullQuery_returnsEmpty() {
		HybridSearchRequest request = HybridSearchRequest.builder().query(null).topK(10).build();

		List<Document> result = strategy.getDocumentsByKeywords(request);

		assertTrue(result.isEmpty());
	}

	@Test
	void getDocumentsByKeywords_noNativeClient_throwsException() {
		when(vectorStore.getNativeClient()).thenReturn(Optional.empty());

		HybridSearchRequest request = HybridSearchRequest.builder().query("test query").topK(10).build();

		assertThrowsExactly(RuntimeException.class, () -> strategy.getDocumentsByKeywords(request));
	}

	@SuppressWarnings("unchecked")
	@Test
	void getDocumentsByKeywords_ioException_returnsEmpty() throws IOException {
		when(vectorStore.getNativeClient()).thenReturn(Optional.of(esClient));
		when(esClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Document.class)))
			.thenThrow(new IOException("connection error"));

		HybridSearchRequest request = HybridSearchRequest.builder().query("test query").topK(10).build();

		List<Document> result = strategy.getDocumentsByKeywords(request);

		assertTrue(result.isEmpty());
	}

	@SuppressWarnings("unchecked")
	@Test
	void getDocumentsByKeywords_nullResponse_returnsEmpty() throws IOException {
		when(vectorStore.getNativeClient()).thenReturn(Optional.of(esClient));
		when(esClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Document.class)))
			.thenReturn(null);

		HybridSearchRequest request = HybridSearchRequest.builder().query("test query").topK(10).build();

		List<Document> result = strategy.getDocumentsByKeywords(request);

		assertTrue(result.isEmpty());
	}

	@Test
	void setMinScore_setsValue() throws IOException {
		when(vectorStore.getNativeClient()).thenReturn(Optional.of(esClient));
		when(esClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Document.class)))
			.thenReturn(null);
		strategy.setMinScore(0.5);

		strategy.getDocumentsByKeywords(HybridSearchRequest.builder().query("revenue").topK(10).build());

		ArgumentCaptor<co.elastic.clients.elasticsearch.core.SearchRequest> requestCaptor = ArgumentCaptor
			.forClass(co.elastic.clients.elasticsearch.core.SearchRequest.class);
		verify(esClient).search(requestCaptor.capture(), eq(Document.class));
		assertEquals(0.5, requestCaptor.getValue().minScore());
		assertEquals(20, requestCaptor.getValue().size());
	}

	@Test
	void setIndexName_setsValue() throws IOException {
		when(vectorStore.getNativeClient()).thenReturn(Optional.of(esClient));
		when(esClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Document.class)))
			.thenReturn(null);
		strategy.setIndexName("custom-index");

		strategy.getDocumentsByKeywords(HybridSearchRequest.builder().query("revenue").topK(10).build());

		ArgumentCaptor<co.elastic.clients.elasticsearch.core.SearchRequest> requestCaptor = ArgumentCaptor
			.forClass(co.elastic.clients.elasticsearch.core.SearchRequest.class);
		verify(esClient).search(requestCaptor.capture(), eq(Document.class));
		assertEquals(List.of("custom-index"), requestCaptor.getValue().index());
	}

	@Test
	void getDocumentsByKeywords_withFilterExpression_usesFilter() throws IOException {
		when(vectorStore.getNativeClient()).thenReturn(Optional.of(esClient));
		when(esClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Document.class)))
			.thenReturn(null);

		FilterExpressionBuilder b = new FilterExpressionBuilder();
		Filter.Expression filter = b.eq("key", "value").build();

		HybridSearchRequest request = HybridSearchRequest.builder()
			.query("test query")
			.topK(5)
			.filterExpression(filter)
			.build();

		List<Document> result = strategy.getDocumentsByKeywords(request);

		assertTrue(result.isEmpty());
		ArgumentCaptor<co.elastic.clients.elasticsearch.core.SearchRequest> requestCaptor = ArgumentCaptor
			.forClass(co.elastic.clients.elasticsearch.core.SearchRequest.class);
		verify(esClient).search(requestCaptor.capture(), eq(Document.class));
		String filterQuery = requestCaptor.getValue().query().bool().filter().get(0).queryString().query();
		assertTrue(filterQuery.contains("key"));
		assertTrue(filterQuery.contains("value"));
	}

}
