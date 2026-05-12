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

import com.alibaba.cloud.ai.dataagent.dto.search.HybridSearchRequest;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultHybridRetrievalStrategyTest {

	private DefaultHybridRetrievalStrategy strategy;

	@Mock
	private VectorStore vectorStore;

	@Mock
	private FusionStrategy fusionStrategy;

	private ExecutorService executorService;

	@BeforeEach
	void setUp() {
		executorService = Executors.newFixedThreadPool(2);
		strategy = new DefaultHybridRetrievalStrategy(executorService, vectorStore, fusionStrategy);
	}

	@Test
	void getDocumentsByKeywords_returnsEmptyList() {
		HybridSearchRequest request = HybridSearchRequest.builder().query("test").topK(5).build();

		List<Document> result = strategy.getDocumentsByKeywords(request);

		assertTrue(result.isEmpty());
	}

	@Test
	void retrieve_returnsVectorDocsOnly() {
		Document vectorDoc = new Document("v1", "vector content", Collections.emptyMap());
		List<Document> vectorResults = List.of(vectorDoc);

		HybridSearchRequest request = HybridSearchRequest.builder().query("test query").topK(5).build();

		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(vectorResults);
		when(fusionStrategy.fuseResults(eq(5), any(), any())).thenReturn(vectorResults);

		List<Document> result = strategy.retrieve(request);

		assertEquals(1, result.size());
		assertEquals("v1", result.get(0).getId());
		verify(vectorStore).similaritySearch(any(SearchRequest.class));
		verify(fusionStrategy).fuseResults(eq(5), any(), any());
	}

}
