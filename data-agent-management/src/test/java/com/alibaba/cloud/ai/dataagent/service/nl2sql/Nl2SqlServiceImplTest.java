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

import java.util.ArrayList;
import java.util.stream.Stream;

import com.alibaba.cloud.ai.dataagent.dto.prompt.SemanticConsistencyDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SemanticConsistencyOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Nl2SqlServiceImplTest {

	@Mock
	private LlmService llmService;

	@Mock
	private JsonParseUtil jsonParseUtil;

	private Nl2SqlServiceImpl nl2SqlService;

	@BeforeEach
	void setUp() {
		nl2SqlService = new Nl2SqlServiceImpl(llmService, jsonParseUtil);
	}

	@Test
	void performSemanticConsistency_rendersAllBusinessInputsAndReturnsTheLlmResponse() {
		SemanticConsistencyDTO dto = SemanticConsistencyDTO.builder()
			.dialect("mysql")
			.sql("SELECT id FROM orders")
			.executionDescription("Get order ids")
			.schemaInfo("orders(id, total)")
			.userQuery("list order ids")
			.evidence("orders are tenant scoped")
			.build();
		ChatResponse response = ChatResponseUtil.createPureResponse("{\"status\":\"VALID\"}");
		when(llmService.callUser(anyString(), eq(SemanticConsistencyOutputDTO.class))).thenReturn(Flux.just(response));

		StepVerifier.create(nl2SqlService.performSemanticConsistency(dto)).expectNext(response).verifyComplete();

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(llmService).callUser(prompt.capture(), eq(SemanticConsistencyOutputDTO.class));
		assertThat(prompt.getValue()).contains("mysql", "SELECT id FROM orders", "Get order ids", "orders(id, total)",
				"list order ids", "orders are tenant scoped");
	}

	@Test
	void performSemanticConsistency_nullEvidenceStillRendersTheRequiredPrompt() {
		SemanticConsistencyDTO dto = SemanticConsistencyDTO.builder()
			.dialect("mysql")
			.sql("SELECT 1")
			.executionDescription("health check")
			.schemaInfo("dual(value)")
			.userQuery("check database")
			.evidence(null)
			.build();
		ChatResponse response = ChatResponseUtil.createPureResponse("{\"status\":\"VALID\"}");
		when(llmService.callUser(anyString(), eq(SemanticConsistencyOutputDTO.class))).thenReturn(Flux.just(response));

		StepVerifier.create(nl2SqlService.performSemanticConsistency(dto)).expectNext(response).verifyComplete();

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(llmService).callUser(prompt.capture(), eq(SemanticConsistencyOutputDTO.class));
		assertThat(prompt.getValue()).contains("SELECT 1", "health check", "dual(value)", "check database");
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "   " })
	void generateSql_withoutMeaningfulExistingSql_usesNewGenerationPrompt(String existingSql) {
		SqlGenerationDTO dto = SqlGenerationDTO.builder()
			.executionDescription("Get all users")
			.dialect("mysql")
			.schemaDTO(createTestSchema())
			.sql(existingSql)
			.query("show all users")
			.evidence("users are active")
			.build();
		stubSystemSql("SELECT * FROM users");

		StepVerifier.create(nl2SqlService.generateSql(dto)).expectNext("SELECT * FROM users").verifyComplete();

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(llmService).callSystem(prompt.capture());
		verify(llmService, never()).callUser(anyString());
		assertThat(prompt.getValue()).contains("Get all users", "mysql", "test_db", "show all users",
				"users are active");
	}

	@Test
	void generateSql_withExistingSql_usesErrorFixerPromptAndPreservesFailureContext() {
		SqlGenerationDTO dto = SqlGenerationDTO.builder()
			.executionDescription("Get users")
			.dialect("postgresql")
			.schemaDTO(createTestSchema())
			.sql("SELECT * FORM users")
			.exceptionMessage("syntax error at position 10")
			.query("get all users")
			.evidence("users table is authoritative")
			.build();
		stubUserSql("SELECT * FROM users");

		StepVerifier.create(nl2SqlService.generateSql(dto)).expectNext("SELECT * FROM users").verifyComplete();

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(llmService).callUser(prompt.capture());
		verify(llmService, never()).callSystem(anyString());
		assertThat(prompt.getValue()).contains("postgresql", "SELECT * FORM users", "syntax error at position 10",
				"Get users", "test_db", "get all users", "users table is authoritative");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("sqlTrimCases")
	void sqlTrim_extractsTheFirstSqlBlockAndPreservesItsFormatting(String name, String input, String expected) {
		assertThat(nl2SqlService.sqlTrim(input)).as(name).isEqualTo(expected);
	}

	@Test
	void sqlTrim_nullInput_rejectsTheMissingSql() {
		assertThatNullPointerException().isThrownBy(() -> nl2SqlService.sqlTrim(null));
	}

	private void stubSystemSql(String sql) {
		Flux<ChatResponse> response = Flux.just(ChatResponseUtil.createPureResponse(sql));
		when(llmService.callSystem(anyString())).thenReturn(response);
		when(llmService.toStringFlux(response)).thenReturn(Flux.just(sql));
	}

	private void stubUserSql(String sql) {
		Flux<ChatResponse> response = Flux.just(ChatResponseUtil.createPureResponse(sql));
		when(llmService.callUser(anyString())).thenReturn(response);
		when(llmService.toStringFlux(response)).thenReturn(Flux.just(sql));
	}

	private SchemaDTO createTestSchema() {
		SchemaDTO schema = new SchemaDTO();
		schema.setName("test_db");
		schema.setDescription("Test database");
		schema.setTable(new ArrayList<>());
		return schema;
	}

	private static Stream<Arguments> sqlTrimCases() {
		return Stream.of(Arguments.of("markdown sql", "```sql\nSELECT * FROM users\n```", "SELECT * FROM users"),
				Arguments.of("plain sql", "SELECT * FROM users", "SELECT * FROM users"),
				Arguments.of("surrounding whitespace", "  SELECT * FROM users  ", "SELECT * FROM users"),
				Arguments.of("untyped block", "```\nSELECT 1\n```", "SELECT 1"),
				Arguments.of("multiline sql", "```sql\n  SELECT *\n  FROM users\n  WHERE id = 1\n```",
						"SELECT *\n  FROM users\n  WHERE id = 1"),
				Arguments.of("multiple blocks", "```sql\nSELECT 1\n```\ntext\n```sql\nSELECT 2\n```", "SELECT 1"));
	}

}
