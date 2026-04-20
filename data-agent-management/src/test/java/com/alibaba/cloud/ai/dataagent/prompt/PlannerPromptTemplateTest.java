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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerPromptTemplateTest {

	@Test
	void plannerPrompt_shouldUseCorrectReportGeneratorNodeName() throws IOException {
		var inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("prompts/planner.txt");
		assertNotNull(inputStream, "planner prompt template should exist");

		String content;
		try (inputStream) {
			content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		}

		assertTrue(content.contains("REPORT_GENERATOR_NODE"),
				"planner prompt should guide model to use REPORT_GENERATOR_NODE");
		assertFalse(content.contains("REPORT_GENERATE_NODE"),
				"planner prompt should not contain legacy typo REPORT_GENERATE_NODE");
	}

}
