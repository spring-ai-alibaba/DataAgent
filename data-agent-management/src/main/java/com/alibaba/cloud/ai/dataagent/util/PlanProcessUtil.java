/*
 * Copyright 2025 the original author or authors.
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

package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.PLANNER_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PLAN_CURRENT_STEP;

/**
 * util class for plan-based execution nodes Provides common functionality for nodes that
 * execute based on predefined plans
 *
 * @author zhangshenghang
 */
@Slf4j
public final class PlanProcessUtil {

	private static final BeanOutputConverter<Plan> converter;

	private static final String STEP_PREFIX = "step_";

	private static final ObjectMapper objectMapper;

	static {
		converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
		});

		objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
	}

	private PlanProcessUtil() {

	}

	/**
	 * Get the current execution step from the plan
	 * @param state the overall state containing plan information
	 * @return the current execution step
	 * @throws IllegalStateException if plan output is empty, plan parsing fails, or step
	 * index is out of range
	 */
	public static ExecutionStep getCurrentExecutionStep(OverAllState state) {
		Plan plan = getPlan(state);
		int currentStep = getCurrentStepNumber(state);
		return getCurrentExecutionStep(plan, currentStep);
	}

	public static String getCurrentExecutionStepInstruction(OverAllState state) {
		String instruction;
		ExecutionStep.ToolParameters currentStepParams = PlanProcessUtil.getCurrentExecutionStep(state)
			.getToolParameters();
		instruction = currentStepParams != null ? currentStepParams.getInstruction() : "无";
		return instruction;
	}

	/**
	 * Get the current execution step from the plan
	 * @param plan the plan object
	 * @param currentStep current step
	 * @return the current execution step
	 * @throws IllegalStateException if plan output is empty, plan parsing fails, or step
	 * index is out of range
	 */
	public static ExecutionStep getCurrentExecutionStep(Plan plan, Integer currentStep) {
		List<ExecutionStep> executionPlan = plan.getExecutionPlan();
		if (executionPlan == null || executionPlan.isEmpty()) {
			throw new IllegalStateException("执行计划为空");
		}

		int stepIndex = currentStep - 1;
		if (stepIndex < 0 || stepIndex >= executionPlan.size()) {
			throw new IllegalStateException("当前步骤索引超出范围: " + stepIndex);
		}

		return executionPlan.get(stepIndex);
	}

	/**
	 * Get the plan object from state
	 * @param state the overall state containing plan information
	 * @return the parsed plan object
	 * @throws IllegalStateException if plan output is empty or plan parsing fails
	 */
	public static Plan getPlan(OverAllState state) {
		String plannerNodeOutput = (String) state.value(PLANNER_NODE_OUTPUT)
			.orElseThrow(() -> new IllegalStateException("计划节点输出为空"));
		Plan plan = converter.convert(plannerNodeOutput);
		if (plan == null) {
			throw new IllegalStateException("计划解析失败");
		}
		return plan;
	}

	/**
	 * Get the current step number from state
	 * @param state the overall state
	 * @return the current step number (defaults to 1 if not set)
	 */
	public static int getCurrentStepNumber(OverAllState state) {
		return state.value(PLAN_CURRENT_STEP, 1);
	}

	/**
	 * Add step result
	 * @param existingResults existing result collection
	 * @param stepNumber step number
	 * @param result result content
	 * @return updated result collection
	 */
	public static Map<String, String> addStepResult(Map<String, String> existingResults, Integer stepNumber,
			String result) {
		Map<String, String> updatedResults = new HashMap<>(existingResults);
		updatedResults.put(STEP_PREFIX + stepNumber, result);
		return updatedResults;
	}

	public static List<List<Map<String, String>>> convertExecutionResultsToList(
			HashMap<String, String> executionResults, Integer sampleDataNumber) {
		List<List<Map<String, String>>> convertedResults = new ArrayList<>();
		if (executionResults.isEmpty()) {
			return convertedResults;
		}
		// 按照步骤顺序处理结果
		for (int i = 1; i <= executionResults.size(); i++) {
			String stepKey = "step_" + i;
			String jsonResult = executionResults.get(stepKey);

			if (jsonResult != null && !jsonResult.isEmpty()) {
				try {
					// 将 JSON 字符串转换为 ResultSetBO 对象
					ResultSetBO resultSetBO = objectMapper.readValue(jsonResult, ResultSetBO.class);

					// 提取 ResultSetBO 中的 data 字段
					List<Map<String, String>> stepData = resultSetBO.getData();
					if (stepData != null) {
						if (sampleDataNumber != null && sampleDataNumber > 0) {
							convertedResults.add(stepData.stream().limit(sampleDataNumber).toList());
						}
						else {
							convertedResults.add(stepData);
						}
					}
				}
				catch (Exception e) {
					log.error("Failed to parse execution result for step {}: {}", stepKey, e.getMessage());
				}
			}
		}

		return convertedResults;
	}

}
