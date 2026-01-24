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
package com.alibaba.cloud.ai.dataagent.dto.planner;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

	@JsonProperty("thought_process")
	@JsonPropertyDescription("简要描述你的分析思路。必须明确提到你检查了哪些表和字段")
	private String thoughtProcess;

	@JsonProperty("execution_plan")
	@JsonPropertyDescription("执行计划的步骤列表")
	private List<ExecutionStep> executionPlan;

	@Override
	public String toString() {
		return "Plan{" + "thoughtProcess='" + thoughtProcess + '\'' + ", executionPlan=" + executionPlan + '}';
	}

	// 为NL2SQL模式准备的Plan，只走SQL生成
	private static final String NL2SQL_PLAN_JSON;

	static {
		ExecutionStep step = new ExecutionStep();
		ExecutionStep.ToolParameters parameters = new ExecutionStep.ToolParameters();
		parameters.setInstruction("SQL生成");
		step.setStep(1);
		step.setToolToUse(Constant.SQL_GENERATE_NODE);
		step.setToolParameters(parameters);
		Plan plan = new Plan();
		plan.setThoughtProcess("根据问题生成SQL");
		plan.setExecutionPlan(List.of(step));
		try {
			NL2SQL_PLAN_JSON = JsonUtil.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(plan);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	public static String nl2SqlPlan() {
		return NL2SQL_PLAN_JSON;
	}

}
