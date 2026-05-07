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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;

import static org.junit.jupiter.api.Assertions.*;

class PromptConstantTest {

	@AfterEach
	void tearDown() {
		PromptLoader.clearCache();
	}

	@Test
	void getIntentRecognitionPromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getIntentRecognitionPromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getEvidenceQueryRewritePromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getEvidenceQueryRewritePromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getAgentKnowledgePromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getAgentKnowledgePromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getQueryEnhancementPromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getQueryEnhancementPromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getFeasibilityAssessmentPromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getFeasibilityAssessmentPromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getMixSelectorPromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getMixSelectorPromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getSemanticConsistencyPromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getSemanticConsistencyPromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getNewSqlGeneratorPromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getNewSqlGeneratorPromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getPlannerPromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getPlannerPromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getReportGeneratorPlainPromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getReportGeneratorPlainPromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getSqlErrorFixerPromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getSqlErrorFixerPromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getPythonGeneratorPromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getPythonGeneratorPromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getPythonAnalyzePromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getPythonAnalyzePromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getBusinessKnowledgePromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getBusinessKnowledgePromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getSemanticModelPromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getSemanticModelPromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getJsonFixPromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getJsonFixPromptTemplate();
		assertNotNull(template);
	}

	@Test
	void getDataViewAnalyzePromptTemplate_returnsNonNull() {
		PromptTemplate template = PromptConstant.getDataViewAnalyzePromptTemplate();
		assertNotNull(template);
	}

}
