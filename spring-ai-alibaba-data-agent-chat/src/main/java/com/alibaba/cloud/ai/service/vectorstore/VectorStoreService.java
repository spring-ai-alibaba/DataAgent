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

import com.alibaba.cloud.ai.request.SchemaInitRequest;
import com.alibaba.cloud.ai.request.SearchRequest;
import org.springframework.ai.document.Document;

import java.util.List;

public interface VectorStoreService {

	/**
	 * Search interface with default filter
	 */
	List<Document> searchWithVectorType(SearchRequest searchRequestDTO);

	/**
	 * Search interface with custom filter
	 */
	List<Document> searchWithFilter(SearchRequest searchRequestDTO);

	List<Document> getDocuments(String query, String vectorType);

	/**
	 * Get documents for tables
	 */
	default List<Document> searchTableByNameAndVectorType(SearchRequest searchRequestDTO) {
		throw new UnsupportedOperationException("Not implemented.");
	}

	/**
	 * Get documents from vector store for specified agent
	 */
	default List<Document> getDocumentsForAgent(String agentId, String query, String vectorType) {
		// Default implementation: if subclass doesn't override, use global search
		return getDocuments(query, vectorType);
	}

	default AgentVectorStoreManager getAgentVectorStoreManager() {
		throw new UnsupportedOperationException("Not implemented.");
	}

	default Boolean schemaForAgent(String agentId, SchemaInitRequest schemaInitRequest) throws Exception {
		throw new UnsupportedOperationException("Not implemented.");
	}

	default Boolean schema(SchemaInitRequest schemaInitRequest) throws Exception {
		throw new UnsupportedOperationException("Not implemented.");
	}

}
