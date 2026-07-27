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
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.converter.SimpleVectorStoreFilterExpressionConverter;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;
import java.util.Map;

/**
 * Adds metadata deletion to Spring AI's in-memory fallback store.
 *
 * <p>
 * {@link SimpleVectorStore} supports ID deletion but not filter deletion. Selecting IDs
 * through similarity search would invoke the embedding model for a delete operation. This
 * adapter keeps Spring AI's storage and lifecycle implementation while supplying the one
 * provider-specific operation that it does not expose.
 */
public final class MetadataAwareSimpleVectorStore extends SimpleVectorStore {

	private final SpelExpressionParser expressionParser = new SpelExpressionParser();

	private final SimpleVectorStoreFilterExpressionConverter filterConverter = new SimpleVectorStoreFilterExpressionConverter();

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
		var expression = expressionParser.parseExpression(filterConverter.convertExpression(filterExpression));
		return store.values().stream().filter(content -> {
			StandardEvaluationContext context = new StandardEvaluationContext();
			context.setVariable("metadata", content.getMetadata());
			return Boolean.TRUE.equals(expression.getValue(context, Boolean.class));
		})
			.limit(limit)
			.map(content -> Document.builder()
				.id(content.getId())
				.text(content.getText())
				.metadata(content.getMetadata())
				.build())
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
