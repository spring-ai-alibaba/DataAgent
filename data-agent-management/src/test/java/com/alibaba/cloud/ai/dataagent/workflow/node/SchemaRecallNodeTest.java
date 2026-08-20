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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.execute;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeExecution;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

@ExtendWith(MockitoExtension.class)
class SchemaRecallNodeTest {

	@Mock
	private SchemaService schemaService;

	private SchemaRecallNode schemaRecallNode;

	@BeforeEach
	void setUp() {
		schemaRecallNode = new SchemaRecallNode(schemaService);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(AGENT_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(DATASOURCE_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(SCHEMA_RECALL_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, new ReplaceStrategy());
		return state;
	}

	private QueryEnhanceOutputDTO createQueryEnhanceDTO(String query) {
		return TestFixtures.createQueryEnhanceDTO(query);
	}

	private Document createTableDocument(String tableName) {
		return new Document("table doc", Map.of("name", tableName));
	}

	@Test
	void apply_withDatasource_returnsSchemaRecallOutput() throws Exception {
		OverAllState state = createTestState();
		state.updateState(
				Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("查询用户"), AGENT_ID, "1", DATASOURCE_ID, 100));

		List<Document> tableDocs = List.of(createTableDocument("users"));
		List<Document> columnDocs = List.of(new Document("col doc"));
		when(schemaService.getTableDocumentsByDatasource(eq(100), anyString())).thenReturn(tableDocs);
		when(schemaService.getColumnDocumentsByTableName(eq(100), anyList())).thenReturn(columnDocs);

		NodeExecution execution = execute(schemaRecallNode.apply(state), SCHEMA_RECALL_NODE_OUTPUT);

		assertEquals(tableDocs, execution.finalResult().get(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT));
		assertEquals(columnDocs, execution.finalResult().get(COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT));
		assertTrue(execution.streamedText().contains("数量: 1，表名: users"));
	}

	@Test
	void apply_noDatasource_returnsEmptySchemaGenerator() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("查询用户"), AGENT_ID, "2"));

		NodeExecution execution = execute(schemaRecallNode.apply(state), SCHEMA_RECALL_NODE_OUTPUT);

		assertEquals(Collections.emptyList(), execution.finalResult().get(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT));
		assertEquals(Collections.emptyList(), execution.finalResult().get(COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT));
		assertTrue(execution.streamedText().contains("该智能体没有激活的数据源"));
	}

	@Test
	void apply_emptyTableDocuments_returnsGeneratorWithEmptyTables() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("不存在的查询"), AGENT_ID, "3",
				DATASOURCE_ID, 200));
		when(schemaService.getTableDocumentsByDatasource(eq(200), anyString())).thenReturn(Collections.emptyList());
		when(schemaService.getColumnDocumentsByTableName(eq(200), anyList())).thenReturn(Collections.emptyList());

		NodeExecution execution = execute(schemaRecallNode.apply(state), SCHEMA_RECALL_NODE_OUTPUT);

		assertEquals(Collections.emptyList(), execution.finalResult().get(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT));
		assertEquals(Collections.emptyList(), execution.finalResult().get(COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT));
		assertTrue(execution.streamedText().contains("未检索到相关数据表"));
	}

	@Test
	void apply_multipleTables_returnsAllTableNames() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("查询用户和订单"), AGENT_ID, "4",
				DATASOURCE_ID, 300));

		List<Document> tableDocs = List.of(createTableDocument("users"), createTableDocument("orders"));
		when(schemaService.getTableDocumentsByDatasource(eq(300), anyString())).thenReturn(tableDocs);
		when(schemaService.getColumnDocumentsByTableName(eq(300), anyList()))
			.thenReturn(List.of(new Document("col1"), new Document("col2")));

		NodeExecution execution = execute(schemaRecallNode.apply(state), SCHEMA_RECALL_NODE_OUTPUT);

		assertEquals(tableDocs, execution.finalResult().get(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT));
		assertEquals(2, ((List<?>) execution.finalResult().get(COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT)).size());
		verify(schemaService).getColumnDocumentsByTableName(300, List.of("users", "orders"));
	}

	@Test
	void apply_schemaServiceFailure_throwsException() {
		OverAllState state = createTestState();
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("查询失败"), AGENT_ID, "5",
				DATASOURCE_ID, 400));
		when(schemaService.getTableDocumentsByDatasource(eq(400), anyString()))
			.thenThrow(new RuntimeException("DB connection failed"));

		RuntimeException exception = assertThrowsExactly(RuntimeException.class, () -> schemaRecallNode.apply(state));
		assertEquals("DB connection failed", exception.getMessage());
	}

	@Test
	void apply_tableDocumentWithoutName_extractsOnlyValidNames() throws Exception {
		OverAllState state = createTestState();
		state.updateState(
				Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("查询"), AGENT_ID, "6", DATASOURCE_ID, 500));

		Document validDoc = createTableDocument("users");
		Document noNameDoc = new Document("doc without name", Map.of("other", "value"));
		List<Document> tableDocs = new ArrayList<>(List.of(validDoc, noNameDoc));

		when(schemaService.getTableDocumentsByDatasource(eq(500), anyString())).thenReturn(tableDocs);
		when(schemaService.getColumnDocumentsByTableName(eq(500), eq(List.of("users"))))
			.thenReturn(Collections.emptyList());

		NodeExecution execution = execute(schemaRecallNode.apply(state), SCHEMA_RECALL_NODE_OUTPUT);

		assertEquals(tableDocs, execution.finalResult().get(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT));
		assertEquals(Collections.emptyList(), execution.finalResult().get(COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT));
		verify(schemaService).getColumnDocumentsByTableName(500, List.of("users"));
	}

	@Test
	void apply_missingAgentId_throwsException() {
		OverAllState state = createTestState();
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("查询")));

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> schemaRecallNode.apply(state));
		assertEquals("State key not found: " + AGENT_ID, exception.getMessage());
	}

}
