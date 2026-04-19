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

import static org.junit.jupiter.api.Assertions.*;

class PromptLoaderTest {

	@AfterEach
	void tearDown() {
		PromptLoader.clearCache();
	}

	@Test
	void loadPrompt_validName_returnsContent() {
		String content = PromptLoader.loadPrompt("intent-recognition");
		assertNotNull(content);
		assertFalse(content.isEmpty());
	}

	@Test
	void loadPrompt_cachedResult_returnsSameContent() {
		String first = PromptLoader.loadPrompt("intent-recognition");
		String second = PromptLoader.loadPrompt("intent-recognition");
		assertSame(first, second);
	}

	@Test
	void loadPrompt_invalidName_returnsNullOrEmpty() {
		try {
			String content = PromptLoader.loadPrompt("nonexistent-prompt-file-xyz-12345");
			assertTrue(content == null || content.isEmpty());
		}
		catch (Exception e) {
			// expected - some implementations throw
		}
	}

	@Test
	void clearCache_emptiesCache() {
		PromptLoader.loadPrompt("intent-recognition");
		assertTrue(PromptLoader.getCacheSize() > 0);

		PromptLoader.clearCache();
		assertEquals(0, PromptLoader.getCacheSize());
	}

	@Test
	void getCacheSize_afterLoading_returnsCorrectCount() {
		PromptLoader.clearCache();
		assertEquals(0, PromptLoader.getCacheSize());

		PromptLoader.loadPrompt("intent-recognition");
		assertEquals(1, PromptLoader.getCacheSize());

		PromptLoader.loadPrompt("mix-selector");
		assertEquals(2, PromptLoader.getCacheSize());
	}

	@Test
	void loadPrompt_multiplePrompts_allLoadable() {
		assertNotNull(PromptLoader.loadPrompt("intent-recognition"));
		assertNotNull(PromptLoader.loadPrompt("mix-selector"));
		assertNotNull(PromptLoader.loadPrompt("new-sql-generate"));
		assertNotNull(PromptLoader.loadPrompt("semantic-consistency"));
		assertNotNull(PromptLoader.loadPrompt("planner"));
		assertNotNull(PromptLoader.loadPrompt("business-knowledge"));
		assertNotNull(PromptLoader.loadPrompt("agent-knowledge"));
		assertNotNull(PromptLoader.loadPrompt("query-enhancement"));
		assertNotNull(PromptLoader.loadPrompt("feasibility-assessment"));
		assertNotNull(PromptLoader.loadPrompt("sql-error-fixer"));
		assertNotNull(PromptLoader.loadPrompt("python-generator"));
		assertNotNull(PromptLoader.loadPrompt("python-analyze"));
		assertNotNull(PromptLoader.loadPrompt("semantic-model"));
		assertNotNull(PromptLoader.loadPrompt("json-fix"));
		assertNotNull(PromptLoader.loadPrompt("data-view-analyze"));
		assertNotNull(PromptLoader.loadPrompt("report-generator-plain"));
		assertNotNull(PromptLoader.loadPrompt("evidence-query-rewrite"));
	}

}
