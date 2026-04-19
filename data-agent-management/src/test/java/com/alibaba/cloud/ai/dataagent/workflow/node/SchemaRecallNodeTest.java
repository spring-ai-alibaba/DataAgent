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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchemaRecallNodeTest {

	@Mock
	private SchemaService schemaService;

	@Mock
	private AgentDatasourceMapper agentDatasourceMapper;

	private SchemaRecallNode schemaRecallNode;

	@BeforeEach
	void setUp() {
		schemaRecallNode = new SchemaRecallNode(schemaService, agentDatasourceMapper);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(AGENT_ID, new ReplaceStrategy());
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
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("查询用户"), AGENT_ID, "1"));

		when(agentDatasourceMapper.selectActiveDatasourceIdByAgentId(1L)).thenReturn(100);

		List<Document> tableDocs = List.of(createTableDocument("users"));
		when(schemaService.getTableDocumentsByDatasource(eq(100), anyString())).thenReturn(tableDocs);
		when(schemaService.getColumnDocumentsByTableName(eq(100), anyList()))
			.thenReturn(List.of(new Document("col doc")));

		Map<String, Object> result = schemaRecallNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(SCHEMA_RECALL_NODE_OUTPUT));
	}

	@Test
	void apply_noDatasource_returnsEmptySchemaGenerator() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("查询用户"), AGENT_ID, "2"));

		when(agentDatasourceMapper.selectActiveDatasourceIdByAgentId(2L)).thenReturn(null);

		Map<String, Object> result = schemaRecallNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(SCHEMA_RECALL_NODE_OUTPUT));
	}

	@Test
	void apply_emptyTableDocuments_returnsGeneratorWithEmptyTables() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("不存在的查询"), AGENT_ID, "3"));

		when(agentDatasourceMapper.selectActiveDatasourceIdByAgentId(3L)).thenReturn(200);
		when(schemaService.getTableDocumentsByDatasource(eq(200), anyString())).thenReturn(Collections.emptyList());
		when(schemaService.getColumnDocumentsByTableName(eq(200), anyList())).thenReturn(Collections.emptyList());

		Map<String, Object> result = schemaRecallNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(SCHEMA_RECALL_NODE_OUTPUT));
	}

	@Test
	void apply_multipleTables_returnsAllTableNames() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("查询用户和订单"), AGENT_ID, "4"));

		when(agentDatasourceMapper.selectActiveDatasourceIdByAgentId(4L)).thenReturn(300);

		List<Document> tableDocs = List.of(createTableDocument("users"), createTableDocument("orders"));
		when(schemaService.getTableDocumentsByDatasource(eq(300), anyString())).thenReturn(tableDocs);
		when(schemaService.getColumnDocumentsByTableName(eq(300), anyList()))
			.thenReturn(List.of(new Document("col1"), new Document("col2")));

		Map<String, Object> result = schemaRecallNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(SCHEMA_RECALL_NODE_OUTPUT));
	}

	@Test
	void apply_schemaServiceFailure_throwsException() {
		OverAllState state = createTestState();
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("查询失败"), AGENT_ID, "5"));

		when(agentDatasourceMapper.selectActiveDatasourceIdByAgentId(5L)).thenReturn(400);
		when(schemaService.getTableDocumentsByDatasource(eq(400), anyString()))
			.thenThrow(new RuntimeException("DB connection failed"));

		assertThrows(RuntimeException.class, () -> schemaRecallNode.apply(state));
	}

	@Test
	void apply_tableDocumentWithoutName_extractsOnlyValidNames() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("查询"), AGENT_ID, "6"));

		when(agentDatasourceMapper.selectActiveDatasourceIdByAgentId(6L)).thenReturn(500);

		Document validDoc = createTableDocument("users");
		Document noNameDoc = new Document("doc without name", Map.of("other", "value"));
		List<Document> tableDocs = new ArrayList<>(List.of(validDoc, noNameDoc));

		when(schemaService.getTableDocumentsByDatasource(eq(500), anyString())).thenReturn(tableDocs);
		when(schemaService.getColumnDocumentsByTableName(eq(500), eq(List.of("users"))))
			.thenReturn(Collections.emptyList());

		Map<String, Object> result = schemaRecallNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(SCHEMA_RECALL_NODE_OUTPUT));
	}

	@Test
	void apply_missingAgentId_throwsException() {
		OverAllState state = createTestState();
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, createQueryEnhanceDTO("查询")));

		assertThrows(IllegalStateException.class, () -> schemaRecallNode.apply(state));
	}

}
