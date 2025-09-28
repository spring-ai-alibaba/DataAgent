package com.alibaba.cloud.ai.service.schema;

import com.alibaba.cloud.ai.dto.schema.SchemaDTO;
import org.springframework.ai.document.Document;

import java.util.List;

public interface SchemaService {

	List<Document> getTableDocumentsForAgent(String agentId, String query);

	List<List<Document>> getColumnDocumentsByKeywordsForAgent(String agentId, List<String> keywords);

	void extractDatabaseName(SchemaDTO schemaDTO);

	void buildSchemaFromDocuments(List<List<Document>> columnDocumentList, List<Document> tableDocuments,
			SchemaDTO schemaDTO);

	SchemaDTO mixRagForAgent(String agentId, String query, List<String> keywords);

	default List<Document> getTableDocuments(String query) {
		return getTableDocumentsForAgent(null, query);
	}

	default List<List<Document>> getColumnDocumentsByKeywords(List<String> keywords) {
		return getColumnDocumentsByKeywordsForAgent(null, keywords);
	}

	default SchemaDTO mixRag(String query, List<String> keywords) {
		return mixRagForAgent(null, query, keywords);
	}

}
