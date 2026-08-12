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
package com.alibaba.cloud.ai.dataagent.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlResultSetExtractorTest {

	@Test
	void extractAll_ordersStepsAndPreservesResultSetBoundaries() {
		Map<String, String> executionResults = Map.of("step_10", "{\"data\":[{\"department\":\"support\"}]}", "step_2",
				"{\"data\":[{\"department\":\"engineering\"}]}", "step_1", "{\"data\":[{\"department\":\"sales\"}]}");

		List<List<Map<String, String>>> result = SqlResultSetExtractor.extractAll(executionResults);

		assertEquals(List.of(List.of(Map.of("department", "sales")), List.of(Map.of("department", "engineering")),
				List.of(Map.of("department", "support"))), result);
	}

	@Test
	void extractSamples_limitsEachStepIndependently() {
		Map<String, String> executionResults = Map.of("step_1", "{\"data\":[{\"id\":\"1\"},{\"id\":\"2\"}]}", "step_2",
				"{\"data\":[{\"id\":\"3\"},{\"id\":\"4\"}]}");

		List<List<Map<String, String>>> result = SqlResultSetExtractor.extractSamples(executionResults, 1);

		assertEquals(List.of(List.of(Map.of("id", "1")), List.of(Map.of("id", "3"))), result);
	}

	@Test
	void extractAll_skipsInvalidEntriesAndKeepsEmptyResultSets() {
		Map<String, String> executionResults = Map.of("not_a_step", "{\"data\":[{\"id\":\"0\"}]}", "step_bad", "{}",
				"step_1", "{\"data\":[]}", "step_2", "not-json");

		List<List<Map<String, String>>> result = SqlResultSetExtractor.extractAll(executionResults);

		assertEquals(List.of(List.of()), result);
	}

	@Test
	void extractSamples_rejectsNonPositiveLimit() {
		assertThrows(IllegalArgumentException.class, () -> SqlResultSetExtractor.extractSamples(Map.of(), 0));
	}

}
