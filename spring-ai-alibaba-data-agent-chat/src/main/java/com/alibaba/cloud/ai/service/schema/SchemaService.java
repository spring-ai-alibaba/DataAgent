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

package com.alibaba.cloud.ai.service.schema;

import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.dto.schema.SchemaDTO;
import org.springframework.ai.document.Document;

import java.util.List;

public interface SchemaService {

	List<Document> getTableDocumentsForAgent(String agentId, String query);

	List<List<Document>> getColumnDocumentsByKeywordsForAgent(String agentId, List<String> keywords);

	void extractDatabaseName(SchemaDTO schemaDTO, DbConfig dbConfig);

	void buildSchemaFromDocuments(String agentId, List<List<Document>> columnDocumentList,
			List<Document> tableDocuments, SchemaDTO schemaDTO);

	SchemaDTO mixRagForAgent(String agentId, String query, List<String> keywords, DbConfig dbConfig);

	default List<Document> getTableDocuments(String query) {
		return getTableDocumentsForAgent(null, query);
	}

	default List<List<Document>> getColumnDocumentsByKeywords(List<String> keywords) {
		return getColumnDocumentsByKeywordsForAgent(null, keywords);
	}

	default SchemaDTO mixRag(String query, List<String> keywords, DbConfig dbConfig) {
		return mixRagForAgent(null, query, keywords, dbConfig);
	}

}
