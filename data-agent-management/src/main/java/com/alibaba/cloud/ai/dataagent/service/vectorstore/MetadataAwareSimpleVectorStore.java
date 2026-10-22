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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Adds metadata operations and numeric membership filtering to the in-memory store.
 *
 * <p>
 * {@link SimpleVectorStore} supports ID deletion but not filter deletion. Selecting IDs
 * through similarity search would invoke the embedding model for a delete operation. This
 * adapter keeps Spring AI's storage and lifecycle implementation and shares one compiled
 * metadata predicate between exact retrieval and cosine similarity search.
 */
public final class MetadataAwareSimpleVectorStore extends SimpleVectorStore {

	public MetadataAwareSimpleVectorStore(EmbeddingModel embeddingModel) {
		super(SimpleVectorStore.builder(embeddingModel));
	}

	public int deleteByMetadata(Map<String, Object> metadata) {
		List<String> ids = this.store.entrySet()
			.stream()
			.filter(entry -> matches(entry.getValue().getMetadata(), metadata))
			.map(Map.Entry::getKey)
			.toList();
		doDelete(ids);
		return ids.size();
	}

	public List<Document> findByFilter(Filter.Expression filterExpression, int limit) {
		var predicate = SimpleVectorStoreMetadataFilter.compile(filterExpression);
		return store.values().stream().filter(content -> predicate.test(content.getMetadata()))
			.limit(limit)
			.map(content -> Document.builder()
				.id(content.getId())
				.text(content.getText())
				.metadata(content.getMetadata())
				.build())
			.toList();
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		var predicate = SimpleVectorStoreMetadataFilter.compile(request.getFilterExpression());
		float[] queryEmbedding = this.embeddingModel.embed(request.getQuery());
		// Match SimpleVectorStore scoring, threshold and topK semantics; only filtering differs.
		return store.values().stream()
			.filter(content -> predicate.test(content.getMetadata()))
			.map(content -> content.toDocument(EmbeddingMath.cosineSimilarity(queryEmbedding, content.getEmbedding())))
			.filter(document -> document.getScore() >= request.getSimilarityThreshold())
			.sorted(Comparator.comparing(Document::getScore).reversed())
			.limit(request.getTopK())
			.toList();
	}

	private boolean matches(Map<String, Object> documentMetadata, Map<String, Object> expectedMetadata) {
		return expectedMetadata.entrySet()
			.stream()
			.allMatch(entry -> valuesEqual(documentMetadata.get(entry.getKey()), entry.getValue()));
	}

	private boolean valuesEqual(Object actual, Object expected) {
		return actual != null && expected != null && actual.toString().equals(expected.toString());
	}

}
