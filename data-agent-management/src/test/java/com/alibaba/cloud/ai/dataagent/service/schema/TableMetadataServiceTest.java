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
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TableMetadataServiceTest {

	private TableMetadataService tableMetadataService;

	@Mock
	private AccessorFactory accessorFactory;

	@Mock
	private Accessor accessor;

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		tableMetadataService = new TableMetadataService(accessorFactory, objectMapper);
	}

	@Test
	void batchEnrichTableMetadata_singleTable_enrichesColumns() throws Exception {
		DbConfigBO dbConfig = DbConfigBO.builder().schema("public").dialectType("mysql").build();

		TableInfoBO table = TableInfoBO.builder().name("users").build();
		List<TableInfoBO> tables = List.of(table);

		ColumnInfoBO col1 = ColumnInfoBO.builder().name("id").type("INT").primary(true).build();
		ColumnInfoBO col2 = ColumnInfoBO.builder().name("name").type("VARCHAR").primary(false).build();

		when(accessorFactory.getAccessorByDbConfig(dbConfig)).thenReturn(accessor);
		when(accessor.showColumns(eq(dbConfig), any())).thenReturn(new ArrayList<>(List.of(col1, col2)));

		Map<String, String> row1 = new HashMap<>();
		row1.put("id", "1");
		row1.put("name", "Alice");
		Map<String, String> row2 = new HashMap<>();
		row2.put("id", "2");
		row2.put("name", "Bob");
		ResultSetBO resultSet = ResultSetBO.builder().column(List.of("id", "name")).data(List.of(row1, row2)).build();
		when(accessor.executeSqlAndReturnObject(eq(dbConfig), any())).thenReturn(resultSet);

		Map<String, List<String>> foreignKeyMap = new HashMap<>();

		tableMetadataService.batchEnrichTableMetadata(tables, dbConfig, foreignKeyMap);

		assertNotNull(table.getColumns());
		assertEquals(2, table.getColumns().size());
		assertEquals(List.of("id"), table.getPrimaryKeys());
		assertEquals("users", table.getColumns().get(0).getTableName());
	}

	@Test
	void batchEnrichTableMetadata_withForeignKeys_setsForeignKeyString() throws Exception {
		DbConfigBO dbConfig = DbConfigBO.builder().schema("public").dialectType("mysql").build();

		TableInfoBO table = TableInfoBO.builder().name("orders").build();
		List<TableInfoBO> tables = List.of(table);

		ColumnInfoBO col = ColumnInfoBO.builder().name("user_id").type("INT").primary(false).build();

		when(accessorFactory.getAccessorByDbConfig(dbConfig)).thenReturn(accessor);
		when(accessor.showColumns(eq(dbConfig), any())).thenReturn(new ArrayList<>(List.of(col)));
		when(accessor.executeSqlAndReturnObject(eq(dbConfig), any()))
			.thenReturn(ResultSetBO.builder().column(List.of("user_id")).data(List.of()).build());

		Map<String, List<String>> foreignKeyMap = new HashMap<>();
		foreignKeyMap.put("orders", List.of("user_id -> users.id"));

		tableMetadataService.batchEnrichTableMetadata(tables, dbConfig, foreignKeyMap);

		assertEquals("user_id -> users.id", table.getForeignKey());
	}

	@Test
	void batchEnrichTableMetadata_noPrimaryKeys_setsEmptyList() throws Exception {
		DbConfigBO dbConfig = DbConfigBO.builder().schema("public").dialectType("mysql").build();

		TableInfoBO table = TableInfoBO.builder().name("logs").build();
		List<TableInfoBO> tables = List.of(table);

		ColumnInfoBO col = ColumnInfoBO.builder().name("message").type("TEXT").primary(false).build();

		when(accessorFactory.getAccessorByDbConfig(dbConfig)).thenReturn(accessor);
		when(accessor.showColumns(eq(dbConfig), any())).thenReturn(new ArrayList<>(List.of(col)));
		when(accessor.executeSqlAndReturnObject(eq(dbConfig), any()))
			.thenReturn(ResultSetBO.builder().column(List.of("message")).data(List.of()).build());

		tableMetadataService.batchEnrichTableMetadata(tables, dbConfig, new HashMap<>());

		assertNotNull(table.getPrimaryKeys());
		assertTrue(table.getPrimaryKeys().isEmpty());
	}

	@Test
	void batchEnrichTableMetadata_emptyColumns_setsEmptySampleData() throws Exception {
		DbConfigBO dbConfig = DbConfigBO.builder().schema("public").dialectType("mysql").build();

		TableInfoBO table = TableInfoBO.builder().name("empty_table").build();
		List<TableInfoBO> tables = List.of(table);

		when(accessorFactory.getAccessorByDbConfig(dbConfig)).thenReturn(accessor);
		when(accessor.showColumns(eq(dbConfig), any())).thenReturn(new ArrayList<>());

		tableMetadataService.batchEnrichTableMetadata(tables, dbConfig, new HashMap<>());

		assertNotNull(table.getColumns());
		assertTrue(table.getColumns().isEmpty());
	}

	@Test
	void batchEnrichTableMetadata_sampleDataFetchFails_usesEmptyMap() throws Exception {
		DbConfigBO dbConfig = DbConfigBO.builder().schema("public").dialectType("mysql").build();

		TableInfoBO table = TableInfoBO.builder().name("failing_table").build();
		List<TableInfoBO> tables = List.of(table);

		ColumnInfoBO col = ColumnInfoBO.builder().name("id").type("INT").primary(true).build();

		when(accessorFactory.getAccessorByDbConfig(dbConfig)).thenReturn(accessor);
		when(accessor.showColumns(eq(dbConfig), any())).thenReturn(new ArrayList<>(List.of(col)));
		when(accessor.executeSqlAndReturnObject(eq(dbConfig), any())).thenThrow(new RuntimeException("DB error"));

		tableMetadataService.batchEnrichTableMetadata(tables, dbConfig, new HashMap<>());

		assertNotNull(table.getColumns());
		assertEquals("[]", table.getColumns().get(0).getSamples());
	}

	@Test
	void batchEnrichTableMetadata_nullResultSet_handlesGracefully() throws Exception {
		DbConfigBO dbConfig = DbConfigBO.builder().schema("public").dialectType("mysql").build();

		TableInfoBO table = TableInfoBO.builder().name("null_result").build();
		List<TableInfoBO> tables = List.of(table);

		ColumnInfoBO col = ColumnInfoBO.builder().name("val").type("VARCHAR").primary(false).build();

		when(accessorFactory.getAccessorByDbConfig(dbConfig)).thenReturn(accessor);
		when(accessor.showColumns(eq(dbConfig), any())).thenReturn(new ArrayList<>(List.of(col)));
		when(accessor.executeSqlAndReturnObject(eq(dbConfig), any()))
			.thenReturn(ResultSetBO.builder().column(null).data(null).build());

		tableMetadataService.batchEnrichTableMetadata(tables, dbConfig, new HashMap<>());

		assertNotNull(table.getColumns());
	}

	@Test
	void batchEnrichTableMetadata_multipleTables_enrichesAll() throws Exception {
		DbConfigBO dbConfig = DbConfigBO.builder().schema("public").dialectType("mysql").build();

		TableInfoBO table1 = TableInfoBO.builder().name("users").build();
		TableInfoBO table2 = TableInfoBO.builder().name("orders").build();
		List<TableInfoBO> tables = List.of(table1, table2);

		ColumnInfoBO col1 = ColumnInfoBO.builder().name("id").type("INT").primary(true).build();
		ColumnInfoBO col2 = ColumnInfoBO.builder().name("order_id").type("INT").primary(true).build();

		when(accessorFactory.getAccessorByDbConfig(dbConfig)).thenReturn(accessor);
		when(accessor.showColumns(eq(dbConfig), any())).thenReturn(new ArrayList<>(List.of(col1)))
			.thenReturn(new ArrayList<>(List.of(col2)));
		when(accessor.executeSqlAndReturnObject(eq(dbConfig), any()))
			.thenReturn(ResultSetBO.builder().column(List.of("id")).data(List.of()).build())
			.thenReturn(ResultSetBO.builder().column(List.of("order_id")).data(List.of()).build());

		tableMetadataService.batchEnrichTableMetadata(tables, dbConfig, new HashMap<>());

		assertNotNull(table1.getColumns());
		assertNotNull(table2.getColumns());
	}

	@Test
	void batchEnrichTableMetadata_sampleDataDeduplicatesAndLimits() throws Exception {
		DbConfigBO dbConfig = DbConfigBO.builder().schema("public").dialectType("mysql").build();

		TableInfoBO table = TableInfoBO.builder().name("data_table").build();
		List<TableInfoBO> tables = List.of(table);

		ColumnInfoBO col = ColumnInfoBO.builder().name("status").type("VARCHAR").primary(false).build();

		when(accessorFactory.getAccessorByDbConfig(dbConfig)).thenReturn(accessor);
		when(accessor.showColumns(eq(dbConfig), any())).thenReturn(new ArrayList<>(List.of(col)));

		Map<String, String> row1 = new HashMap<>();
		row1.put("status", "active");
		Map<String, String> row2 = new HashMap<>();
		row2.put("status", "active");
		Map<String, String> row3 = new HashMap<>();
		row3.put("status", "inactive");
		Map<String, String> row4 = new HashMap<>();
		row4.put("status", "pending");
		Map<String, String> row5 = new HashMap<>();
		row5.put("status", "archived");
		ResultSetBO resultSet = ResultSetBO.builder()
			.column(List.of("status"))
			.data(List.of(row1, row2, row3, row4, row5))
			.build();
		when(accessor.executeSqlAndReturnObject(eq(dbConfig), any())).thenReturn(resultSet);

		tableMetadataService.batchEnrichTableMetadata(tables, dbConfig, new HashMap<>());

		String samples = table.getColumns().get(0).getSamples();
		assertNotNull(samples);
		assertFalse(samples.equals("[]"));
	}

	@Test
	void batchEnrichTableMetadata_noForeignKeyForTable_setsEmptyString() throws Exception {
		DbConfigBO dbConfig = DbConfigBO.builder().schema("public").dialectType("mysql").build();

		TableInfoBO table = TableInfoBO.builder().name("standalone").build();
		List<TableInfoBO> tables = List.of(table);

		ColumnInfoBO col = ColumnInfoBO.builder().name("id").type("INT").primary(true).build();

		when(accessorFactory.getAccessorByDbConfig(dbConfig)).thenReturn(accessor);
		when(accessor.showColumns(eq(dbConfig), any())).thenReturn(new ArrayList<>(List.of(col)));
		when(accessor.executeSqlAndReturnObject(eq(dbConfig), any()))
			.thenReturn(ResultSetBO.builder().column(List.of("id")).data(List.of()).build());

		Map<String, List<String>> foreignKeyMap = new HashMap<>();

		tableMetadataService.batchEnrichTableMetadata(tables, dbConfig, foreignKeyMap);

		assertEquals("", table.getForeignKey());
	}

	@Test
	void batchEnrichTableMetadata_multipleForeignKeys_joinsWithSeparator() throws Exception {
		DbConfigBO dbConfig = DbConfigBO.builder().schema("public").dialectType("mysql").build();

		TableInfoBO table = TableInfoBO.builder().name("order_items").build();
		List<TableInfoBO> tables = List.of(table);

		ColumnInfoBO col = ColumnInfoBO.builder().name("id").type("INT").primary(true).build();

		when(accessorFactory.getAccessorByDbConfig(dbConfig)).thenReturn(accessor);
		when(accessor.showColumns(eq(dbConfig), any())).thenReturn(new ArrayList<>(List.of(col)));
		when(accessor.executeSqlAndReturnObject(eq(dbConfig), any()))
			.thenReturn(ResultSetBO.builder().column(List.of("id")).data(List.of()).build());

		Map<String, List<String>> foreignKeyMap = new HashMap<>();
		foreignKeyMap.put("order_items", List.of("order_id -> orders.id", "product_id -> products.id"));

		tableMetadataService.batchEnrichTableMetadata(tables, dbConfig, foreignKeyMap);

		assertTrue(table.getForeignKey().contains("order_id -> orders.id"));
		assertTrue(table.getForeignKey().contains("product_id -> products.id"));
	}

}
