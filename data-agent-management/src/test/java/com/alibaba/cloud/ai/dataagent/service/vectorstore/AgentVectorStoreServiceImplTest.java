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

import com.alibaba.cloud.ai.dataagent.dto.search.AgentSearchRequest;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentVectorStoreServiceImplTest {

	@Mock
	private VectorStore vectorStore;

	@Mock
	private DynamicFilterService dynamicFilterService;

	private DataAgentProperties dataAgentProperties;

	private AgentVectorStoreServiceImpl service;

	@BeforeEach
	void setUp() {
		dataAgentProperties = new DataAgentProperties();
		DataAgentProperties.VectorStoreProperties vsProps = new DataAgentProperties.VectorStoreProperties();
		vsProps.setDefaultTopkLimit(10);
		vsProps.setDefaultSimilarityThreshold(0.5);
		vsProps.setEnableHybridSearch(false);
		vsProps.setBatchDelTopkLimit(100);
		dataAgentProperties.setVectorStore(vsProps);

		service = new AgentVectorStoreServiceImpl(vectorStore, Optional.empty(), dataAgentProperties,
				dynamicFilterService);
	}

	@Test
	void search_emptyAgentId_throws() {
		AgentSearchRequest request = AgentSearchRequest.builder()
			.agentId("")
			.docVectorType("KNOWLEDGE")
			.query("test")
			.topK(10)
			.similarityThreshold(0.5)
			.build();

		assertThrows(IllegalArgumentException.class, () -> service.search(request));
	}

	@Test
	void search_nullFilter_returnsEmpty() {
		when(dynamicFilterService.buildDynamicFilter("1", "KNOWLEDGE")).thenReturn(null);

		AgentSearchRequest request = AgentSearchRequest.builder()
			.agentId("1")
			.docVectorType("KNOWLEDGE")
			.query("test")
			.topK(10)
			.similarityThreshold(0.5)
			.build();

		List<Document> result = service.search(request);
		assertTrue(result.isEmpty());
	}

	@Test
	void search_withFilter_delegatesToVectorStore() {
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		Filter.Expression filter = b.eq("agentId", "1").build();
		when(dynamicFilterService.buildDynamicFilter("1", "KNOWLEDGE")).thenReturn(filter);
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		AgentSearchRequest request = AgentSearchRequest.builder()
			.agentId("1")
			.docVectorType("KNOWLEDGE")
			.query("test")
			.topK(10)
			.similarityThreshold(0.5)
			.build();

		List<Document> result = service.search(request);
		assertNotNull(result);
		verify(vectorStore).similaritySearch(any(SearchRequest.class));
	}

	@Test
	void addDocuments_nullAgentId_throws() {
		assertThrows(IllegalArgumentException.class, () -> service.addDocuments(null, List.of(new Document("test"))));
	}

	@Test
	void addDocuments_emptyDocuments_throws() {
		assertThrows(IllegalArgumentException.class, () -> service.addDocuments("1", Collections.emptyList()));
	}

	@Test
	void addDocuments_withTableType_requiresDatasourceId() {
		Map<String, Object> meta = new HashMap<>();
		meta.put("vectorType", "table");
		meta.put("datasourceId", "1");
		Document doc = new Document("table content", meta);

		service.addDocuments("1", List.of(doc));
		verify(vectorStore).add(anyList());
	}

	@Test
	void addDocuments_withKnowledgeType_requiresAgentId() {
		Map<String, Object> meta = new HashMap<>();
		meta.put("vectorType", "businessTerm");
		meta.put("agentId", "1");
		Document doc = new Document("knowledge content", meta);

		service.addDocuments("1", List.of(doc));
		verify(vectorStore).add(anyList());
	}

	@Test
	void addDocuments_knowledgeType_mismatchedAgentId_throws() {
		Map<String, Object> meta = new HashMap<>();
		meta.put("vectorType", "businessTerm");
		meta.put("agentId", "2");
		Document doc = new Document("content", meta);

		assertThrows(IllegalArgumentException.class, () -> service.addDocuments("1", List.of(doc)));
	}

	@Test
	void deleteDocumentsByVectorType_delegates() throws Exception {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

		Boolean result = service.deleteDocumentsByVectorType("1", "KNOWLEDGE");
		assertTrue(result);
	}

	@Test
	void deleteDocumentsByMetadata_nonSimpleStore_usesFilterDelete() {
		Boolean result = service.deleteDocumentsByMetadata(Map.of("agentId", "1"));
		assertTrue(result);
		verify(vectorStore).delete(anyString());
	}

	@Test
	void getDocumentsForAgent_defaultParams() {
		when(dynamicFilterService.buildDynamicFilter(anyString(), anyString())).thenReturn(null);

		List<Document> result = service.getDocumentsForAgent("1", "query", "KNOWLEDGE");
		assertTrue(result.isEmpty());
	}

	@Test
	void getDocumentsForAgent_customParams() {
		when(dynamicFilterService.buildDynamicFilter("1", "KNOWLEDGE")).thenReturn(null);

		List<Document> result = service.getDocumentsForAgent("1", "query", "KNOWLEDGE", 5, 0.8);
		assertTrue(result.isEmpty());
	}

	@Test
	void getDocumentsOnlyByFilter_nullFilter_throws() {
		assertThrows(IllegalArgumentException.class, () -> service.getDocumentsOnlyByFilter(null, 10));
	}

	@Test
	void getDocumentsOnlyByFilter_nullTopK_usesDefault() {
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		Filter.Expression filter = b.eq("agentId", "1").build();
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		List<Document> result = service.getDocumentsOnlyByFilter(filter, null);
		assertNotNull(result);
	}

	@Test
	void hasDocuments_withDocs_returnsTrue() {
		Document doc = new Document("content", Map.of("agentId", "1"));
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

		assertTrue(service.hasDocuments("1"));
	}

	@Test
	void hasDocuments_noDocs_returnsFalse() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

		assertFalse(service.hasDocuments("1"));
	}

}
