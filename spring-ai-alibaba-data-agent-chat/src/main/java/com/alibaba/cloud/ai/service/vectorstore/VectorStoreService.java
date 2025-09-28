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
