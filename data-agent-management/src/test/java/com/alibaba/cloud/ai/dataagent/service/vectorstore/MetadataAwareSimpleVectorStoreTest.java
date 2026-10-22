/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import com.alibaba.cloud.ai.dataagent.service.vector.MetadataDocumentRetriever;
import com.alibaba.cloud.ai.dataagent.support.KeywordEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.env.StandardEnvironment;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MetadataAwareSimpleVectorStoreTest {

	@Test
	void numericMembershipMatchesInBothSearchPathsAndKeepsAgentIsolation() {
		MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		store.add(List.of(knowledge("long", "1", 1L), knowledge("integer", "1", 2),
				knowledge("disabled", "1", 3L), knowledge("other-agent", "2", 1L),
				knowledge("string-id", "1", "1")));
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		var filter = b.and(b.eq("agentId", "1"),
				b.and(b.eq("vectorType", "businessTerm"), b.in("businessTermId", 1L, 2L))).build();

		assertBothPaths(store, filter, "long", "integer");
	}

	@Test
	void agentKnowledgeIdsAndKnowledgeTypesRemainScoped() {
		MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		store.add(List.of(new Document("faq", "order FAQ",
				Map.of("agentId", "1", "vectorType", "agentKnowledge", "agentKnowledgeId", 1)),
				new Document("other", "order FAQ",
						Map.of("agentId", "2", "vectorType", "agentKnowledge", "agentKnowledgeId", 1)),
				knowledge("business", "1", 1L)));
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		var filter = b.and(b.eq("agentId", "1"),
				b.and(b.eq("vectorType", "agentKnowledge"), b.in("agentKnowledgeId", 1L))).build();

		assertBothPaths(store, filter, "faq");
	}

	@Test
	void membershipDoesNotLoseLargeIntegerPrecisionOrTruncateFractions() {
		MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		store.add(List.of(knowledge("large", "1", 9007199254740993L),
				knowledge("adjacent", "1", 9007199254740992L), knowledge("decimal", "1", new BigDecimal("1.50")),
				knowledge("integer", "1", 1L)));
		var filter = new FilterExpressionBuilder().in("businessTermId", 9007199254740993L, 1.5d).build();

		assertBothPaths(store, filter, "large", "decimal");
	}

	@Test
	void largeMembershipListRemainsASetInsteadOfAnExpandedExpression() {
		MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		store.add(List.of(knowledge("first", "1", 1), knowledge("last", "1", 10000L),
				knowledge("outside", "1", 10001L)));
		Object[] ids = LongStream.rangeClosed(1, 10000).boxed().toArray();
		var filter = new FilterExpressionBuilder().in("businessTermId", ids).build();

		assertBothPaths(store, filter, "first", "last");
	}

	@Test
	void nestedGroupsNotAndNumericNinKeepTheirBooleanMeaning() {
		MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		store.add(List.of(knowledge("one", "1", 1L), knowledge("two", "1", 2L), knowledge("three", "1", 3L)));
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		var excluded = b.or(b.in("businessTermId", 1), b.eq("businessTermId", 3L)).build();
		var filter = new Filter.Expression(Filter.ExpressionType.NOT, new Filter.Group(excluded));
		assertBothPaths(store, filter, "two");
		assertBothPaths(store, b.nin("businessTermId", 1, 3).build(), "two");
		assertBothPaths(store, b.in("agentId", "1").build(), "one", "two", "three");
	}

	@Test
	void loadedDocumentsStillMatchNumericMembership(@TempDir Path directory) {
		MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		store.add(List.of(knowledge("saved", "1", 1L)));
		var file = directory.resolve("vectors.json").toFile();
		store.save(file);
		MetadataAwareSimpleVectorStore restored = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		restored.load(file);

		assertBothPaths(restored, new FilterExpressionBuilder().in("businessTermId", 1L).build(), "saved");
	}

	@Test
	void similaritySearchPreservesThresholdTopKAndUnfilteredQueries() {
		MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		store.add(List.of(knowledge("order", "1", 1L),
				new Document("user", "user profile", Map.of("agentId", "1", "businessTermId", 2L))));
		var filter = new FilterExpressionBuilder().in("businessTermId", 1, 2).build();
		var results = store.similaritySearch(SearchRequest.builder().query("order")
			.filterExpression(filter).similarityThreshold(0.4).topK(8).build());
		assertThat(results).extracting(Document::getId).containsExactly("order");
		assertThat(results.get(0).getScore()).isEqualTo(1.0);
		assertThat(store.similaritySearch(SearchRequest.builder().query("order")
			.similarityThreshold(0.0).topK(1).build())).extracting(Document::getId).containsExactly("order");
	}

	private static Document knowledge(String id, String agentId, Object knowledgeId) {
		return new Document(id, "已完成订单金额 order", Map.of("agentId", agentId, "vectorType", "businessTerm",
				"businessTermId", knowledgeId));
	}

	private static void assertBothPaths(MetadataAwareSimpleVectorStore store, Filter.Expression filter,
			String... expectedIds) {
		assertThat(store.findByFilter(filter, 20)).extracting(Document::getId).containsExactlyInAnyOrder(expectedIds);
		assertThat(store.similaritySearch(SearchRequest.builder().query("已完成的订单总金额是多少")
			.filterExpression(filter).similarityThreshold(0.4).topK(20).build()))
			.extracting(Document::getId).containsExactlyInAnyOrder(expectedIds);
	}

	@Test
	void deleteByMetadata_removesOnlyExactMetadataMatches() {
		MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		store.add(List.of(new Document("one", Map.of("agentId", "1")), new Document("two", Map.of("agentId", "2"))));

		assertEquals(1, store.deleteByMetadata(Map.of("agentId", 1)));
		assertEquals(0, store.deleteByMetadata(Map.of("agentId", "1")));
		assertEquals(1, store.deleteByMetadata(Map.of("agentId", "2")));
	}

	@Test
	void exactMetadataRetrievalDoesNotRequireAQueryEmbedding() {
		MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		store.add(List.of(new Document("first order", Map.of("agentId", "1")),
				new Document("second order", Map.of("agentId", "1")),
				new Document("other order", Map.of("agentId", "2"))));
		var filter = new FilterExpressionBuilder().eq("agentId", "1").build();
		MetadataDocumentRetriever retriever = new MetadataDocumentRetriever(new StandardEnvironment());

		assertThat(retriever.find(store, filter, 1)).hasSize(1)
			.allSatisfy(document -> assertThat(document.getMetadata()).containsEntry("agentId", "1"));
	}

	@Test
	void unsupportedStoresFailInsteadOfFallingBackToSyntheticSimilaritySearch() {
		SimpleVectorStore store = SimpleVectorStore.builder(new KeywordEmbeddingModel()).build();
		var filter = new FilterExpressionBuilder().eq("agentId", "1").build();
		MetadataDocumentRetriever retriever = new MetadataDocumentRetriever(new StandardEnvironment());

		assertThatThrownBy(() -> retriever.find(store, filter, 1)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Exact metadata retrieval is not supported");
	}

}
