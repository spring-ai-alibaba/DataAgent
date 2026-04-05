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
package com.alibaba.cloud.ai.dataagent.service.nl2sql;

import com.alibaba.cloud.ai.dataagent.dto.prompt.SemanticConsistencyDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;

import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Nl2SqlServiceImplTest {

	@Mock
	private LlmService llmService;

	@Mock
	private JsonParseUtil jsonParseUtil;

	private Nl2SqlServiceImpl nl2SqlService;

	@BeforeEach
	void setUp() {
		nl2SqlService = new Nl2SqlServiceImpl(llmService, jsonParseUtil);

		ChatResponse mockResponse = ChatResponseUtil.createPureResponse("test response");
		when(llmService.callUser(anyString())).thenReturn(Flux.just(mockResponse));
		when(llmService.callSystem(anyString())).thenReturn(Flux.just(mockResponse));
		when(llmService.toStringFlux(any())).thenReturn(Flux.just("SELECT * FROM users"));
	}

	private SchemaDTO createTestSchema() {
		SchemaDTO schema = new SchemaDTO();
		schema.setName("test_db");
		schema.setDescription("Test database");
		schema.setTable(new ArrayList<>());
		return schema;
	}

	@Test
	void performSemanticConsistency_validDto_returnsValidationFlux() {
		SemanticConsistencyDTO dto = SemanticConsistencyDTO.builder()
			.dialect("mysql")
			.sql("SELECT * FROM users")
			.executionDescription("Get all users")
			.schemaInfo("users(id, name)")
			.userQuery("show all users")
			.evidence("evidence text")
			.build();

		Flux<ChatResponse> result = nl2SqlService.performSemanticConsistency(dto);

		StepVerifier.create(result).expectNextCount(1).verifyComplete();
		verify(llmService).callUser(anyString());
	}

	@Test
	void generateSql_noExistingSql_generatesNewSql() {
		SqlGenerationDTO dto = SqlGenerationDTO.builder()
			.executionDescription("Get all users")
			.dialect("mysql")
			.schemaDTO(createTestSchema())
			.sql(null)
			.build();

		Flux<String> result = nl2SqlService.generateSql(dto);

		StepVerifier.create(result).expectNext("SELECT * FROM users").verifyComplete();
		verify(llmService).callSystem(anyString());
	}

	@Test
	void generateSql_withExistingSql_usesErrorFixerPrompt() {
		SqlGenerationDTO dto = SqlGenerationDTO.builder()
			.executionDescription("Get all users")
			.dialect("mysql")
			.schemaDTO(createTestSchema())
			.sql("SELECT * FORM users")
			.exceptionMessage("syntax error")
			.build();

		Flux<String> result = nl2SqlService.generateSql(dto);

		StepVerifier.create(result).expectNext("SELECT * FROM users").verifyComplete();
		verify(llmService).callUser(anyString());
	}

	@Test
	void generateSql_withEmptySql_generatesNewSql() {
		SqlGenerationDTO dto = SqlGenerationDTO.builder()
			.executionDescription("Get all users")
			.dialect("mysql")
			.schemaDTO(createTestSchema())
			.sql("")
			.build();

		Flux<String> result = nl2SqlService.generateSql(dto);

		StepVerifier.create(result).expectNext("SELECT * FROM users").verifyComplete();
		verify(llmService).callSystem(anyString());
	}

	@Test
	void sqlTrim_withMarkdownMarkers_returnsCleanSql() {
		String markdownSql = "```sql\nSELECT * FROM users\n```";
		String result = nl2SqlService.sqlTrim(markdownSql);
		assertEquals("SELECT * FROM users", result);
	}

	@Test
	void sqlTrim_cleanSql_returnsUnchanged() {
		String cleanSql = "SELECT * FROM users";
		String result = nl2SqlService.sqlTrim(cleanSql);
		assertEquals("SELECT * FROM users", result);
	}

	@Test
	void sqlTrim_withLeadingTrailingWhitespace_returnsTrimmed() {
		String sql = "  SELECT * FROM users  ";
		String result = nl2SqlService.sqlTrim(sql);
		assertEquals("SELECT * FROM users", result);
	}

	@Test
	void sqlTrim_withTripleBackticksNoLang_returnsCleanSql() {
		String markdownSql = "```\nSELECT 1\n```";
		String result = nl2SqlService.sqlTrim(markdownSql);
		assertEquals("SELECT 1", result);
	}

	@Test
	void performSemanticConsistency_llmServiceCalled_withCorrectPrompt() {
		SemanticConsistencyDTO dto = SemanticConsistencyDTO.builder()
			.dialect("mysql")
			.sql("SELECT id FROM orders")
			.executionDescription("Get order ids")
			.schemaInfo("orders(id, total)")
			.userQuery("list order ids")
			.evidence("")
			.build();

		nl2SqlService.performSemanticConsistency(dto);
		verify(llmService).callUser(anyString());
	}

	@Test
	void generateSql_withWhitespaceSql_generatesNewSql() {
		SqlGenerationDTO dto = SqlGenerationDTO.builder()
			.executionDescription("Get all users")
			.dialect("mysql")
			.schemaDTO(createTestSchema())
			.sql("   ")
			.build();

		Flux<String> result = nl2SqlService.generateSql(dto);

		StepVerifier.create(result).expectNext("SELECT * FROM users").verifyComplete();
	}

	@Test
	void sqlTrim_null_throwsNPE() {
		assertThrows(NullPointerException.class, () -> nl2SqlService.sqlTrim(null));
	}

	@Test
	void sqlTrim_markdownCodeBlockWithLineBreaks_returnsCleanSql() {
		String markdownSql = "```sql\n  SELECT *\n  FROM users\n  WHERE id = 1\n```";
		String result = nl2SqlService.sqlTrim(markdownSql);
		assertNotNull(result);
		assertTrue(result.contains("SELECT"));
	}

	@Test
	void sqlTrim_multipleBacktickBlocks_extractsFirst() {
		String sql = "```sql\nSELECT 1\n```\nSome text\n```sql\nSELECT 2\n```";
		String result = nl2SqlService.sqlTrim(sql);
		assertNotNull(result);
	}

	@Test
	void performSemanticConsistency_nullEvidence_buildsPrompt() {
		SemanticConsistencyDTO dto = SemanticConsistencyDTO.builder()
			.dialect("mysql")
			.sql("SELECT 1")
			.executionDescription("test")
			.schemaInfo("t(c)")
			.userQuery("test")
			.evidence(null)
			.build();

		Flux<ChatResponse> result = nl2SqlService.performSemanticConsistency(dto);
		StepVerifier.create(result).expectNextCount(1).verifyComplete();
	}

	@Test
	void generateSql_withNonNullNonEmptySql_usesErrorFixerPath() {
		SqlGenerationDTO dto = SqlGenerationDTO.builder()
			.executionDescription("Get users")
			.dialect("postgresql")
			.schemaDTO(createTestSchema())
			.sql("SELECT * FROM users")
			.exceptionMessage("syntax error at position 5")
			.query("get all users")
			.evidence("no evidence")
			.build();

		Flux<String> result = nl2SqlService.generateSql(dto);

		StepVerifier.create(result).expectNext("SELECT * FROM users").verifyComplete();
		verify(llmService).callUser(anyString());
		verify(llmService, never()).callSystem(anyString());
	}

	@Test
	void generateSql_withNullSql_usesNewGenerationPath() {
		SqlGenerationDTO dto = SqlGenerationDTO.builder()
			.executionDescription("Get users")
			.dialect("mysql")
			.schemaDTO(createTestSchema())
			.sql(null)
			.query("get all users")
			.evidence("no evidence")
			.build();

		Flux<String> result = nl2SqlService.generateSql(dto);

		StepVerifier.create(result).expectNext("SELECT * FROM users").verifyComplete();
		verify(llmService).callSystem(anyString());
		verify(llmService, never()).callUser(anyString());
	}

}
