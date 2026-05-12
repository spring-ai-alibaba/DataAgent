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
package com.alibaba.cloud.ai.dataagent.prompt;

import com.alibaba.cloud.ai.dataagent.dto.schema.ColumnDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class PromptHelperTest {

	@AfterEach
	void tearDown() {
		PromptLoader.clearCache();
	}

	private SchemaDTO createTestSchema() {
		SchemaDTO schema = new SchemaDTO();
		schema.setName("test_db");

		TableDTO table = new TableDTO();
		table.setName("users");
		table.setDescription("User table");
		table.setPrimaryKeys(Arrays.asList("id"));

		ColumnDTO col1 = new ColumnDTO();
		col1.setName("id");
		col1.setType("bigint");
		col1.setDescription("Primary key");

		ColumnDTO col2 = new ColumnDTO();
		col2.setName("name");
		col2.setType("varchar");
		col2.setDescription("User name");
		col2.setData(Arrays.asList("Alice", "Bob", "Charlie"));

		table.setColumn(Arrays.asList(col1, col2));
		schema.setTable(Arrays.asList(table));

		return schema;
	}

	@Test
	void buildMixMacSqlDbPrompt_withColumnType_includesTypeInfo() {
		SchemaDTO schema = createTestSchema();
		String result = PromptHelper.buildMixMacSqlDbPrompt(schema, true);

		assertNotNull(result);
		assertTrue(result.contains("test_db"));
		assertTrue(result.contains("users"));
		assertTrue(result.contains("BIGINT"));
	}

	@Test
	void buildMixMacSqlDbPrompt_withoutColumnType_excludesTypeInfo() {
		SchemaDTO schema = createTestSchema();
		String result = PromptHelper.buildMixMacSqlDbPrompt(schema, false);

		assertNotNull(result);
		assertTrue(result.contains("users"));
		assertFalse(result.contains("BIGINT"));
	}

	@Test
	void buildMixMacSqlDbPrompt_nullSchemaName_handlesGracefully() {
		SchemaDTO schema = createTestSchema();
		schema.setName(null);
		String result = PromptHelper.buildMixMacSqlDbPrompt(schema, true);

		assertNotNull(result);
		assertTrue(result.contains("DB_ID"));
	}

	@Test
	void buildMixMacSqlDbPrompt_withForeignKeys_includesForeignKeys() {
		SchemaDTO schema = createTestSchema();
		schema.setForeignKeys(Arrays.asList("users.id = orders.user_id"));
		String result = PromptHelper.buildMixMacSqlDbPrompt(schema, true);

		assertTrue(result.contains("Foreign keys"));
		assertTrue(result.contains("users.id = orders.user_id"));
	}

	@Test
	void buildMixMacSqlTablePrompt_primaryKeyColumn_showsPrimaryKey() {
		TableDTO table = new TableDTO();
		table.setName("users");
		table.setDescription("User table");
		table.setPrimaryKeys(Arrays.asList("id"));

		ColumnDTO col = new ColumnDTO();
		col.setName("id");
		col.setType("bigint");
		col.setDescription("Primary key");
		table.setColumn(Arrays.asList(col));

		String result = PromptHelper.buildMixMacSqlTablePrompt(table, true);

		assertTrue(result.contains("Primary Key"));
	}

	@Test
	void buildMixMacSqlTablePrompt_sameNameAndDescription_omitsDescription() {
		TableDTO table = new TableDTO();
		table.setName("users");
		table.setDescription("users");

		ColumnDTO col = new ColumnDTO();
		col.setName("id");
		col.setType("bigint");
		col.setDescription("id");
		table.setColumn(Arrays.asList(col));

		String result = PromptHelper.buildMixMacSqlTablePrompt(table, true);

		assertNotNull(result);
		assertTrue(result.contains("# Table: users"));
	}

	@Test
	void buildMixMacSqlTablePrompt_columnWithEnumData_showsExamples() {
		TableDTO table = new TableDTO();
		table.setName("users");
		table.setDescription("User table");

		ColumnDTO col = new ColumnDTO();
		col.setName("status");
		col.setType("varchar");
		col.setDescription("Status");
		col.setData(Arrays.asList("active", "inactive", "pending", "deleted"));
		table.setColumn(Arrays.asList(col));

		String result = PromptHelper.buildMixMacSqlTablePrompt(table, true);

		assertTrue(result.contains("Examples:"));
		assertTrue(result.contains("active"));
	}

	@Test
	void buildMixMacSqlTablePrompt_idColumnWithData_omitsExamples() {
		TableDTO table = new TableDTO();
		table.setName("users");
		table.setDescription("User table");

		ColumnDTO col = new ColumnDTO();
		col.setName("id");
		col.setType("bigint");
		col.setDescription("Primary key");
		col.setData(Arrays.asList("1", "2", "3"));
		table.setColumn(Arrays.asList(col));

		String result = PromptHelper.buildMixMacSqlTablePrompt(table, true);

		assertFalse(result.contains("Examples:"));
	}

	@Test
	void buildMixMacSqlTablePrompt_columnWithEmptyData_omitsExamples() {
		TableDTO table = new TableDTO();
		table.setName("users");
		table.setDescription("User table");

		ColumnDTO col = new ColumnDTO();
		col.setName("status");
		col.setType("varchar");
		col.setDescription("Status");
		col.setData(Arrays.asList("", ""));
		table.setColumn(Arrays.asList(col));

		String result = PromptHelper.buildMixMacSqlTablePrompt(table, true);

		assertFalse(result.contains("Examples:"));
	}

	@Test
	void buildBusinessKnowledgePrompt_withContent_includesBusinessTerms() {
		String result = PromptHelper.buildBusinessKnowledgePrompt("GMV means gross merchandise value");
		assertNotNull(result);
		assertTrue(result.contains("GMV means gross merchandise value"));
	}

	@Test
	void buildBusinessKnowledgePrompt_blankContent_usesDefault() {
		String result = PromptHelper.buildBusinessKnowledgePrompt("");
		assertNotNull(result);
	}

	@Test
	void buildBusinessKnowledgePrompt_nullContent_usesDefault() {
		String result = PromptHelper.buildBusinessKnowledgePrompt(null);
		assertNotNull(result);
	}

	@Test
	void buildAgentKnowledgePrompt_withContent_includesKnowledge() {
		String result = PromptHelper.buildAgentKnowledgePrompt("This agent analyses sales data");
		assertNotNull(result);
		assertTrue(result.contains("This agent analyses sales data"));
	}

	@Test
	void buildAgentKnowledgePrompt_blankContent_usesDefault() {
		String result = PromptHelper.buildAgentKnowledgePrompt("");
		assertNotNull(result);
	}

	@Test
	void buildAgentKnowledgePrompt_nullContent_usesDefault() {
		String result = PromptHelper.buildAgentKnowledgePrompt(null);
		assertNotNull(result);
	}

	@Test
	void buildSemanticModelPrompt_withModels_includesModelInfo() {
		SemanticModel model = SemanticModel.builder()
			.tableName("users")
			.columnName("name")
			.businessName("User Name")
			.build();

		String result = PromptHelper.buildSemanticModelPrompt(Arrays.asList(model));
		assertNotNull(result);
	}

	@Test
	void buildSemanticModelPrompt_emptyModels_handlesGracefully() {
		String result = PromptHelper.buildSemanticModelPrompt(Collections.emptyList());
		assertNotNull(result);
	}

	@Test
	void buildSemanticModelPrompt_nullModels_handlesGracefully() {
		String result = PromptHelper.buildSemanticModelPrompt(null);
		assertNotNull(result);
	}

	@Test
	void buildIntentRecognitionPrompt_withMultiTurn_includesHistory() {
		String result = PromptHelper.buildIntentRecognitionPrompt("previous context", "What is the total sales?");
		assertNotNull(result);
		assertTrue(result.contains("previous context"));
		assertTrue(result.contains("What is the total sales?"));
	}

	@Test
	void buildIntentRecognitionPrompt_nullMultiTurn_usesDefault() {
		String result = PromptHelper.buildIntentRecognitionPrompt(null, "What is the total sales?");
		assertNotNull(result);
	}

	@Test
	void buildQueryEnhancePrompt_withEvidence_includesEvidence() {
		String result = PromptHelper.buildQueryEnhancePrompt("history", "latest query", "some evidence");
		assertNotNull(result);
		assertTrue(result.contains("some evidence"));
	}

	@Test
	void buildQueryEnhancePrompt_emptyEvidence_usesDefault() {
		String result = PromptHelper.buildQueryEnhancePrompt("history", "latest query", "");
		assertNotNull(result);
	}

	@Test
	void buildQueryEnhancePrompt_nullMultiTurn_usesDefault() {
		String result = PromptHelper.buildQueryEnhancePrompt(null, "latest query", "evidence");
		assertNotNull(result);
	}

	@Test
	void buildEvidenceQueryRewritePrompt_withMultiTurn_includesHistory() {
		String result = PromptHelper.buildEvidenceQueryRewritePrompt("history", "latest query");
		assertNotNull(result);
	}

	@Test
	void buildEvidenceQueryRewritePrompt_nullMultiTurn_usesDefault() {
		String result = PromptHelper.buildEvidenceQueryRewritePrompt(null, "latest query");
		assertNotNull(result);
	}

	@Test
	void buildFeasibilityAssessmentPrompt_withAllParams_buildsPrompt() {
		SchemaDTO schema = createTestSchema();
		String result = PromptHelper.buildFeasibilityAssessmentPrompt("query", schema, "evidence", "history");
		assertNotNull(result);
	}

	@Test
	void buildFeasibilityAssessmentPrompt_nullParams_handlesGracefully() {
		SchemaDTO schema = createTestSchema();
		String result = PromptHelper.buildFeasibilityAssessmentPrompt(null, schema, null, null);
		assertNotNull(result);
	}

	@Test
	void buildReportGeneratorPromptWithOptimization_noOptimizations_buildsPrompt() {
		String result = PromptHelper.buildReportGeneratorPromptWithOptimization("requirements", "steps", "summary",
				null);
		assertNotNull(result);
	}

	@Test
	void buildReportGeneratorPromptWithOptimization_emptyOptimizations_buildsPrompt() {
		String result = PromptHelper.buildReportGeneratorPromptWithOptimization("requirements", "steps", "summary",
				new ArrayList<>());
		assertNotNull(result);
	}

	@Test
	void buildDataViewAnalysisPrompt_returnsNonNull() {
		String result = PromptHelper.buildDataViewAnalysisPrompt();
		assertNotNull(result);
	}

}
