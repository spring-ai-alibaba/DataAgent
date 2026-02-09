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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import com.alibaba.cloud.ai.dataagent.entity.UserPromptConfig;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.cache.ResultCacheService;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.prompt.UserPromptService;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * Report generation node that creates comprehensive analysis reports based on execution
 * results.
 *
 * This node is responsible for: - Generating detailed analysis reports from SQL execution
 * results - Summarizing data insights and findings - Providing comprehensive answers to
 * user queries - Creating structured final output for users
 *
 * @author zhangshenghang
 */
@Slf4j
@Component
public class ReportGeneratorNode implements NodeAction {

	private final LlmService llmService;

	private final BeanOutputConverter<Plan> converter;

	private final UserPromptService promptConfigService;

	private final ResultCacheService resultCacheService; // 注入缓存服务

	// 定义最大结果大小常量
	private static final int MAX_RESULT_SIZE = 5000; // 每个结果最大5KB

	private static final int MAX_TOTAL_SIZE = 20000; // 总体结果最大20KB

	public ReportGeneratorNode(LlmService llmService, UserPromptService promptConfigService,
			ResultCacheService resultCacheService) { // 注入缓存服务
		this.llmService = llmService;
		this.converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
		});
		this.promptConfigService = promptConfigService;
		this.resultCacheService = resultCacheService;
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// Get necessary input parameters
		String plannerNodeOutput = StateUtil.getStringValue(state, PLANNER_NODE_OUTPUT);
		String userInput = StateUtil.getCanonicalQuery(state);
		Integer currentStep = StateUtil.getObjectValue(state, PLAN_CURRENT_STEP, Integer.class, 1);
		@SuppressWarnings("unchecked")
		HashMap<String, String> executionResults = StateUtil.getObjectValue(state, SQL_EXECUTE_NODE_OUTPUT,
				HashMap.class, new HashMap<>());

		// Parse plan and get current step
		Plan plan = converter.convert(plannerNodeOutput);
		ExecutionStep executionStep = getCurrentExecutionStep(plan, currentStep);
		String summaryAndRecommendations = executionStep.getToolParameters().getSummaryAndRecommendations();

		// Get agent id from state
		String agentIdStr = StateUtil.getStringValue(state, AGENT_ID);
		Long agentId = null;
		try {
			if (agentIdStr != null) {
				agentId = Long.parseLong(agentIdStr);
			}
		}
		catch (NumberFormatException ignore) {
			// ignore parse error, treat as global config
		}

		// 使用缓存机制处理执行结果
		HashMap<String, String> processedExecutionResults = processExecutionResults(executionResults);

		// Generate report streaming flux
		Flux<ChatResponse> reportGenerationFlux = generateReport(userInput, plan, processedExecutionResults,
				summaryAndRecommendations, agentId);

		TextType reportTextType = TextType.MARK_DOWN;

		// Use utility class to create streaming generator with content collection
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "开始生成报告...", "报告生成完成！", reportContent -> {
					log.info("Generated report content: {}", reportContent);
					Map<String, Object> result = new HashMap<>();
					result.put(RESULT, reportContent);
					result.put(SQL_EXECUTE_NODE_OUTPUT, null);
					result.put(PLAN_CURRENT_STEP, null);
					result.put(PLANNER_NODE_OUTPUT, null);
					return result;
				},
				Flux.concat(Flux.just(ChatResponseUtil.createPureResponse(reportTextType.getStartSign())),
						reportGenerationFlux,
						Flux.just(ChatResponseUtil.createPureResponse(reportTextType.getEndSign()))));

		return Map.of(RESULT, generator);
	}

	/**
	 * 处理执行结果，对大数据进行缓存
	 */
	private HashMap<String, String> processExecutionResults(HashMap<String, String> executionResults) {
		HashMap<String, String> processedResults = new HashMap<>();

		int totalSize = 0;
		for (Map.Entry<String, String> entry : executionResults.entrySet()) {
			String stepKey = entry.getKey();
			String stepResult = entry.getValue();

			if (totalSize >= MAX_TOTAL_SIZE) {
				log.warn("Total execution results size exceeded limit, stopping processing");
				break;
			}

			// 检查单个结果大小
			if (stepResult.length() > MAX_RESULT_SIZE) {
				// 将大数据结果存入缓存，并在提示词中引用
				String cacheKey = resultCacheService.storeResult(stepKey, stepResult);

				// 创建引用信息
				String referenceInfo = createReferenceInfo(stepKey, stepResult, cacheKey);
				processedResults.put(stepKey, referenceInfo);

				totalSize += referenceInfo.length();
			}
			else {
				processedResults.put(stepKey, stepResult);
				totalSize += stepResult.length();
			}
		}

		return processedResults;
	}

	/**
	 * 创建结果引用信息
	 */
	private String createReferenceInfo(String stepKey, String originalResult, String cacheKey) {
		// 提供结果摘要和缓存引用
		String summary = getSummaryFromResult(originalResult);
		log.info("originalResult.length()：{}，summary.length()：{}", originalResult.length(), summary.length());
		return String.format(
				"**[结果过大已缓存]**\n" + "- 原始结果大小: %d 字符\n" + "- 摘要: %s\n" + "- 完整结果可调用getFullData工具根据缓存键 '%s' 获取\n",
				originalResult.length(), summary, cacheKey);
	}

	/**
	 * 从结果中提取摘要
	 */
	private String getSummaryFromResult(String result) {
		if (result == null || result.isEmpty()) {
			return "无数据";
		}

		// 如果是JSON格式，尝试提取关键字段
		if (result.startsWith("{") || result.startsWith("[")) {
			return extractJsonSummary(result);
		}
		else {
			// 截取前200个字符作为摘要
			return result.length() > 200 ? result.substring(0, 200) + "..." : result;
		}
	}

	/**
	 * 提取JSON结果的摘要
	 */
	private String extractJsonSummary(String jsonResult) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(jsonResult);

			if (rootNode.isObject()) {
				// 检查是否是包含 data 数组的对象结构
				JsonNode dataNode = rootNode.get("data");
				JsonNode columnNode = rootNode.get("column");

				if (dataNode != null && dataNode.isArray() && columnNode != null && columnNode.isArray()) {
					// 这是包含列定义和数据的特殊格式
					int totalRows = dataNode.size();
					int columnsCount = columnNode.size();

					// 获取列名
					StringBuilder columnsBuilder = new StringBuilder();
					for (int i = 0; i < Math.min(columnNode.size(), 10); i++) { // 增加列名显示数量
						if (i > 0)
							columnsBuilder.append(", ");
						columnsBuilder.append(columnNode.get(i).asText());
					}
					String columnsPreview = columnsBuilder.toString();

					// 获取前100条记录的详细内容
					StringBuilder rowsPreview = new StringBuilder();
					int recordsToShow = Math.min(totalRows, 100); // 最多显示100条记录

					for (int i = 0; i < recordsToShow; i++) {
						JsonNode row = dataNode.get(i);
						if (i > 0)
							rowsPreview.append("; ");

						if (row.isArray()) {
							// 如果每行是数组格式
							StringBuilder rowBuilder = new StringBuilder();
							rowBuilder.append("[");
							for (int j = 0; j < row.size(); j++) {
								if (j > 0)
									rowBuilder.append(",");
								String value = row.get(j) != null ? row.get(j).asText() : "null";
								// 限制单个值长度，避免过长
								rowBuilder.append(value.length() > 100 ? value.substring(0, 100) + "..." : value);
							}
							rowBuilder.append("]");
							rowsPreview.append(rowBuilder.toString());
						}
						else if (row.isObject()) {
							// 如果每行是对象格式
							Iterator<Map.Entry<String, JsonNode>> fields = row.fields();
							StringBuilder rowBuilder = new StringBuilder();
							rowBuilder.append("{");
							int fieldCount = 0;
							while (fields.hasNext()) {
								Map.Entry<String, JsonNode> field = fields.next();
								if (fieldCount > 0)
									rowBuilder.append(",");

								String value = field.getValue() != null ? field.getValue().asText() : "null";
								// 限制单个值长度，避免过长
								String truncatedValue = value.length() > 100 ? value.substring(0, 100) + "..." : value;
								rowBuilder.append(field.getKey()).append("=").append(truncatedValue);
								fieldCount++;
							}
							rowBuilder.append("}");
							rowsPreview.append(rowBuilder.toString());
						}
					}

					String additionalInfo = recordsToShow < totalRows
							? String.format(", 还有%d条记录未显示", totalRows - recordsToShow) : "";

					return String.format("表格式数据: %d列×%d行, 列名: [%s], 前%d条记录: %s%s", columnsCount, totalRows,
							columnsPreview, recordsToShow, rowsPreview.toString(), additionalInfo);
				}
				else {
					// 普通对象处理逻辑
					Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
					StringBuilder summary = new StringBuilder();
					int count = 0;
					while (fields.hasNext() && count < 10) { // 显示更多字段
						Map.Entry<String, JsonNode> field = fields.next();
						String fieldName = field.getKey();
						JsonNode fieldValue = field.getValue();

						String valueSummary;
						if (fieldValue.isArray()) {
							valueSummary = String.format("[数组, %d项]", fieldValue.size());
						}
						else if (fieldValue.isObject()) {
							valueSummary = "{对象}";
						}
						else {
							String textValue = fieldValue.asText();
							valueSummary = textValue.length() > 100 ? textValue.substring(0, 100) + "..." : textValue;
						}

						if (count > 0)
							summary.append(", ");
						summary.append(fieldName).append("=").append(valueSummary);
						count++;
					}
					return summary.toString();
				}
			}
			else if (rootNode.isArray()) {
				// 对于纯数组的情况也保留前100条记录
				int totalItems = rootNode.size();
				StringBuilder itemsPreview = new StringBuilder();
				int itemsToShow = Math.min(totalItems, 100);

				for (int i = 0; i < itemsToShow; i++) {
					JsonNode item = rootNode.get(i);
					if (i > 0)
						itemsPreview.append(", ");

					String itemText = item != null ? item.asText() : "null";
					itemsPreview.append(itemText.length() > 100 ? itemText.substring(0, 100) + "..." : itemText);
				}

				String additionalInfo = itemsToShow < totalItems ? String.format(", 还有%d项未显示", totalItems - itemsToShow)
						: "";

				return String.format("数组，共%d个项目，前%d项: [%s]%s", totalItems, itemsToShow, itemsPreview.toString(),
						additionalInfo);
			}
		}
		catch (Exception e) {
			log.warn("JSON parsing failed for summary extraction: {}", e.getMessage());
			// JSON解析失败，返回前200个字符
			return jsonResult.length() > 200 ? jsonResult.substring(0, 200) + "..." : jsonResult;
		}

		return jsonResult.length() > 200 ? jsonResult.substring(0, 200) + "..." : jsonResult;
	}

	/**
	 * Gets the current execution step from the plan.
	 */
	private ExecutionStep getCurrentExecutionStep(Plan plan, Integer currentStep) {
		List<ExecutionStep> executionPlan = plan.getExecutionPlan();
		if (executionPlan == null || executionPlan.isEmpty()) {
			throw new IllegalStateException("Execution plan is empty");
		}

		int stepIndex = currentStep - 1;
		if (stepIndex < 0 || stepIndex >= executionPlan.size()) {
			throw new IllegalStateException("Current step index out of range: " + stepIndex);
		}

		return executionPlan.get(stepIndex);
	}

	/**
	 * Generates the analysis report.
	 */
	private Flux<ChatResponse> generateReport(String userInput, Plan plan, HashMap<String, String> executionResults,
			String summaryAndRecommendations, Long agentId) {
		// Build user requirements and plan description
		String userRequirementsAndPlan = buildUserRequirementsAndPlan(userInput, plan);

		// Build analysis steps and data results description
		String analysisStepsAndData = buildAnalysisStepsAndData(plan, executionResults);

		// Get optimization configs if available (优先按智能体加载)
		List<UserPromptConfig> optimizationConfigs = promptConfigService.getOptimizationConfigs("report-generator",
				agentId);

		String reportPrompt = PromptHelper.buildReportGeneratorPromptWithOptimization(userRequirementsAndPlan,
				analysisStepsAndData, summaryAndRecommendations, optimizationConfigs);
		log.debug("Report Node Prompt: \n {} \n", reportPrompt);
		return llmService.callUser(reportPrompt);
	}

	/**
	 * Builds user requirements and plan description.
	 */
	private String buildUserRequirementsAndPlan(String userInput, Plan plan) {
		StringBuilder sb = new StringBuilder();
		sb.append("## 用户原始需求\n");
		sb.append(userInput).append("\n\n");

		sb.append("## 执行计划概述\n");
		sb.append("**思考过程**: ").append(plan.getThoughtProcess()).append("\n\n");

		sb.append("## 详细执行步骤\n");
		List<ExecutionStep> executionPlan = plan.getExecutionPlan();
		for (int i = 0; i < executionPlan.size(); i++) {
			ExecutionStep step = executionPlan.get(i);
			sb.append("### 步骤 ").append(i + 1).append(": 步骤编号 ").append(step.getStep()).append("\n");
			sb.append("**工具**: ").append(step.getToolToUse()).append("\n");
			if (step.getToolParameters() != null) {
				sb.append("**参数描述**: ").append(step.getToolParameters().getInstruction()).append("\n");
			}
			sb.append("\n");
		}

		return sb.toString();
	}

	/**
	 * Builds analysis steps and data results description.
	 */
	private String buildAnalysisStepsAndData(Plan plan, HashMap<String, String> executionResults) {
		StringBuilder sb = new StringBuilder();
		sb.append("## 数据执行结果\n");

		if (executionResults.isEmpty()) {
			sb.append("暂无执行结果数据\n");
		}
		else {
			List<ExecutionStep> executionPlan = plan.getExecutionPlan();
			for (Map.Entry<String, String> entry : executionResults.entrySet()) {
				String stepKey = entry.getKey();
				String stepResult = entry.getValue();

				sb.append("### ").append(stepKey).append("\n");

				// Try to get corresponding step description
				try {
					int stepIndex = Integer.parseInt(stepKey.replace("step_", "")) - 1;
					if (stepIndex >= 0 && stepIndex < executionPlan.size()) {
						ExecutionStep step = executionPlan.get(stepIndex);
						sb.append("**步骤编号**: ").append(step.getStep()).append("\n");
						sb.append("**使用工具**: ").append(step.getToolToUse()).append("\n");
						if (step.getToolParameters() != null) {
							sb.append("**参数描述**: ").append(step.getToolParameters().getInstruction()).append("\n");
							if (step.getToolParameters().getSqlQuery() != null) {
								sb.append("**执行SQL**: \n```sql\n")
									.append(step.getToolParameters().getSqlQuery())
									.append("\n```\n");
							}
						}
					}
				}
				catch (NumberFormatException e) {
					// Ignore parsing errors
				}

				sb.append("**执行结果摘要**: \n```json\n").append(stepResult).append("\n```\n\n");

				// 添加获取完整数据的指引
				if (stepResult.contains("[结果过大已缓存]")) {
					sb.append("**注意**: 完整数据已缓存，如需查看请调用getFullData工具获取\n\n");
				}
				else {
					sb.append("\n");
				}

			}
		}

		return sb.toString();
	}

}
