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
package com.alibaba.cloud.ai.dataagent.service.integration;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeResourceManager;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreServiceImpl;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.MetadataAwareSimpleVectorStore;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService;
import com.alibaba.cloud.ai.dataagent.service.vector.MetadataDocumentRetriever;
import com.alibaba.cloud.ai.dataagent.support.KeywordEmbeddingModel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorReplacementIntegrationTest {

	private FailAfterEmbeddingModel embeddingModel;

	private SimpleVectorStore vectorStore;

	private AgentVectorStoreService service;

	private Map<String, Object> identityMetadata;

	@BeforeEach
	void setUp() {
		embeddingModel = new FailAfterEmbeddingModel();
		vectorStore = new MetadataAwareSimpleVectorStore(embeddingModel);
		DataAgentProperties properties = new DataAgentProperties();
		service = new AgentVectorStoreServiceImpl(vectorStore, Optional.empty(), properties,
				new DynamicFilterService(null, null), new MetadataDocumentRetriever(new StandardEnvironment()));
		identityMetadata = Map.of(Constant.AGENT_ID, "1", DocumentMetadataConstant.VECTOR_TYPE,
				DocumentMetadataConstant.BUSINESS_TERM, DocumentMetadataConstant.DB_BUSINESS_TERM_ID, 10L);
		service.addDocuments("1", List.of(new Document("stable-old-id", "订单旧定义", identityMetadata)));
	}

	@Test
	void failedReplacementKeepsTheOldVectorAvailable() {
		embeddingModel.failAfterSuccessfulCalls(1);

		assertThatThrownBy(
				() -> service
					.replaceDocumentsByMetadata(identityMetadata,
							List.of(new Document("stable-old-id", "用户新定义", identityMetadata),
									new Document("用户补充定义", identityMetadata))))
			.isInstanceOf(IllegalStateException.class);

		embeddingModel.disableFailure();
		assertThat(search("订单查询")).extracting(Document::getText).containsExactly("订单旧定义");
		assertThat(search("用户查询")).isEmpty();
	}

	@Test
	void successfulReplacementPublishesNewVectorBeforeRemovingOldVector() {
		service.replaceDocumentsByMetadata(identityMetadata, List.of(new Document("用户新定义", identityMetadata)));

		assertThat(search("用户查询")).extracting(Document::getText).containsExactly("用户新定义");
		assertThat(search("订单查询")).isEmpty();
	}

	@Test
	void qaKnowledgeReembeddingUsesTheRealAtomicReplacementPath() throws Exception {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(11);
		knowledge.setAgentId(1);
		knowledge.setType(KnowledgeType.QA);
		knowledge.setQuestion("用户旧问题");
		AgentKnowledgeResourceManager resourceManager = new AgentKnowledgeResourceManager(null, null, service);

		resourceManager.doEmbedingToVectorStore(knowledge);
		knowledge.setQuestion("订单新问题");
		resourceManager.doEmbedingToVectorStore(knowledge);

		Filter.Expression filter = new FilterExpressionBuilder().eq(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, 11)
			.build();
		assertThat(service.getDocumentsOnlyByFilter(filter, 5)).extracting(Document::getText).containsExactly("订单新问题");
	}

	private List<Document> search(String query) {
		Filter.Expression filter = new FilterExpressionBuilder().eq(Constant.AGENT_ID, "1").build();
		return vectorStore.similaritySearch(
				SearchRequest.builder().query(query).topK(5).similarityThreshold(0.8).filterExpression(filter).build());
	}

	private static final class FailAfterEmbeddingModel implements EmbeddingModel {

		private final KeywordEmbeddingModel delegate = new KeywordEmbeddingModel();

		private int successfulCallsBeforeFailure = Integer.MAX_VALUE;

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			beforeEmbedding();
			return delegate.call(request);
		}

		@Override
		public float[] embed(Document document) {
			beforeEmbedding();
			return delegate.embed(document);
		}

		@Override
		public float[] embed(String text) {
			beforeEmbedding();
			return delegate.embed(text);
		}

		@Override
		public int dimensions() {
			return delegate.dimensions();
		}

		void failAfterSuccessfulCalls(int successfulCalls) {
			this.successfulCallsBeforeFailure = successfulCalls;
		}

		void disableFailure() {
			this.successfulCallsBeforeFailure = Integer.MAX_VALUE;
		}

		private void beforeEmbedding() {
			if (successfulCallsBeforeFailure == 0) {
				throw new IllegalStateException("simulated embedding outage");
			}
			successfulCallsBeforeFailure--;
		}

	}

}
