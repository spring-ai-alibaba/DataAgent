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

import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.constant.Constant;
import com.alibaba.cloud.ai.connector.accessor.Accessor;
import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.connector.bo.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.connector.bo.TableInfoBO;
import com.alibaba.cloud.ai.request.AgentSearchRequest;
import com.alibaba.cloud.ai.request.SchemaInitRequest;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AgentVectorStoreServiceImpl}
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentVectorStoreService Tests")
public class AgentVectorStoreServiceImplTest {

	@Mock
	private VectorStore vectorStore;

	@Mock
	private BatchingStrategy batchingStrategy;

	@Mock
	private AccessorFactory accessorFactory;

	@Mock
	private Accessor accessor;

	@InjectMocks
	private AgentVectorStoreServiceImpl agentVectorStoreService;

	private final String TEST_AGENT_ID = "test-agent-id";

	private final String TEST_QUERY = "test query";

	private final String TEST_VECTOR_TYPE = "column";

	@BeforeEach
	void setUp() {
		// Set similarity threshold for testing
		ReflectionTestUtils.setField(agentVectorStoreService, "similarityThreshold", 0.2);
	}

	@Test
	@DisplayName("Test search with valid request")
	void testSearchWithValidRequest() {
		// Arrange
		AgentSearchRequest searchRequest = AgentSearchRequest.getInstance(TEST_AGENT_ID);
		searchRequest.setQuery(TEST_QUERY);
		searchRequest.setTopK(5);
		searchRequest.setMetadataFilter(Map.of(Constant.VECTOR_TYPE, TEST_VECTOR_TYPE));

		List<Document> expectedDocuments = Arrays.asList(
				new Document("Test content 1",
						Map.of(Constant.AGENT_ID, TEST_AGENT_ID, Constant.VECTOR_TYPE, TEST_VECTOR_TYPE)),
				new Document("Test content 2",
						Map.of(Constant.AGENT_ID, TEST_AGENT_ID, Constant.VECTOR_TYPE, TEST_VECTOR_TYPE)));

		// Mock the vectorStore.similaritySearch to return documents on first call and
		// empty list on subsequent calls
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(expectedDocuments)
			.thenReturn(Collections.emptyList());

		// Act
		List<Document> result = agentVectorStoreService.search(searchRequest);

		// Assert
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals(expectedDocuments, result);
		verify(vectorStore).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test addDocuments with valid input")
	void testAddDocuments() {
		// Arrange
		List<Document> documents = Arrays.asList(new Document("Test content 1"), new Document("Test content 2"));

		// Act
		agentVectorStoreService.addDocuments(TEST_AGENT_ID, documents);

		// Assert
		verify(vectorStore).add(documents);
	}

	@Test
	@DisplayName("Test addDocuments with null agentId should throw exception")
	void testAddDocumentsWithNullAgentId() {
		// Arrange
		List<Document> documents = Arrays.asList(new Document("Test content"));

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
	@DisplayName("Test estimateDocuments with existing documents")
	void testEstimateDocuments() {
		// Arrange
		List<Document> mockDocuments = Arrays.asList(
				new Document("Test content 1", Map.of(Constant.AGENT_ID, TEST_AGENT_ID)),
				new Document("Test content 2", Map.of(Constant.AGENT_ID, TEST_AGENT_ID)));
		// Mock the vectorStore.similaritySearch to return documents on first call and
		// empty list on subsequent calls
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockDocuments)
			.thenReturn(Collections.emptyList());

		// Act
		int result = agentVectorStoreService.estimateDocuments(TEST_AGENT_ID);

		// Assert
		assertEquals(2, result);
		verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test hasDocuments with existing documents returns true")
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
	@DisplayName("Test hasDocuments with no documents returns false")
	void testHasDocumentsWithNoDocuments() {
		// Arrange
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

		// Act
		boolean result = agentVectorStoreService.hasDocuments(TEST_AGENT_ID);

		// Assert
		assertFalse(result);
		verify(vectorStore).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test getDocumentsForAgent")
	void testGetDocumentsForAgent() {
		// Arrange
		List<Document> expectedDocuments = Arrays.asList(new Document("Test content",
				Map.of(Constant.AGENT_ID, TEST_AGENT_ID, Constant.VECTOR_TYPE, TEST_VECTOR_TYPE)));

		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(expectedDocuments);

		// Act
		List<Document> result = agentVectorStoreService.getDocumentsForAgent(TEST_AGENT_ID, TEST_QUERY,
				TEST_VECTOR_TYPE);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(expectedDocuments, result);
		verify(vectorStore).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test deleteDocumentsByVectorType")
	void testDeleteDocumentsByVectorType() throws Exception {
		// Arrange
		List<Document> mockDocuments = Arrays.asList(
				new Document("Test content 1",
						Map.of(Constant.AGENT_ID, TEST_AGENT_ID, Constant.VECTOR_TYPE, TEST_VECTOR_TYPE)),
				new Document("Test content 2",
						Map.of(Constant.AGENT_ID, TEST_AGENT_ID, Constant.VECTOR_TYPE, TEST_VECTOR_TYPE)));
		// Mock the vectorStore.similaritySearch to return documents on first call and
		// empty list on subsequent calls
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockDocuments)
			.thenReturn(Collections.emptyList());

		// Act
		Boolean result = agentVectorStoreService.deleteDocumentsByVectorType(TEST_AGENT_ID, TEST_VECTOR_TYPE);

		// Assert
		assertTrue(result);
		verify(vectorStore, times(2)).similaritySearch(any(SearchRequest.class));
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

		List<Document> mockDocuments = Arrays.asList(
				new Document("Test content 1", Map.of(Constant.AGENT_ID, TEST_AGENT_ID, "key1", "value1", "key2", 123)),
				new Document("Test content 2",
						Map.of(Constant.AGENT_ID, TEST_AGENT_ID, "key1", "value1", "key2", 123)));
		// Mock the vectorStore.similaritySearch to return documents on first call and
		// empty list on subsequent calls
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockDocuments)
			.thenReturn(Collections.emptyList());

		// Act
		Boolean result = agentVectorStoreService.deleteDocumentsByMetedata(TEST_AGENT_ID, metadata);

		// Assert
		assertTrue(result);
		verify(vectorStore, times(2)).similaritySearch(any(SearchRequest.class));
	}

	@Test
	@DisplayName("Test deleteDocumentsByMetedata with null agentId should throw exception")
	void testDeleteDocumentsByMetedataWithNullAgentId() {
		// Arrange
		Map<String, Object> metadata = new HashMap<>();

		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> agentVectorStoreService.deleteDocumentsByMetedata(null, metadata));
		assertEquals("AgentId cannot be null.", exception.getMessage());
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
	@DisplayName("Test schema with valid input")
	void testSchemaWithValidInput() throws Exception {
		// Arrange
		SchemaInitRequest schemaInitRequest = new SchemaInitRequest();
		DbConfig dbConfig = new DbConfig();
		dbConfig.setSchema("test-schema");
		schemaInitRequest.setDbConfig(dbConfig);
		schemaInitRequest.setTables(Arrays.asList("table1", "table2"));

		List<ForeignKeyInfoBO> foreignKeys = Arrays.asList(new ForeignKeyInfoBO("table1", "col1", "table2", "col2"));

		List<TableInfoBO> tables = Arrays.asList(
				TableInfoBO.builder().name("table1").description("Description 1").build(),
				TableInfoBO.builder().name("table2").description("Description 2").build());

		when(accessorFactory.getAccessorByDbConfig(any(DbConfig.class))).thenReturn(accessor);
		when(accessor.showForeignKeys(any(DbConfig.class), any())).thenReturn(foreignKeys);
		when(accessor.fetchTables(any(DbConfig.class), any())).thenReturn(tables);
		// Create a list of documents to simulate the batching strategy output
		List<Document> documents = Arrays.asList(
				new Document("table1 content", Map.of("name", "table1", "description", "Description 1")),
				new Document("table2 content", Map.of("name", "table2", "description", "Description 2")));
		when(batchingStrategy.batch(any())).thenReturn(Arrays.asList(documents));

		// Act
		Boolean result = agentVectorStoreService.schema(TEST_AGENT_ID, schemaInitRequest);

		// Assert
		assertTrue(result);
		verify(accessorFactory).getAccessorByDbConfig(dbConfig);
		verify(accessor).showForeignKeys(eq(dbConfig), any());
		verify(accessor).fetchTables(eq(dbConfig), any());
		verify(vectorStore, atLeastOnce()).add(any());
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
	@DisplayName("Test buildFilterExpressionString with null map")
	void testBuildFilterExpressionStringWithNullMap() {
		// Act
		String result = (String) ReflectionTestUtils.invokeMethod(agentVectorStoreService,
				"buildFilterExpressionString", (Map<String, Object>) null);

		// Assert
		assertNull(result);
	}

	@Test
	@DisplayName("Test buildFilterExpressionString with empty map")
	void testBuildFilterExpressionStringWithEmptyMap() {
		// Act
		String result = (String) ReflectionTestUtils.invokeMethod(agentVectorStoreService,
				"buildFilterExpressionString", new HashMap<>());

		// Assert
		assertNull(result);
	}

	@Test
	@DisplayName("Test buildFilterExpressionString with valid map")
	void testBuildFilterExpressionStringWithValidMap() {
		// Arrange
		Map<String, Object> filterMap = new HashMap<>();
		filterMap.put("key1", "value1");
		filterMap.put("key2", 123);
		filterMap.put("key3", true);
		filterMap.put("key4", null);

		// Act
		String result = (String) ReflectionTestUtils.invokeMethod(agentVectorStoreService,
				"buildFilterExpressionString", filterMap);

		// Assert
		assertNotNull(result);
		assertTrue(result.contains("key1 == 'value1'"));
		assertTrue(result.contains("key2 == 123"));
		assertTrue(result.contains("key3 == true"));
		assertTrue(result.contains("key4 == null"));
		assertTrue(result.contains(" && "));
	}

	@Test
	@DisplayName("Test buildFilterExpressionString with invalid key should throw exception")
	void testBuildFilterExpressionStringWithInvalidKey() {
		// Arrange
		Map<String, Object> filterMap = new HashMap<>();
		filterMap.put("invalid-key", "value1");

		// Act & Assert
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils
			.invokeMethod(agentVectorStoreService, "buildFilterExpressionString", filterMap));
		assertTrue(exception.getMessage().contains("Invalid key name: invalid-key"));
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
	@DisplayName("Test escapeStringLiteral")
	void testEscapeStringLiteral() {
		// Test null input
		String result1 = (String) ReflectionTestUtils.invokeMethod(agentVectorStoreService, "escapeStringLiteral",
				(String) null);
		assertEquals("", result1);

		// Test normal string
		String result2 = (String) ReflectionTestUtils.invokeMethod(agentVectorStoreService, "escapeStringLiteral",
				"normal string");
		assertEquals("normal string", result2);

		// Test string with special characters
		String result3 = (String) ReflectionTestUtils.invokeMethod(agentVectorStoreService, "escapeStringLiteral",
				"string'with\\special\nchars");
		assertEquals("string\\'with\\\\special\\nchars", result3);
	}

}
