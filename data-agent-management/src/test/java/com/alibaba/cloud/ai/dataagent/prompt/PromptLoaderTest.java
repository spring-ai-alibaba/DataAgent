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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLoaderTest {

	@AfterEach
	void tearDown() {
		PromptLoader.clearCache();
	}

	@Test
	void loadPrompt_validName_returnsTheRequestedTemplate() {
		String content = PromptLoader.loadPrompt("intent-recognition");

		assertThat(content).contains("# 角色", "{latest_query}", "{multi_turn}", "{format}");
	}

	@Test
	void loadPrompt_cachedResult_returnsSameContent() {
		String first = PromptLoader.loadPrompt("intent-recognition");
		String second = PromptLoader.loadPrompt("intent-recognition");

		assertThat(second).isSameAs(first);
	}

	@Test
	void loadPrompt_missingResource_throwsAnExplicitContractError() {
		assertThatThrownBy(() -> PromptLoader.loadPrompt("nonexistent-prompt-file-xyz-12345"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Prompt resource not found: prompts/nonexistent-prompt-file-xyz-12345.txt");
		assertThat(PromptLoader.getCacheSize()).isZero();
	}

	@Test
	void clearCache_emptiesCache() {
		PromptLoader.loadPrompt("intent-recognition");
		assertThat(PromptLoader.getCacheSize()).isOne();

		PromptLoader.clearCache();

		assertThat(PromptLoader.getCacheSize()).isZero();
	}

	@Test
	void getCacheSize_afterLoading_returnsCorrectCount() {
		PromptLoader.clearCache();
		assertThat(PromptLoader.getCacheSize()).isZero();

		PromptLoader.loadPrompt("intent-recognition");
		assertThat(PromptLoader.getCacheSize()).isOne();

		PromptLoader.loadPrompt("mix-selector");
		assertThat(PromptLoader.getCacheSize()).isEqualTo(2);
	}

}
