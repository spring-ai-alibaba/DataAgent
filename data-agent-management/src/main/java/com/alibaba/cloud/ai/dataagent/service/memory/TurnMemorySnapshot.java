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
package com.alibaba.cloud.ai.dataagent.service.memory;

import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * Compact, run-scoped accumulator. It captures verified state before downstream nodes
 * clear large graph values.
 */
public class TurnMemorySnapshot {

	private String canonicalQuery;

	private Integer datasourceId;

	private String schemaFingerprint;

	private String plannerJson;

	private String finalAnswer;

	private final Set<String> tableNames = new LinkedHashSet<>();

	private final Set<String> sqlStatements = new LinkedHashSet<>();

	private Map<String, Object> executionResults = new LinkedHashMap<>();

	public synchronized void capture(OverAllState state, String nodeName) {
		state.value(QUERY_ENHANCE_NODE_OUTPUT)
			.map(value -> JsonUtil.getObjectMapper().convertValue(value, QueryEnhanceOutputDTO.class))
			.map(QueryEnhanceOutputDTO::getCanonicalQuery)
			.filter(StringUtils::isNotBlank)
			.ifPresent(value -> this.canonicalQuery = value);
		state.value(DATASOURCE_ID)
			.map(value -> value instanceof Number number ? number.intValue() : Integer.valueOf(value.toString()))
			.ifPresent(value -> this.datasourceId = value);
		state.value(SCHEMA_FINGERPRINT)
			.map(Object::toString)
			.filter(StringUtils::isNotBlank)
			.ifPresent(value -> this.schemaFingerprint = value);
		state.value(PLANNER_NODE_OUTPUT)
			.map(Object::toString)
			.filter(StringUtils::isNotBlank)
			.ifPresent(value -> this.plannerJson = value);
		state.value(FINAL_ANSWER)
			.map(Object::toString)
			.filter(StringUtils::isNotBlank)
			.ifPresent(value -> this.finalAnswer = value);
		state.value(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT).ifPresent(this::captureTableNames);
		if ("SqlExecuteNode".equals(nodeName)) {
			state.value(SQL_GENERATE_OUTPUT)
				.map(Object::toString)
				.filter(value -> StringUtils.isNotBlank(value) && !value.equals(com.alibaba.cloud.ai.graph.StateGraph.END))
				.ifPresent(this.sqlStatements::add);
		}
		state.value(SQL_EXECUTE_NODE_OUTPUT).ifPresent(this::captureExecutionResults);
	}

	private void captureTableNames(Object value) {
		if (!(value instanceof List<?> values)) {
			return;
		}
		for (Object item : values) {
			if (item instanceof Document document) {
				Object name = document.getMetadata().get("name");
				if (name != null && StringUtils.isNotBlank(name.toString())) {
					this.tableNames.add(name.toString());
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void captureExecutionResults(Object value) {
		if (value instanceof Map<?, ?> map) {
			this.executionResults = JsonUtil.getObjectMapper().convertValue(map, LinkedHashMap.class);
		}
	}

	public synchronized String queryFrameJson() {
		Map<String, Object> frame = new LinkedHashMap<>();
		frame.put("canonicalQuery", canonicalQuery);
		frame.put("tables", new ArrayList<>(tableNames));
		frame.put("sqlStatements", new ArrayList<>(sqlStatements));
		return writeJson(frame);
	}

	public synchronized String sqlArtifactJson() {
		return writeJson(new ArrayList<>(sqlStatements));
	}

	public synchronized String resultArtifactJson() {
		return writeJson(executionResults);
	}

	private String writeJson(Object value) {
		try {
			return JsonUtil.getObjectMapper().writeValueAsString(value);
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to serialize turn memory snapshot", e);
		}
	}

	public synchronized String getCanonicalQuery() {
		return canonicalQuery;
	}

	public synchronized Integer getDatasourceId() {
		return datasourceId;
	}

	public synchronized String getSchemaFingerprint() {
		return schemaFingerprint;
	}

	public synchronized String getPlannerJson() {
		return plannerJson;
	}

	public synchronized String getFinalAnswer() {
		return finalAnswer;
	}

	public synchronized boolean hasVerifiedEvidence(String reportContent) {
		return StringUtils.isNotBlank(reportContent) || !executionResults.isEmpty();
	}

}
