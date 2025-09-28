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
package com.alibaba.cloud.ai.service.vectorstore;

import com.alibaba.cloud.ai.request.SearchRequest;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class AbstractVectorStoreService implements VectorStoreService {

	/**
	 * Get embedding model
	 */
	protected abstract EmbeddingModel getEmbeddingModel();

	/**
	 * Convert text to Double type vector
	 */
	public List<Double> embedDouble(String text) {
		return convertToDoubleList(getEmbeddingModel().embed(text));
	}

	/**
	 * Convert text to Float type vector
	 */
	public List<Float> embedFloat(String text) {
		return convertToFloatList(getEmbeddingModel().embed(text));
	}

	/**
	 * Get documents from vector store
	 */
	@Override
	public List<Document> getDocuments(String query, String vectorType) {
		SearchRequest request = new SearchRequest();
		request.setQuery(query);
		request.setVectorType(vectorType);
		request.setTopK(100);
		return new ArrayList<>(searchWithVectorType(request));
	}

	/**
	 * Convert float[] to Double List
	 */
	protected List<Double> convertToDoubleList(float[] array) {
		return IntStream.range(0, array.length)
			.mapToDouble(i -> (double) array[i])
			.boxed()
			.collect(Collectors.toList());
	}

	/**
	 * Convert float[] to Float List
	 */
	protected List<Float> convertToFloatList(float[] array) {
		return IntStream.range(0, array.length).mapToObj(i -> array[i]).collect(Collectors.toList());
	}

}
