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

import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts the SQL execution history into ordered result sets for downstream Python
 * nodes.
 */
@Slf4j
public final class SqlResultSetExtractor {

	private static final String STEP_PREFIX = "step_";

	private SqlResultSetExtractor() {
	}

	public static List<List<Map<String, String>>> extractAll(Map<String, String> executionResults) {
		return extract(executionResults, null);
	}

	public static List<List<Map<String, String>>> extractSamples(Map<String, String> executionResults,
			int maxRowsPerStep) {
		if (maxRowsPerStep <= 0) {
			throw new IllegalArgumentException("maxRowsPerStep must be greater than zero");
		}
		return extract(executionResults, maxRowsPerStep);
	}

	private static List<List<Map<String, String>>> extract(Map<String, String> executionResults,
			Integer maxRowsPerStep) {
		if (executionResults == null || executionResults.isEmpty()) {
			return List.of();
		}
		return executionResults.entrySet()
			.stream()
			.map(SqlResultSetExtractor::toStepResult)
			.filter(Objects::nonNull)
			.sorted(Comparator.comparingInt(StepResult::stepNumber))
			.map(stepResult -> parseRows(stepResult, maxRowsPerStep))
			.filter(Objects::nonNull)
			.toList();
	}

	private static StepResult toStepResult(Map.Entry<String, String> entry) {
		String key = entry.getKey();
		if (key == null || !key.startsWith(STEP_PREFIX)) {
			return null;
		}
		try {
			return new StepResult(Integer.parseInt(key.substring(STEP_PREFIX.length())), entry.getValue());
		}
		catch (NumberFormatException ex) {
			log.warn("Ignoring SQL execution result with invalid step key: {}", key);
			return null;
		}
	}

	private static List<Map<String, String>> parseRows(StepResult stepResult, Integer maxRowsPerStep) {
		try {
			ResultSetBO resultSet = JsonUtil.getObjectMapper().readValue(stepResult.json(), ResultSetBO.class);
			List<Map<String, String>> rows = resultSet.getData();
			if (rows == null || rows.isEmpty()) {
				return List.of();
			}
			return maxRowsPerStep == null ? List.copyOf(rows) : rows.stream().limit(maxRowsPerStep).toList();
		}
		catch (Exception ex) {
			log.warn("Ignoring invalid SQL execution result for step_{}: {}", stepResult.stepNumber(), ex.getMessage());
			return null;
		}
	}

	private record StepResult(int stepNumber, String json) {
	}

}
