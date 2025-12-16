/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.dataagent.dto.planner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStep {

	@JsonProperty("step")
	private int step;

	@JsonProperty("tool_to_use")
	private String toolToUse;

	@JsonProperty("tool_parameters")
	private ToolParameters toolParameters;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	// 序列化时忽略 null 值，让生成的 JSON 更干净
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class ToolParameters {

		// 统一指令字段：
		// 1. SQL_GENERATE_NODE -> 存当前步骤要做的详细的 SQL 需求
		// 2. PYTHON_GENERATE_NODE -> 存当前步骤要做的详细的编程需求
		@JsonProperty("instruction")
		private String instruction;

		// REPORT_GENERATOR_NODE专用。报告的大纲、需要回答的关键问题和建议方向。
		@JsonProperty("summary_and_recommendations")
		private String summaryAndRecommendations;

		// --- 运行态字段 ---
		// Planner 永远不填这个字段（Prompt里根本不提它）
		// 但是 SQL_GENERATE_NODE 运行完后，会把生成的 SQL 塞进来
		@JsonProperty("sql_query")
		private String sqlQuery;

	}

	@Override
	public String toString() {
		return "ExecutionStep{" + "step=" + step + ", toolToUse='" + toolToUse + '\'' + ", toolParameters="
				+ toolParameters + '}';
	}

}
