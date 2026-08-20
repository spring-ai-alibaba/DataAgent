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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.prompt.PromptTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class PromptConstantTest {

	@AfterEach
	void tearDown() {
		PromptLoader.clearCache();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("promptContracts")
	void promptTemplate_declaredVariables_renderWithoutLeavingPlaceholders(String promptName,
			Supplier<PromptTemplate> templateFactory, List<String> variables) {
		Map<String, Object> values = new LinkedHashMap<>();
		variables.forEach(variable -> values.put(variable, "sentinel_" + variable));

		String rendered = templateFactory.get().render(values);

		assertThat(rendered).as(promptName).contains(values.values().toArray(String[]::new));
		variables.forEach(variable -> assertThat(rendered).as(promptName).doesNotContain("{" + variable + "}"));
	}

	private static Stream<Arguments> promptContracts() {
		return Stream.of(
				contract("intent-recognition", PromptConstant::getIntentRecognitionPromptTemplate, "latest_query",
						"multi_turn", "format"),
				contract("evidence-query-rewrite", PromptConstant::getEvidenceQueryRewritePromptTemplate,
						"latest_query", "multi_turn", "format"),
				contract("agent-knowledge", PromptConstant::getAgentKnowledgePromptTemplate, "agentKnowledge"),
				contract("query-enhancement", PromptConstant::getQueryEnhancementPromptTemplate, "latest_query",
						"multi_turn", "evidence", "current_time_info", "format"),
				contract("feasibility-assessment", PromptConstant::getFeasibilityAssessmentPromptTemplate,
						"canonical_query", "multi_turn", "evidence", "recalled_schema", "format"),
				contract("mix-selector", PromptConstant::getMixSelectorPromptTemplate, "evidence", "question",
						"schema_info"),
				contract("semantic-consistency", PromptConstant::getSemanticConsistencyPromptTemplate, "dialect", "sql",
						"execution_description", "schema_info", "user_query", "evidence", "format"),
				contract("new-sql-generate", PromptConstant::getNewSqlGeneratorPromptTemplate, "dialect",
						"execution_description", "schema_info", "question", "evidence", "previous_step_results"),
				contract("planner", PromptConstant::getPlannerPromptTemplate, "user_question", "evidence", "schema",
						"semantic_model", "plan_validation_error", "format"),
				contract("report-generator-plain", PromptConstant::getReportGeneratorPlainPromptTemplate,
						"user_requirements_and_plan", "analysis_steps_and_data", "summary_and_recommendations",
						"optimization_section", "json_example"),
				contract("sql-error-fixer", PromptConstant::getSqlErrorFixerPromptTemplate, "dialect", "error_sql",
						"error_message", "execution_description", "schema_info", "question", "evidence",
						"previous_step_results"),
				contract("python-generator", PromptConstant::getPythonGeneratorPromptTemplate, "python_memory",
						"python_timeout", "database_schema", "sample_input", "plan_description"),
				contract("python-analyze", PromptConstant::getPythonAnalyzePromptTemplate, "python_output",
						"user_query"),
				contract("business-knowledge", PromptConstant::getBusinessKnowledgePromptTemplate, "businessKnowledge"),
				contract("semantic-model", PromptConstant::getSemanticModelPromptTemplate, "semanticModel"),
				contract("json-fix", PromptConstant::getJsonFixPromptTemplate, "json_string", "error_message"),
				contract("data-view-analyze", PromptConstant::getDataViewAnalyzePromptTemplate, "format"));
	}

	private static Arguments contract(String name, Supplier<PromptTemplate> templateFactory, String... variables) {
		return Arguments.of(name, templateFactory, List.of(variables));
	}

}
