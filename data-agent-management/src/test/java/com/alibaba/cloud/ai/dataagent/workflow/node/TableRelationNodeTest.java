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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticModelService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
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
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableRelationNodeTest {

	@Mock
	private SchemaService schemaService;

	@Mock
	private Nl2SqlService nl2SqlService;

	@Mock
	private SemanticModelService semanticModelService;

	@Mock
	private DatabaseUtil databaseUtil;

	@Mock
	private DatasourceService datasourceService;

	@Mock
	private AgentDatasourceService agentDatasourceService;

	private TableRelationNode tableRelationNode;

	@BeforeEach
	void setUp() {
		tableRelationNode = new TableRelationNode(schemaService, nl2SqlService, semanticModelService, databaseUtil,
				datasourceService, agentDatasourceService);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(EVIDENCE, new ReplaceStrategy());
		state.registerKeyAndStrategy(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(AGENT_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(TABLE_RELATION_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(DB_DIALECT_TYPE, new ReplaceStrategy());
		state.registerKeyAndStrategy(TABLE_RELATION_RETRY_COUNT, new ReplaceStrategy());
		state.registerKeyAndStrategy(TABLE_RELATION_EXCEPTION_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_GENERATE_SCHEMA_MISSING_ADVICE, new ReplaceStrategy());
		state.registerKeyAndStrategy(GENEGRATED_SEMANTIC_MODEL_PROMPT, new ReplaceStrategy());
		return state;
	}

	private void setupCommonMocks(String agentId, List<Document> tableDocs) throws Exception {
		DbConfigBO dbConfig = DbConfigBO.builder().dialectType("mysql").schema("test_db").build();
		when(databaseUtil.getAgentDbConfig(Long.valueOf(agentId))).thenReturn(dbConfig);

		AgentDatasource agentDs = new AgentDatasource();
		agentDs.setDatasourceId(100);
		when(agentDatasourceService.getCurrentAgentDatasource(Long.valueOf(agentId))).thenReturn(agentDs);
		when(datasourceService.getLogicalRelations(100)).thenReturn(Collections.emptyList());
		when(semanticModelService.getByAgentIdAndTableNames(eq(Long.valueOf(agentId)), anyList()))
			.thenReturn(Collections.emptyList());

		SchemaDTO schemaResult = new SchemaDTO();
		schemaResult.setName("test_db");
		schemaResult.setTable(new ArrayList<>());
		schemaResult.setForeignKeys(new ArrayList<>());

		when(nl2SqlService.fineSelect(any(SchemaDTO.class), anyString(), anyString(), isNull(), any(DbConfigBO.class),
				any()))
			.thenAnswer(invocation -> {
				java.util.function.Consumer<SchemaDTO> consumer = invocation.getArgument(5);
				consumer.accept(schemaResult);
				return Flux.just(ChatResponseUtil.createPureResponse("schema selected"));
			});
	}

	private Document createTableDocument(String tableName) {
		return new Document("table doc", Map.of("name", tableName));
	}

	@Test
	void apply_validSchema_returnsTableRelationOutput() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询用户");
		List<Document> tableDocs = List.of(createTableDocument("users"));
		List<Document> colDocs = List.of(new Document("column doc"));

		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, EVIDENCE, "evidence", AGENT_ID, "1",
				TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, tableDocs, COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, colDocs));

		setupCommonMocks("1", tableDocs);

		Map<String, Object> result = tableRelationNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(TABLE_RELATION_OUTPUT));
		assertTrue(result.containsKey(DB_DIALECT_TYPE));
		assertEquals("mysql", result.get(DB_DIALECT_TYPE));
		assertEquals(0, result.get(TABLE_RELATION_RETRY_COUNT));
		assertEquals("", result.get(TABLE_RELATION_EXCEPTION_OUTPUT));
	}

	@Test
	void apply_emptyTableDocuments_returnsResultWithDefaults() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询");
		List<Document> emptyTableDocs = Collections.emptyList();
		List<Document> emptyColDocs = Collections.emptyList();

		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, EVIDENCE, "evidence", AGENT_ID, "2",
				TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, emptyTableDocs, COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, emptyColDocs));

		setupCommonMocks("2", emptyTableDocs);

		Map<String, Object> result = tableRelationNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(TABLE_RELATION_OUTPUT));
		assertEquals(0, result.get(TABLE_RELATION_RETRY_COUNT));
	}

	@Test
	void apply_emptyColumnDocuments_returnsMinimalSchema() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询用户");
		List<Document> tableDocs = List.of(createTableDocument("users"));

		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, EVIDENCE, "evidence", AGENT_ID, "3",
				TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, tableDocs, COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT,
				Collections.emptyList()));

		setupCommonMocks("3", tableDocs);

		Map<String, Object> result = tableRelationNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(TABLE_RELATION_OUTPUT));
	}

	@Test
	void apply_databaseConfigMissing_throwsException() {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询");
		List<Document> tableDocs = List.of(createTableDocument("users"));

		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, EVIDENCE, "evidence", AGENT_ID, "4",
				TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, tableDocs, COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT,
				Collections.emptyList()));

		when(databaseUtil.getAgentDbConfig(4L)).thenThrow(new RuntimeException("DB config not found"));

		assertThrows(RuntimeException.class, () -> tableRelationNode.apply(state));
	}

	@Test
	void apply_noActiveDatasource_returnsEmptyForeignKeys() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询用户");
		List<Document> tableDocs = List.of(createTableDocument("users"));

		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, EVIDENCE, "evidence", AGENT_ID, "5",
				TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, tableDocs, COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT,
				Collections.emptyList()));

		DbConfigBO dbConfig = DbConfigBO.builder().dialectType("mysql").schema("test_db").build();
		when(databaseUtil.getAgentDbConfig(5L)).thenReturn(dbConfig);
		when(agentDatasourceService.getCurrentAgentDatasource(5L)).thenReturn(null);
		when(semanticModelService.getByAgentIdAndTableNames(eq(5L), anyList())).thenReturn(Collections.emptyList());

		SchemaDTO schemaResult = new SchemaDTO();
		schemaResult.setName("test_db");
		schemaResult.setTable(new ArrayList<>());
		schemaResult.setForeignKeys(new ArrayList<>());

		when(nl2SqlService.fineSelect(any(SchemaDTO.class), anyString(), anyString(), isNull(), any(DbConfigBO.class),
				any()))
			.thenAnswer(invocation -> {
				java.util.function.Consumer<SchemaDTO> consumer = invocation.getArgument(5);
				consumer.accept(schemaResult);
				return Flux.just(ChatResponseUtil.createPureResponse("done"));
			});

		Map<String, Object> result = tableRelationNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(TABLE_RELATION_OUTPUT));
	}

	@Test
	void apply_withSchemaAdvice_passesAdviceToFineSelect() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询用户");
		List<Document> tableDocs = List.of(createTableDocument("users"));

		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, EVIDENCE, "evidence", AGENT_ID, "6",
				TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, tableDocs, COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT,
				Collections.emptyList(), SQL_GENERATE_SCHEMA_MISSING_ADVICE, "add orders table"));

		DbConfigBO dbConfig = DbConfigBO.builder().dialectType("mysql").schema("test_db").build();
		when(databaseUtil.getAgentDbConfig(6L)).thenReturn(dbConfig);

		AgentDatasource agentDs = new AgentDatasource();
		agentDs.setDatasourceId(600);
		when(agentDatasourceService.getCurrentAgentDatasource(6L)).thenReturn(agentDs);
		when(datasourceService.getLogicalRelations(600)).thenReturn(Collections.emptyList());
		when(semanticModelService.getByAgentIdAndTableNames(eq(6L), anyList())).thenReturn(Collections.emptyList());

		SchemaDTO schemaResult = new SchemaDTO();
		schemaResult.setName("test_db");
		schemaResult.setTable(new ArrayList<>());
		schemaResult.setForeignKeys(new ArrayList<>());

		when(nl2SqlService.fineSelect(any(SchemaDTO.class), anyString(), anyString(), eq("add orders table"),
				any(DbConfigBO.class), any()))
			.thenAnswer(invocation -> {
				java.util.function.Consumer<SchemaDTO> consumer = invocation.getArgument(5);
				consumer.accept(schemaResult);
				return Flux.just(ChatResponseUtil.createPureResponse("done"));
			});

		Map<String, Object> result = tableRelationNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(TABLE_RELATION_OUTPUT));
	}

}
