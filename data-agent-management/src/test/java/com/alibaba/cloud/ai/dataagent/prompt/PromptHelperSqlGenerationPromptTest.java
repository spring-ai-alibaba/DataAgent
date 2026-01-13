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

package com.alibaba.cloud.ai.dataagent.prompt;

import com.alibaba.cloud.ai.dataagent.dto.prompt.SqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.ColumnDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptHelperSqlGenerationPromptTest {

	private static SchemaDTO buildMinimalSchema() {
		ColumnDTO idColumn = new ColumnDTO();
		idColumn.setName("id");
		idColumn.setDescription("id");
		idColumn.setType("int");

		TableDTO tableDTO = new TableDTO();
		tableDTO.setName("t_user");
		tableDTO.setDescription("t_user");
		tableDTO.setColumn(List.of(idColumn));

		SchemaDTO schemaDTO = new SchemaDTO();
		schemaDTO.setName("test_db");
		schemaDTO.setTable(List.of(tableDTO));
		return schemaDTO;
	}

	@Test
	@DisplayName("SQL生成提示词应注入上一步结果")
	void newSqlGeneratorShouldIncludePreviousStepResults() {
		SchemaDTO schemaDTO = buildMinimalSchema();
		String previousStepResults = "上一步: step_1\n上一步结果: {\"column\":[\"id\"],\"data\":[{\"id\":\"1\"}]}";

		SqlGenerationDTO dto = SqlGenerationDTO.builder()
			.dialect("MySQL")
			.query("用户问题")
			.schemaDTO(schemaDTO)
			.evidence("evidence")
			.executionDescription("查询用户")
			.previousStepResults(previousStepResults)
			.build();

		String prompt = PromptHelper.buildNewSqlGeneratorPrompt(dto);

		assertAll(() -> assertTrue(prompt.contains("## 5. 上一步执行结果"), "应包含上一步执行结果章节"),
				() -> assertTrue(prompt.contains(previousStepResults), "应包含注入的上一步结果内容"));
	}

	@Test
	@DisplayName("SQL修复提示词缺省上一步结果时应填充为无")
	void sqlErrorFixerShouldDefaultPreviousStepResultsToNone() {
		SchemaDTO schemaDTO = buildMinimalSchema();

		SqlGenerationDTO dto = SqlGenerationDTO.builder()
			.dialect("MySQL")
			.query("用户问题")
			.schemaDTO(schemaDTO)
			.evidence("evidence")
			.executionDescription("查询用户")
			.sql("select 1")
			.exceptionMessage("syntax error")
			.build();

		String prompt = PromptHelper.buildSqlErrorFixerPrompt(dto);

		assertAll(() -> assertTrue(prompt.contains("## 5. 上一步执行结果"), "应包含上一步执行结果章节"),
				() -> assertTrue(prompt.contains("\n无\n"), "缺省时应填充为无"));
	}

}

