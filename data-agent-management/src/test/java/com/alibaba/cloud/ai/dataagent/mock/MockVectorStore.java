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
package com.alibaba.cloud.ai.dataagent.mock;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory VectorStore implementation for integration tests. Stores documents and
 * returns them based on simple content matching during similarity search.
 */
public class MockVectorStore implements VectorStore {

	private final List<Document> documents = new CopyOnWriteArrayList<>();

	@Override
	public void add(List<Document> documents) {
		this.documents.addAll(documents);
	}

	@Override
	public void delete(List<String> idList) {
		documents.removeIf(doc -> idList.contains(doc.getId()));
	}

	@Override
	public void delete(Filter.Expression expression) {
		// no-op for tests
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {
		String query = request.getQuery();
		int topK = request.getTopK();

		List<Document> results = new ArrayList<>();
		for (Document doc : documents) {
			if (doc.getText() != null && doc.getText().toLowerCase().contains(query.toLowerCase())) {
				results.add(doc);
			}
			if (results.size() >= topK) {
				break;
			}
		}

		if (results.isEmpty() && !documents.isEmpty()) {
			results.add(documents.get(0));
		}

		return results.subList(0, Math.min(results.size(), topK));
	}

	public List<Document> getAllDocuments() {
		return List.copyOf(documents);
	}

	public void clear() {
		documents.clear();
	}

}
