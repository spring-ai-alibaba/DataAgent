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
package com.alibaba.cloud.ai.service.code;

import com.alibaba.cloud.ai.config.DataAgentProperties;
import com.alibaba.cloud.ai.connector.accessor.Accessor;
import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.connector.bo.ColumnInfoBO;
import com.alibaba.cloud.ai.connector.bo.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.connector.bo.TableInfoBO;
import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.constant.Constant;
import com.alibaba.cloud.ai.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.request.AgentSearchRequest;
import com.alibaba.cloud.ai.request.SchemaInitRequest;
import com.alibaba.cloud.ai.service.hybrid.retrieval.HybridRetrievalStrategy;
import com.alibaba.cloud.ai.service.vectorstore.AgentVectorStoreServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.shaded.com.google.common.util.concurrent.MoreExecutors;

import java.util.*;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentVectorStoreServiceImplTest {

	private static final String TEST_AGENT_ID = "test-agent-id";

	private static final String TEST_VECTOR_TYPE = "test-vector-type";

	private static final String TEST_QUERY = "test query";

	@Mock
	private VectorStore vectorStore;

	private ExecutorService directExecutor;

	@Mock
	private Optional<HybridRetrievalStrategy> hybridRetrievalStrategy;

	@Mock(lenient = true)
	private DataAgentProperties dataAgentProperties;

	@Mock
	private BatchingStrategy batchingStrategy;

	@Mock
	private AccessorFactory accessorFactory;

	@Mock
	private Accessor accessor;

	@InjectMocks
	private AgentVectorStoreServiceImpl agentVectorStoreService;

	@BeforeEach
	void setUp() {
		// 使用 MoreExecutors.newDirectExecutorService() 创建单线程线程池
		directExecutor = MoreExecutors.newDirectExecutorService();

		// Setup dataAgentProperties mock
		DataAgentProperties.VectorStoreProperties vectorStoreProperties = new DataAgentProperties.VectorStoreProperties();
		vectorStoreProperties.setEnableHybridSearch(false);
		vectorStoreProperties.setTopkLimit(10);
		vectorStoreProperties.setSimilarityThreshold(0.7);
		vectorStoreProperties.setBatchDelTopkLimit(100);
		when(dataAgentProperties.getVectorStore()).thenReturn(vectorStoreProperties);
	}

	@Test
	@DisplayName("Test search with valid request")
	void testSearchWithValidRequest() {
		// Arrange
		AgentSearchRequest searchRequest = AgentSearchRequest.builder()
			.agentId(TEST_AGENT_ID)
			.docVectorType(TEST_VECTOR_TYPE)
			.query(TEST_QUERY)
			.topK(5)
			.similarityThreshold(0.8)
			.build();

		List<Document> expectedDocuments = Arrays.asList(new Document("Document 1 content", Map.of("key1", "value1")),
				new Document("Document 2 content", Map.of("key2", "value2")));

		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(expectedDocuments);

		// Act
		List<Document> result = agentVectorStoreService.search(searchRequest);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals(expectedDocuments, result);
		verify(vectorStore).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test search with hybrid search enabled")
	void testSearchWithHybridSearchEnabled() {
		// Arrange
		DataAgentProperties.VectorStoreProperties vectorStoreProperties = new DataAgentProperties.VectorStoreProperties();
		vectorStoreProperties.setEnableHybridSearch(true);
		when(dataAgentProperties.getVectorStore()).thenReturn(vectorStoreProperties);

		HybridRetrievalStrategy mockStrategy = mock(HybridRetrievalStrategy.class);
		// 正确地mock Optional<HybridRetrievalStrategy>
		when(hybridRetrievalStrategy.isPresent()).thenReturn(true);
		when(hybridRetrievalStrategy.get()).thenReturn(mockStrategy);

		AgentSearchRequest searchRequest = AgentSearchRequest.builder()
			.agentId(TEST_AGENT_ID)
			.docVectorType(TEST_VECTOR_TYPE)
			.query(TEST_QUERY)
			.topK(5)
			.similarityThreshold(0.8)
			.build();

		List<Document> expectedDocuments = Arrays.asList(new Document("Hybrid result 1", Map.of("key1", "value1")),
				new Document("Hybrid result 2", Map.of("key2", "value2")));

		when(mockStrategy.retrieve(any(AgentSearchRequest.class))).thenReturn(expectedDocuments);

		// Act
		List<Document> result = agentVectorStoreService.search(searchRequest);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals(expectedDocuments, result);
		verify(mockStrategy).retrieve(searchRequest);
		verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test addDocuments with valid input")
	void testAddDocuments() {
		// Arrange
		List<Document> documents = Arrays.asList(
				new Document("Document 1", Map.of(Constant.AGENT_ID, TEST_AGENT_ID, "key1", "value1")),
				new Document("Document 2", Map.of(Constant.AGENT_ID, TEST_AGENT_ID, "key2", "value2")));

		// Act
		agentVectorStoreService.addDocuments(TEST_AGENT_ID, documents);

		// Assert
		verify(vectorStore).add(documents);
	}

	@Test
	@DisplayName("Test addDocuments with null agentId should throw exception")
	void testAddDocumentsWithNullAgentId() {
		// Arrange
		List<Document> documents = Arrays.asList(new Document("Document 1", Map.of(Constant.AGENT_ID, TEST_AGENT_ID)));

		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> agentVectorStoreService.addDocuments(null, documents));
		assertEquals("AgentId cannot be null.", exception.getMessage());
	}

	@Test
	@DisplayName("Test addDocuments with empty documents should throw exception")
	void testAddDocumentsWithEmptyDocuments() {
		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> agentVectorStoreService.addDocuments(TEST_AGENT_ID, Collections.emptyList()));
		assertEquals("Documents cannot be empty.", exception.getMessage());
	}

	@Test
	@DisplayName("Test addDocuments with document missing agentId in metadata should throw exception")
	void testAddDocumentsWithMissingAgentIdInMetadata() {
		// Arrange
		List<Document> documents = Arrays.asList(new Document("Document 1", Map.of("key1", "value1")));

		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> agentVectorStoreService.addDocuments(TEST_AGENT_ID, documents));
		assertEquals("Document metadata must contain agentId.", exception.getMessage());
	}

	@Test
	@DisplayName("Test addDocuments with document having different agentId in metadata should throw exception")
	void testAddDocumentsWithDifferentAgentIdInMetadata() {
		// Arrange
		List<Document> documents = Arrays
			.asList(new Document("Document 1", Map.of(Constant.AGENT_ID, "different-agent-id")));

		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> agentVectorStoreService.addDocuments(TEST_AGENT_ID, documents));
		assertEquals("Document metadata agentId does not match.", exception.getMessage());
	}

	@Test
	@DisplayName("Test deleteDocumentsByVectorType")
	void testDeleteDocumentsByVectorType() throws Exception {
		// Arrange
		// 创建一个新的测试实例，使用SimpleVectorStore类型的mock
		VectorStore simpleVectorStore = mock(org.springframework.ai.vectorstore.SimpleVectorStore.class);

		// 使用反射设置vectorStore字段为SimpleVectorStore类型的mock
		ReflectionTestUtils.setField(agentVectorStoreService, "vectorStore", simpleVectorStore);

		List<Document> mockDocuments = Arrays.asList(
				new Document("Test content 1",
						Map.of(Constant.AGENT_ID, TEST_AGENT_ID, DocumentMetadataConstant.VECTOR_TYPE,
								TEST_VECTOR_TYPE)),
				new Document("Test content 2", Map.of(Constant.AGENT_ID, TEST_AGENT_ID,
						DocumentMetadataConstant.VECTOR_TYPE, TEST_VECTOR_TYPE)));
		// Mock the vectorStore.similaritySearch to return documents on first call and
		// empty list on subsequent calls
		when(simpleVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockDocuments)
			.thenReturn(Collections.emptyList());

		// Act
		Boolean result = agentVectorStoreService.deleteDocumentsByVectorType(TEST_AGENT_ID, TEST_VECTOR_TYPE);

		// Assert
		assertTrue(result);
		verify(simpleVectorStore, times(2)).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test deleteDocumentsByVectorType with null agentId should throw exception")
	void testDeleteDocumentsByVectorTypeWithNullAgentId() {
		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> agentVectorStoreService.deleteDocumentsByVectorType(null, TEST_VECTOR_TYPE));
		assertEquals("AgentId cannot be null.", exception.getMessage());
	}

	@Test
	@DisplayName("Test deleteDocumentsByVectorType with null vectorType should throw exception")
	void testDeleteDocumentsByVectorTypeWithNullVectorType() {
		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> agentVectorStoreService.deleteDocumentsByVectorType(TEST_AGENT_ID, null));
		assertEquals("VectorType cannot be null.", exception.getMessage());
	}

	@Test
	@DisplayName("Test deleteDocumentsByMetedata")
	void testDeleteDocumentsByMetedata() throws Exception {
		// Arrange
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key1", "value1");
		metadata.put("key2", 123);

		// 创建一个新的测试实例，使用SimpleVectorStore类型的mock
		VectorStore simpleVectorStore = mock(org.springframework.ai.vectorstore.SimpleVectorStore.class);

		// 使用反射设置vectorStore字段为SimpleVectorStore类型的mock
		ReflectionTestUtils.setField(agentVectorStoreService, "vectorStore", simpleVectorStore);

		List<Document> mockDocuments = Arrays.asList(
				new Document("Test content 1", Map.of(Constant.AGENT_ID, TEST_AGENT_ID, "key1", "value1", "key2", 123)),
				new Document("Test content 2",
						Map.of(Constant.AGENT_ID, TEST_AGENT_ID, "key1", "value1", "key2", 123)));
		// Mock the vectorStore.similaritySearch to return documents on first call and
		// empty list on subsequent calls
		when(simpleVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockDocuments)
			.thenReturn(Collections.emptyList());

		// Act
		Boolean result = agentVectorStoreService.deleteDocumentsByMetedata(TEST_AGENT_ID, metadata);

		// Assert
		assertTrue(result);
		verify(simpleVectorStore, times(2)).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test deleteDocumentsByMetedata with null agentId should throw exception")
	void testDeleteDocumentsByMetedataWithNullAgentId() {
		// Arrange
		Map<String, Object> metadata = new HashMap<>();

		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> agentVectorStoreService.deleteDocumentsByMetedata(null, metadata));
		assertEquals("AgentId cannot be empty.", exception.getMessage());
	}

	@Test
	@DisplayName("Test deleteDocumentsByMetedata with null metadata should throw exception")
	void testDeleteDocumentsByMetedataWithNullMetadata() {
		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> agentVectorStoreService.deleteDocumentsByMetedata(TEST_AGENT_ID, null));
		assertEquals("Metadata cannot be null.", exception.getMessage());
	}

	@Test
	@DisplayName("Test schema with exception should return false")
	void testSchemaWithException() throws Exception {
		// Arrange
		SchemaInitRequest schemaInitRequest = new SchemaInitRequest();
		DbConfig dbConfig = new DbConfig();
		schemaInitRequest.setDbConfig(dbConfig);

		when(accessorFactory.getAccessorByDbConfig(any(DbConfig.class)))
			.thenThrow(new RuntimeException("Database connection failed"));

		// Act
		Boolean result = agentVectorStoreService.schema(TEST_AGENT_ID, schemaInitRequest);

		// Assert
		assertFalse(result);
		verify(accessorFactory).getAccessorByDbConfig(dbConfig);
	}

	@Test
	@DisplayName("Test buildForeignKeyMap")
	void testBuildForeignKeyMap() {
		// Arrange
		List<ForeignKeyInfoBO> foreignKeys = Arrays.asList(new ForeignKeyInfoBO("table1", "col1", "table2", "col2"),
				new ForeignKeyInfoBO("table3", "col3", "table4", "col4"));

		// Act
		Map<String, List<String>> result = (Map<String, List<String>>) ReflectionTestUtils
			.invokeMethod(agentVectorStoreService, "buildForeignKeyMap", foreignKeys);

		// Assert
		assertNotNull(result);
		assertEquals(4, result.size());
		assertTrue(result.containsKey("table1"));
		assertTrue(result.containsKey("table2"));
		assertTrue(result.containsKey("table3"));
		assertTrue(result.containsKey("table4"));
		assertEquals(1, result.get("table1").size());
		assertEquals(1, result.get("table2").size());
		assertEquals(1, result.get("table3").size());
		assertEquals(1, result.get("table4").size());
		assertTrue(result.get("table1").get(0).contains("table1.col1=table2.col2"));
		assertTrue(result.get("table2").get(0).contains("table1.col1=table2.col2"));
		assertTrue(result.get("table3").get(0).contains("table3.col3=table4.col4"));
		assertTrue(result.get("table4").get(0).contains("table3.col3=table4.col4"));
	}

	@Test
	@DisplayName("Test partitionList")
	void testPartitionList() {
		// Arrange
		List<String> list = Arrays.asList("item1", "item2", "item3", "item4", "item5");
		int batchSize = 2;

		// Act
		@SuppressWarnings("unchecked")
		List<List<String>> result = (List<List<String>>) ReflectionTestUtils.invokeMethod(agentVectorStoreService,
				"partitionList", list, batchSize);

		// Assert
		assertNotNull(result);
		assertEquals(3, result.size());
		assertEquals(2, result.get(0).size());
		assertEquals(2, result.get(1).size());
		assertEquals(1, result.get(2).size());
		assertEquals("item1", result.get(0).get(0));
		assertEquals("item2", result.get(0).get(1));
		assertEquals("item3", result.get(1).get(0));
		assertEquals("item4", result.get(1).get(1));
		assertEquals("item5", result.get(2).get(0));
	}

	@Test
	@DisplayName("Test getDocumentsForAgent")
	void testGetDocumentsForAgent() {
		// Arrange
		AgentSearchRequest searchRequest = AgentSearchRequest.builder()
			.agentId(TEST_AGENT_ID)
			.docVectorType(TEST_VECTOR_TYPE)
			.query(TEST_QUERY)
			.topK(10)
			.similarityThreshold(0.7)
			.build();

		List<Document> expectedDocuments = Arrays.asList(new Document("Document 1 content", Map.of("key1", "value1")),
				new Document("Document 2 content", Map.of("key2", "value2")));

		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(expectedDocuments);

		// Act
		List<Document> result = agentVectorStoreService.getDocumentsForAgent(TEST_AGENT_ID, TEST_QUERY,
				TEST_VECTOR_TYPE);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals(expectedDocuments, result);
		verify(vectorStore).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test getDocumentsOnlyByFilter")
	void testGetDocumentsOnlyByFilter() {
		// Arrange
		String filterExpression = "agentId == 'test-agent-id' && vectorType == 'test-vector-type'";
		int topK = 5;

		List<Document> expectedDocuments = Arrays.asList(new Document("Document 1 content", Map.of("key1", "value1")),
				new Document("Document 2 content", Map.of("key2", "value2")));

		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(expectedDocuments);

		// Act
		List<Document> result = agentVectorStoreService.getDocumentsOnlyByFilter(filterExpression, topK);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals(expectedDocuments, result);
		verify(vectorStore).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test getTableDocuments")
	void testGetTableDocuments() {
		// Arrange
		List<String> tableNames = Arrays.asList("table1", "table2");

		List<Document> expectedDocuments = Arrays.asList(new Document("Table 1 content", Map.of("key1", "value1")),
				new Document("Table 2 content", Map.of("key2", "value2")));

		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(expectedDocuments);

		// Act
		List<Document> result = agentVectorStoreService.getTableDocuments(TEST_AGENT_ID, tableNames);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals(expectedDocuments, result);
		verify(vectorStore).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test getTableDocuments with empty table names")
	void testGetTableDocumentsWithEmptyTableNames() {
		// Arrange
		List<String> tableNames = Collections.emptyList();

		// Act
		List<Document> result = agentVectorStoreService.getTableDocuments(TEST_AGENT_ID, tableNames);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
		verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test getColumnDocuments")
	void testGetColumnDocuments() {
		// Arrange
		String upstreamTableName = "table1";
		List<String> columnNames = Arrays.asList("col1", "col2");

		List<Document> expectedDocuments = Arrays.asList(new Document("Column 1 content", Map.of("key1", "value1")),
				new Document("Column 2 content", Map.of("key2", "value2")));

		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(expectedDocuments);

		// Act
		List<Document> result = agentVectorStoreService.getColumnDocuments(TEST_AGENT_ID, upstreamTableName,
				columnNames);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals(expectedDocuments, result);
		verify(vectorStore).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test getColumnDocuments with empty column names")
	void testGetColumnDocumentsWithEmptyColumnNames() {
		// Arrange
		String upstreamTableName = "table1";
		List<String> columnNames = Collections.emptyList();

		// Act
		List<Document> result = agentVectorStoreService.getColumnDocuments(TEST_AGENT_ID, upstreamTableName,
				columnNames);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
		verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test hasDocuments with existing documents")
	void testHasDocumentsWithExistingDocuments() {
		// Arrange
		List<Document> mockDocuments = Arrays
			.asList(new Document("Test content", Map.of(Constant.AGENT_ID, TEST_AGENT_ID)));

		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockDocuments);

		// Act
		boolean result = agentVectorStoreService.hasDocuments(TEST_AGENT_ID);

		// Assert
		assertTrue(result);
		verify(vectorStore).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test hasDocuments with no documents")
	void testHasDocumentsWithNoDocuments() {
		// Arrange
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

		// Act
		boolean result = agentVectorStoreService.hasDocuments(TEST_AGENT_ID);

		// Assert
		assertFalse(result);
		verify(vectorStore).similaritySearch(any(SearchRequest.class));
	}

	/**
	 * Helper method to create TableInfoBO with columns
	 */
	private TableInfoBO createTableInfoBO(String name, String description, List<ColumnInfoBO> columns) {
		TableInfoBO table = TableInfoBO.builder().name(name).description(description).build();
		table.setColumns(columns);
		return table;
	}

}
