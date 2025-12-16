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

import com.alibaba.cloud.ai.dataagent.common.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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
	public static class ToolParameters {

		// 对于SQL_GENERATE_NODE，内容将直接作为 SQL生成节点的 Prompt
		// 对于PYTHON_GENERATE_NODE，内容是任务简述（用于日志记录）。
		private String description;

		//REPORT_GENERATOR_NODE专用。报告的大纲、需要回答的关键问题和建议方向。
		@JsonProperty("summary_and_recommendations")
		private String summaryAndRecommendations;

		@JsonProperty("sql_query")
		private String sqlQuery;

		//Python节点专用，给 Python 解释器的具体编程指令（例如：“读取上一步的数据，计算环比增长率并绘制柱状图”）
		@JsonProperty("instruction")
		private String instruction;

		@JsonProperty("input_data_description")
		private String inputDataDescription;

		public String toJsonStr() {
			ObjectMapper objectMapper = JsonUtil.getObjectMapper();
			try {
				return objectMapper.writeValueAsString(this);
			}
			catch (JsonProcessingException e) {
				throw new RuntimeException("Failed to convert object to JSON string", e);
			}
		}

	}

	@Override
	public String toString() {
		return "ExecutionStep{" + "step=" + step + ", toolToUse='" + toolToUse + '\'' + ", toolParameters="
				+ toolParameters + '}';
	}

}
