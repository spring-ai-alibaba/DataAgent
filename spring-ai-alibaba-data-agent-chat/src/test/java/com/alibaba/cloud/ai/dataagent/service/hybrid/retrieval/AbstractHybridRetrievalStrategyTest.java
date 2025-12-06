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
package com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval;

import com.alibaba.cloud.ai.dataagent.common.request.HybridSearchRequest;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.testcontainers.shaded.com.google.common.util.concurrent.MoreExecutors;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AbstractHybridRetrievalStrategy 单元测试 测试混合检索策略的模板方法实现
 */
@ExtendWith(MockitoExtension.class)
class AbstractHybridRetrievalStrategyTest {

	@Mock
	private VectorStore vectorStore;

	@Mock
	private FusionStrategy fusionStrategy;

	private ExecutorService directExecutor;

	private MyHybridRetrievalStrategy retrievalStrategy;

	// 这是一个具体的子类，用于测试抽象类中的逻辑
	static class MyHybridRetrievalStrategy extends AbstractHybridRetrievalStrategy {

		public MyHybridRetrievalStrategy(ExecutorService executorService, VectorStore vectorStore,
				FusionStrategy fusionStrategy) {
			super(executorService, vectorStore, fusionStrategy);
		}

		@Override
		public List<Document> getDocumentsByKeywords(HybridSearchRequest request) {
			// 在测试中，这个方法的行为会被 Mockito 控制
			return Collections.emptyList();
		}

	}

	@BeforeEach
	void setUp() {
		directExecutor = MoreExecutors.newDirectExecutorService();

		// 将 mock 对象和同步的 executor 注入到被测试的类中
		retrievalStrategy = org.mockito.Mockito
			.spy(new MyHybridRetrievalStrategy(directExecutor, vectorStore, fusionStrategy));
	}

	@Test
	void retrieve_ShouldFuseVectorAndKeywordResults_WhenQueryIsPresent() {
		// 1. 准备 (Arrange)
		HybridSearchRequest request = HybridSearchRequest.builder()
			.query("test query")
			.topK(10)
			.similarityThreshold(0.7)
			.vectorWeight(0.6)
			.keywordWeight(0.4)
			.build();

		// 先单独创建 Document 对象
		Document vecDoc1 = new Document("vec_doc1", Map.of("source", "vector"));
		Document vecDoc2 = new Document("vec_doc2", Map.of("source", "vector"));
		Document keyDoc1 = new Document("key_doc1", Map.of("source", "keyword"));
		Document fusedDoc1 = new Document("vec_doc1", Map.of("source", "vector"));

		// 然后创建列表
		List<Document> vectorResults = List.of(vecDoc1, vecDoc2);
		List<Document> keywordResults = List.of(keyDoc1);
		List<Document> fusedResults = List.of(fusedDoc1);

		// 定义 mock 对象的行为
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(vectorResults);
		// 使用 spy 来 mock getDocumentsByKeywords 方法
		org.mockito.Mockito.doReturn(keywordResults).when(retrievalStrategy).getDocumentsByKeywords(request);
		when(fusionStrategy.fuseResults(10, vectorResults, keywordResults)).thenReturn(fusedResults);

		// 2. 执行 (Act)
		// 因为我们用了 directExecutor，这里的调用会同步执行所有逻辑
		List<Document> finalDocuments = retrievalStrategy.retrieve(request);

		// 3. 断言 (Assert)
		assertEquals(fusedResults.size(), finalDocuments.size());
		assertEquals(fusedResults.get(0).getText(), finalDocuments.get(0).getText());
	}

	@Test
	void retrieve_ShouldReturnOnlyVectorResults_WhenQueryIsEmpty() {
		// 1. 准备 (Arrange)
		HybridSearchRequest request = HybridSearchRequest.builder()
			.query("") // 空查询字符串
			.topK(10)
			.similarityThreshold(0.5)
			.vectorWeight(0.7)
			.keywordWeight(0.3)
			.build();

		Document doc1 = new Document("vec_doc1", Map.of("source", "vector"));
		Document doc2 = new Document("vec_doc2", Map.of("source", "vector"));
		List<Document> vectorResults = List.of(doc1, doc2);
		List<Document> keywordResults = Collections.emptyList(); // 空查询应该返回空结果
		List<Document> fusedResults = List.of(doc1, doc2); // 融合后应该只有向量结果

		// 定义 mock 对象的行为
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(vectorResults);
		// 使用 spy 来 mock getDocumentsByKeywords 方法
		org.mockito.Mockito.doReturn(keywordResults).when(retrievalStrategy).getDocumentsByKeywords(request);
		when(fusionStrategy.fuseResults(10, vectorResults, keywordResults)).thenReturn(fusedResults);

		// 2. 执行 (Act)
		List<Document> finalDocuments = retrievalStrategy.retrieve(request);

		// 3. 断言 (Assert)
		assertEquals(fusedResults.size(), finalDocuments.size());
		assertEquals(vectorResults, finalDocuments);
	}

}
