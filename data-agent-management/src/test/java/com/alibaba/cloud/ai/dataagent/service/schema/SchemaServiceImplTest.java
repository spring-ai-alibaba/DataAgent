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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchemaServiceImplTest {

	@Mock
	private AccessorFactory accessorFactory;

	@Mock
	private TableMetadataService tableMetadataService;

	@Mock
	private BatchingStrategy batchingStrategy;

	@Mock
	private DynamicFilterService dynamicFilterService;

	@Mock
	private DataAgentProperties dataAgentProperties;

	@Mock
	private AgentVectorStoreService agentVectorStoreService;

	private SchemaServiceImpl schemaService;

	@BeforeEach
	void setUp() {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		schemaService = new SchemaServiceImpl(executor, accessorFactory, tableMetadataService, batchingStrategy,
				dynamicFilterService, dataAgentProperties, agentVectorStoreService);

		DataAgentProperties.VectorStoreProperties vsProps = new DataAgentProperties.VectorStoreProperties();
		vsProps.setTableTopkLimit(10);
		vsProps.setTableSimilarityThreshold(0.5);
		vsProps.setDefaultTopkLimit(10);
		vsProps.setDefaultSimilarityThreshold(0.5);
		when(dataAgentProperties.getVectorStore()).thenReturn(vsProps);
		when(dataAgentProperties.getMaxColumnsPerTable()).thenReturn(50);
	}

	private Document createTableDoc(String name) {
		Map<String, Object> meta = new HashMap<>();
		meta.put("name", name);
		meta.put("description", name + " description");
		meta.put("foreignKey", "");
		meta.put("datasourceId", "1");
		meta.put("vectorType", "TABLE");
		return new Document("table: " + name, meta);
	}

	private Document createColumnDoc(String tableName, String columnName) {
		Map<String, Object> meta = new HashMap<>();
		meta.put("name", columnName);
		meta.put("description", columnName + " column");
		meta.put("type", "VARCHAR");
		meta.put("tableName", tableName);
		meta.put("vectorType", "COLUMN");
		return new Document("column: " + columnName, meta);
	}

	@Test
	void buildTableListFromDocuments_singleTable() {
		List<Document> docs = List.of(createTableDoc("users"));
		List<TableDTO> tables = schemaService.buildTableListFromDocuments(docs);

		assertEquals(1, tables.size());
		assertEquals("users", tables.get(0).getName());
	}

	@Test
	void buildTableListFromDocuments_withPrimaryKeyList() {
		Map<String, Object> meta = new HashMap<>();
		meta.put("name", "orders");
		meta.put("description", "Orders table");
		meta.put("primaryKey", List.of("id", "user_id"));
		Document doc = new Document("table: orders", meta);

		List<TableDTO> tables = schemaService.buildTableListFromDocuments(List.of(doc));
		assertEquals(2, tables.get(0).getPrimaryKeys().size());
	}

	@Test
	void buildTableListFromDocuments_withPrimaryKeyString() {
		Map<String, Object> meta = new HashMap<>();
		meta.put("name", "orders");
		meta.put("description", "Orders table");
		meta.put("primaryKey", "id");
		Document doc = new Document("table: orders", meta);

		List<TableDTO> tables = schemaService.buildTableListFromDocuments(List.of(doc));
		assertEquals(1, tables.get(0).getPrimaryKeys().size());
	}

	@Test
	void buildTableListFromDocuments_emptyPrimaryKeyString() {
		Map<String, Object> meta = new HashMap<>();
		meta.put("name", "orders");
		meta.put("description", "Orders table");
		meta.put("primaryKey", "");
		Document doc = new Document("table: orders", meta);

		List<TableDTO> tables = schemaService.buildTableListFromDocuments(List.of(doc));
		assertNull(tables.get(0).getPrimaryKeys());
	}

	@Test
	void extractRelatedNamesFromForeignKeys_nonePresent() {
		List<Document> docs = List.of(createTableDoc("users"));
		Set<String> names = schemaService.extractRelatedNamesFromForeignKeys(docs);
		assertTrue(names.isEmpty());
	}

	@Test
	void extractRelatedNamesFromForeignKeys_withFK() {
		Map<String, Object> meta = new HashMap<>();
		meta.put("name", "orders");
		meta.put("description", "");
		meta.put("foreignKey", "orders.user_id=users.id");
		Document doc = new Document("table: orders", meta);

		Set<String> names = schemaService.extractRelatedNamesFromForeignKeys(List.of(doc));
		assertEquals(2, names.size());
		assertTrue(names.contains("orders.user_id"));
		assertTrue(names.contains("users.id"));
	}

	@Test
	void extractRelatedNamesFromForeignKeys_multipleFKs() {
		Map<String, Object> meta = new HashMap<>();
		meta.put("name", "order_items");
		meta.put("description", "");
		meta.put("foreignKey", "order_items.order_id=orders.id、order_items.product_id=products.id");
		Document doc = new Document("table: order_items", meta);

		Set<String> names = schemaService.extractRelatedNamesFromForeignKeys(List.of(doc));
		assertEquals(4, names.size());
	}

	@Test
	void buildForeignKeyMap_empty() {
		Map<String, List<String>> map = schemaService.buildForeignKeyMap(Collections.emptyList());
		assertTrue(map.isEmpty());
	}

	@Test
	void buildForeignKeyMap_withForeignKeys() {
		ForeignKeyInfoBO fk = new ForeignKeyInfoBO();
		fk.setTable("orders");
		fk.setColumn("user_id");
		fk.setReferencedTable("users");
		fk.setReferencedColumn("id");

		Map<String, List<String>> map = schemaService.buildForeignKeyMap(List.of(fk));
		assertTrue(map.containsKey("orders"));
		assertTrue(map.containsKey("users"));
	}

	@Test
	void buildSchemaFromDocuments_basic() {
		List<Document> tableDocs = new ArrayList<>(List.of(createTableDoc("users")));
		List<Document> columnDocs = new ArrayList<>(List.of(createColumnDoc("users", "name")));
		SchemaDTO schemaDTO = new SchemaDTO();

		schemaService.buildSchemaFromDocuments("1", columnDocs, tableDocs, schemaDTO);

		assertNotNull(schemaDTO.getTable());
		assertFalse(schemaDTO.getTable().isEmpty());
		assertEquals("users", schemaDTO.getTable().get(0).getName());
	}

	@Test
	void getTableDocumentsByDatasource_delegates() {
		when(agentVectorStoreService.getDocumentsOnlyByFilter(any(), anyInt())).thenReturn(List.of());

		List<Document> result = schemaService.getTableDocumentsByDatasource(1, "test query");
		assertNotNull(result);
	}

	@Test
	void getTableDocuments_emptyTableNames() {
		List<Document> result = schemaService.getTableDocuments(1, Collections.emptyList());
		assertTrue(result.isEmpty());
	}

	@Test
	void getColumnDocumentsByTableName_emptyTableNames() {
		List<Document> result = schemaService.getColumnDocumentsByTableName(1, Collections.emptyList());
		assertTrue(result.isEmpty());
	}

	@Test
	void extractDatabaseName_mysql() {
		SchemaDTO schemaDTO = new SchemaDTO();
		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setUrl("jdbc:mysql://localhost:3306/mydb?useSSL=false");
		dbConfig.setDialectType("mysql");

		schemaService.extractDatabaseName(schemaDTO, dbConfig);
		assertEquals("mydb", schemaDTO.getName());
	}

	@Test
	void extractDatabaseName_postgresql() {
		SchemaDTO schemaDTO = new SchemaDTO();
		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setUrl("jdbc:postgresql://localhost:5432/mydb");
		dbConfig.setDialectType("postgresql");
		dbConfig.setSchema("public");

		schemaService.extractDatabaseName(schemaDTO, dbConfig);
		assertEquals("public", schemaDTO.getName());
	}

	@Test
	void extractDatabaseName_unknownDialect_doesNothing() {
		SchemaDTO schemaDTO = new SchemaDTO();
		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setUrl("jdbc:unknown://localhost:1234/db");
		dbConfig.setDialectType("unknown");

		schemaService.extractDatabaseName(schemaDTO, dbConfig);
		assertNull(schemaDTO.getName());
	}

	@Test
	void buildTableListFromDocuments_emptyList() {
		List<TableDTO> tables = schemaService.buildTableListFromDocuments(Collections.emptyList());
		assertTrue(tables.isEmpty());
	}

	@Test
	void buildTableListFromDocuments_noPrimaryKey() {
		Map<String, Object> meta = new HashMap<>();
		meta.put("name", "simple_table");
		meta.put("description", "A table");
		Document doc = new Document("table: simple_table", meta);

		List<TableDTO> tables = schemaService.buildTableListFromDocuments(List.of(doc));
		assertEquals(1, tables.size());
		assertNull(tables.get(0).getPrimaryKeys());
	}

	@Test
	void extractRelatedNamesFromForeignKeys_emptyList() {
		Set<String> names = schemaService.extractRelatedNamesFromForeignKeys(Collections.emptyList());
		assertTrue(names.isEmpty());
	}

	@Test
	void extractRelatedNamesFromForeignKeys_invalidFKFormat_ignored() {
		Map<String, Object> meta = new HashMap<>();
		meta.put("name", "bad");
		meta.put("description", "");
		meta.put("foreignKey", "invalid_no_equals_sign");
		Document doc = new Document("table: bad", meta);

		Set<String> names = schemaService.extractRelatedNamesFromForeignKeys(List.of(doc));
		assertTrue(names.isEmpty());
	}

	@Test
	void buildForeignKeyMap_sameTableBothSides_mergesEntries() {
		ForeignKeyInfoBO fk = new ForeignKeyInfoBO();
		fk.setTable("self_ref");
		fk.setColumn("parent_id");
		fk.setReferencedTable("self_ref");
		fk.setReferencedColumn("id");

		Map<String, List<String>> map = schemaService.buildForeignKeyMap(List.of(fk));
		assertEquals(1, map.size());
		assertTrue(map.containsKey("self_ref"));
		assertEquals(2, map.get("self_ref").size());
	}

	@Test
	void buildSchemaFromDocuments_withForeignKeys_collectsForeignKeys() {
		Map<String, Object> meta = new HashMap<>();
		meta.put("name", "orders");
		meta.put("description", "Orders table");
		meta.put("foreignKey", "orders.user_id=users.id");
		meta.put("datasourceId", "1");
		meta.put("vectorType", "TABLE");
		Document tableDoc = new Document("table: orders", meta);

		List<Document> tableDocs = new ArrayList<>(List.of(tableDoc));
		List<Document> columnDocs = new ArrayList<>();
		SchemaDTO schemaDTO = new SchemaDTO();

		schemaService.buildSchemaFromDocuments("1", columnDocs, tableDocs, schemaDTO);

		assertNotNull(schemaDTO.getForeignKeys());
		assertFalse(schemaDTO.getForeignKeys().isEmpty());
	}

	@Test
	void buildSchemaFromDocuments_emptyColumnDocs_tablesHaveNoColumns() {
		List<Document> tableDocs = new ArrayList<>(List.of(createTableDoc("t1")));
		List<Document> columnDocs = new ArrayList<>();
		SchemaDTO schemaDTO = new SchemaDTO();

		schemaService.buildSchemaFromDocuments("1", columnDocs, tableDocs, schemaDTO);

		assertEquals(1, schemaDTO.getTable().size());
		assertTrue(schemaDTO.getTable().get(0).getColumn().isEmpty());
	}

	@Test
	void getTableDocuments_nullDatasourceId_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> schemaService.getTableDocuments(null, List.of("t1")));
	}

	@Test
	void getColumnDocumentsByTableName_nullDatasourceId_throwsException() {
		assertThrows(IllegalArgumentException.class,
				() -> schemaService.getColumnDocumentsByTableName(null, List.of("t1")));
	}

	@Test
	void getTableDocumentsByDatasource_nullDatasourceId_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> schemaService.getTableDocumentsByDatasource(null, "query"));
	}

	@Test
	void storeSchemaDocuments_batchesAndStores() {
		Document colDoc = createColumnDoc("users", "name");
		Document tableDoc = createTableDoc("users");
		when(batchingStrategy.batch(anyList())).thenReturn(List.of(List.of(colDoc)), List.of(List.of(tableDoc)));

		schemaService.storeSchemaDocuments(1, List.of(colDoc), List.of(tableDoc));

		verify(agentVectorStoreService, times(2)).addDocuments(eq("1"), anyList());
	}

	@Test
	void extractDatabaseName_mysql_noMatch_doesNotSet() {
		SchemaDTO schemaDTO = new SchemaDTO();
		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setUrl("jdbc:mysql://localhost/noport");
		dbConfig.setDialectType("mysql");

		schemaService.extractDatabaseName(schemaDTO, dbConfig);
		assertNull(schemaDTO.getName());
	}

}
