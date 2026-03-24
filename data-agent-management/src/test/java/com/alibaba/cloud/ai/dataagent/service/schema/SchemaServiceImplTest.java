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
package com.alibaba.cloud.ai.dataagent.service.schema;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.dto.search.AgentSearchRequest;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SchemaServiceImplTest {

	private SchemaServiceImpl schemaService;

	private RecordingAgentVectorStoreService agentVectorStoreService;

	@BeforeEach
	void setUp() {
		DataAgentProperties dataAgentProperties = new DataAgentProperties();
		dataAgentProperties.getVectorStore().setTableTopkLimit(6);
		dataAgentProperties.getVectorStore().setTableSimilarityThreshold(0.35);
		agentVectorStoreService = new RecordingAgentVectorStoreService();

		schemaService = new SchemaServiceImpl(null, null, null, null, null, dataAgentProperties,
				agentVectorStoreService);
	}

	@Test
	void getTableDocumentsByDatasource_ShouldUseQueryInSimilaritySearch() {
		Integer datasourceId = 42;
		String query = "查询用户订单";
		List<Document> expectedDocuments = List.of(new Document("orders table",
				Map.of(DocumentMetadataConstant.NAME, "orders", Constant.DATASOURCE_ID, datasourceId.toString())));
		agentVectorStoreService.documentsToReturn = expectedDocuments;

		List<Document> actualDocuments = schemaService.getTableDocumentsByDatasource(datasourceId, query);

		assertEquals(expectedDocuments, actualDocuments);
		assertNotNull(agentVectorStoreService.lastSearchRequest);
		assertEquals(query, agentVectorStoreService.lastSearchRequest.getQuery());
		assertEquals(6, agentVectorStoreService.lastSearchRequest.getTopK());
		assertEquals(0.35, agentVectorStoreService.lastSearchRequest.getSimilarityThreshold());
		assertNotNull(agentVectorStoreService.lastSearchRequest.getFilterExpression());
	}

	private static final class RecordingAgentVectorStoreService implements AgentVectorStoreService {

		private SearchRequest lastSearchRequest;

		private List<Document> documentsToReturn = List.of();

		@Override
		public List<Document> search(AgentSearchRequest searchRequest) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Boolean deleteDocumentsByVectorType(String agentId, String vectorType) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Boolean deleteDocumentsByMetedata(String agentId, Map<String, Object> metadata) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Boolean deleteDocumentsByMetadata(Map<String, Object> metadata) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<Document> getDocumentsForAgent(String agentId, String query, String vectorType) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<Document> getDocumentsForAgent(String agentId, String query, String vectorType, int topK,
				double threshold) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<Document> similaritySearch(SearchRequest searchRequest) {
			this.lastSearchRequest = searchRequest;
			return documentsToReturn;
		}

		@Override
		public List<Document> getDocumentsOnlyByFilter(Filter.Expression filterExpression, Integer topK) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean hasDocuments(String agentId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void addDocuments(String agentId, List<Document> documents) {
			throw new UnsupportedOperationException();
		}

	}

}
