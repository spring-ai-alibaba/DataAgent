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

import com.alibaba.cloud.ai.dataagent.dto.prompt.SemanticConsistencyDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.ColumnDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.entity.UserPromptConfig;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.util.ReportTemplateUtil.cleanJsonExample;

public class PromptHelper {

	public static String buildMixSelectorPrompt(String evidence, String question, SchemaDTO schemaDTO) {
		String schemaInfo = buildMixMacSqlDbPrompt(schemaDTO, true);
		Map<String, Object> params = new HashMap<>();
		params.put("schema_info", schemaInfo);
		params.put("question", question);
		if (StringUtils.isBlank(evidence))
			params.put("evidence", "无");
		else
			params.put("evidence", evidence);
		return PromptConstant.getMixSelectorPromptTemplate().render(params);
	}

	public static String buildMixMacSqlDbPrompt(SchemaDTO schemaDTO, Boolean withColumnType) {
		StringBuilder sb = new StringBuilder();
		sb.append("【DB_ID】 ").append(schemaDTO.getName() == null ? "" : schemaDTO.getName()).append("\n");
		for (TableDTO tableDTO : schemaDTO.getTable()) {
			sb.append(buildMixMacSqlTablePrompt(tableDTO, withColumnType)).append("\n");
		}
		if (CollectionUtils.isNotEmpty(schemaDTO.getForeignKeys())) {
			sb.append("【Foreign keys】\n").append(StringUtils.join(schemaDTO.getForeignKeys(), "\n"));
		}
		return sb.toString();
	}

	public static String buildMixMacSqlTablePrompt(TableDTO tableDTO, Boolean withColumnType) {
		StringBuilder sb = new StringBuilder();
		// sb.append("# Table:
		// ").append(tableDTO.getName()).append(StringUtils.isBlank(tableDTO.getDescription())
		// ? "" : ", " + tableDTO.getDescription()).append("\n");
		sb.append("# Table: ").append(tableDTO.getName());
		if (!StringUtils.equals(tableDTO.getName(), tableDTO.getDescription())) {
			sb.append(StringUtils.isBlank(tableDTO.getDescription()) ? "" : ", " + tableDTO.getDescription())
				.append("\n");
		}
		else {
			sb.append("\n");
		}
		sb.append("[\n");
		List<String> columnLines = new ArrayList<>();
		for (ColumnDTO columnDTO : tableDTO.getColumn()) {
			StringBuilder line = new StringBuilder();
			line.append("(")
				.append(columnDTO.getName())
				.append(BooleanUtils.isTrue(withColumnType)
						? ":" + StringUtils.defaultString(columnDTO.getType(), "").toUpperCase(Locale.ROOT) : "");
			if (!StringUtils.equals(columnDTO.getDescription(), columnDTO.getName())) {
				line.append(", ").append(StringUtils.defaultString(columnDTO.getDescription(), ""));
			}
			if (CollectionUtils.isNotEmpty(tableDTO.getPrimaryKeys())
					&& tableDTO.getPrimaryKeys().contains(columnDTO.getName())) {
				line.append(", Primary Key");
			}
			List<String> enumData = Optional.ofNullable(columnDTO.getData())
				.orElse(new ArrayList<>())
				.stream()
				.filter(d -> !StringUtils.isEmpty(d))
				.collect(Collectors.toList());
			if (CollectionUtils.isNotEmpty(enumData) && !"id".equals(columnDTO.getName())) {
				line.append(", Examples: [");
				List<String> data = new ArrayList<>(enumData.subList(0, Math.min(3, enumData.size())));
				line.append(StringUtils.join(data, ",")).append("]");
			}

			line.append(")");
			columnLines.add(line.toString());
		}
		sb.append(StringUtils.join(columnLines, ",\n"));
		sb.append("\n]");
		return sb.toString();
	}

	public static String buildNewSqlGeneratorPrompt(SqlGenerationDTO sqlGenerationDTO) {
		String schemaInfo = buildMixMacSqlDbPrompt(sqlGenerationDTO.getSchemaDTO(), true);
		Map<String, Object> params = new HashMap<>();
		params.put("dialect", sqlGenerationDTO.getDialect());
		params.put("question", sqlGenerationDTO.getQuery());
		params.put("schema_info", schemaInfo);
		params.put("evidence", sqlGenerationDTO.getEvidence());
		params.put("execution_description", sqlGenerationDTO.getExecutionDescription());
		return PromptConstant.getNewSqlGeneratorPromptTemplate().render(params);
	}

	public static String buildSemanticConsistenPrompt(SemanticConsistencyDTO semanticConsistencyDTO) {
		Map<String, Object> params = new HashMap<>();
		params.put("dialect", semanticConsistencyDTO.getDialect());
		params.put("execution_description", semanticConsistencyDTO.getExecutionDescription());
		params.put("user_query", semanticConsistencyDTO.getUserQuery());
		params.put("evidence", semanticConsistencyDTO.getEvidence());
		params.put("schema_info", semanticConsistencyDTO.getSchemaInfo());
		params.put("sql", semanticConsistencyDTO.getSql());
		return PromptConstant.getSemanticConsistencyPromptTemplate().render(params);
	}

	/**
	 * Build report generation prompt with custom prompt
	 * @param userRequirementsAndPlan user requirements and plan
	 * @param analysisStepsAndData analysis steps and data
	 * @param summaryAndRecommendations summary and recommendations
	 * @return built prompt
	 */
	public static String buildReportGeneratorPromptWithOptimization(String userRequirementsAndPlan,
			String analysisStepsAndData, String summaryAndRecommendations, List<UserPromptConfig> optimizationConfigs,
			boolean plainReport) {

		Map<String, Object> params = new HashMap<>();
		params.put("user_requirements_and_plan", userRequirementsAndPlan);
		params.put("analysis_steps_and_data", analysisStepsAndData);
		params.put("summary_and_recommendations", summaryAndRecommendations);
		// html 模板的echarts示例
		if (!plainReport)
			params.put("json_example", cleanJsonExample);

		// Build optional optimization section content from user configs
		String optimizationSection = buildOptimizationSection(optimizationConfigs, params);
		params.put("optimization_section", optimizationSection);

		// Render using the chosen report generator template
		return (plainReport ? PromptConstant.getReportGeneratorPlainPromptTemplate()
				: PromptConstant.getReportGeneratorPromptTemplate())
			.render(params);
	}

	public static String buildSqlErrorFixerPrompt(SqlGenerationDTO sqlGenerationDTO) {
		String schemaInfo = buildMixMacSqlDbPrompt(sqlGenerationDTO.getSchemaDTO(), true);

		Map<String, Object> params = new HashMap<>();
		params.put("dialect", sqlGenerationDTO.getDialect());
		params.put("question", sqlGenerationDTO.getQuery());
		params.put("schema_info", schemaInfo);
		params.put("evidence", sqlGenerationDTO.getEvidence());
		params.put("error_sql", sqlGenerationDTO.getSql());
		params.put("error_message", sqlGenerationDTO.getExceptionMessage());
		params.put("execution_description", sqlGenerationDTO.getExecutionDescription());

		return PromptConstant.getSqlErrorFixerPromptTemplate().render(params);
	}

	public static String buildBusinessKnowledgePrompt(String businessTerms) {
		Map<String, Object> params = new HashMap<>();
		if (StringUtils.isNotBlank(businessTerms))
			params.put("businessKnowledge", businessTerms);
		else
			params.put("businessKnowledge", "无");
		return PromptConstant.getBusinessKnowledgePromptTemplate().render(params);
	}

	// agentKnowledge
	public static String buildAgentKnowledgePrompt(String agentKnowledge) {
		Map<String, Object> params = new HashMap<>();
		if (StringUtils.isNotBlank(agentKnowledge))
			params.put("agentKnowledge", agentKnowledge);
		else
			params.put("agentKnowledge", "无");
		return PromptConstant.getAgentKnowledgePromptTemplate().render(params);
	}

	public static String buildSemanticModelPrompt(List<SemanticModel> semanticModels) {
		Map<String, Object> params = new HashMap<>();
		String semanticModel = CollectionUtils.isEmpty(semanticModels) ? ""
				: semanticModels.stream().map(SemanticModel::getPromptInfo).collect(Collectors.joining(";\n"));
		params.put("semanticModel", semanticModel);
		return PromptConstant.getSemanticModelPromptTemplate().render(params);
	}

	/**
	 * 构建优化提示词部分内容
	 * @param optimizationConfigs 优化配置列表
	 * @param params 模板参数
	 * @return 优化部分的内容
	 */
	private static String buildOptimizationSection(List<UserPromptConfig> optimizationConfigs,
			Map<String, Object> params) {

		if (optimizationConfigs == null || optimizationConfigs.isEmpty()) {
			return "";
		}

		StringBuilder result = new StringBuilder();
		result.append("## 优化要求\n");

		for (UserPromptConfig config : optimizationConfigs) {
			String optimizationContent = renderOptimizationPrompt(config.getOptimizationPrompt(), params);
			if (!optimizationContent.trim().isEmpty()) {
				result.append("- ").append(optimizationContent).append("\n");
			}
		}

		return result.toString().trim();
	}

	/**
	 * 构建意图识别提示词
	 * @param multiTurn 多轮对话历史
	 * @param latestQuery 最新用户输入
	 * @return 意图识别提示词
	 */
	public static String buildIntentRecognitionPrompt(String multiTurn, String latestQuery) {
		Map<String, Object> params = new HashMap<>();
		params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
		params.put("latest_query", latestQuery);
		return PromptConstant.getIntentRecognitionPromptTemplate().render(params);
	}

	/**
	 * 构建查询处理提示词
	 * @param multiTurn 多轮对话历史
	 * @param latestQuery 最新用户输入
	 * @return 查询处理提示词
	 */
	public static String buildQueryEnhancePrompt(String multiTurn, String latestQuery, String evidence) {
		Map<String, Object> params = new HashMap<>();
		params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
		params.put("latest_query", latestQuery);
		if (StringUtils.isEmpty(evidence))
			params.put("evidence", "无");
		else
			params.put("evidence", evidence);
		params.put("current_time_info", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		return PromptConstant.getQueryEnhancementPromptTemplate().render(params);
	}

	/**
	 * 构建可行性评估提示词
	 * @param canonicalQuery 规范化查询
	 * @param recalledSchema 召回的数据库Schema
	 * @param evidence 参考信息
	 * @param multiTurn 多轮对话历史
	 * @return 可行性评估提示词
	 */
	public static String buildFeasibilityAssessmentPrompt(String canonicalQuery, SchemaDTO recalledSchema,
			String evidence, String multiTurn) {
		Map<String, Object> params = new HashMap<>();
		String schemaInfo = buildMixMacSqlDbPrompt(recalledSchema, true);
		params.put("canonical_query", canonicalQuery != null ? canonicalQuery : "");
		params.put("recalled_schema", schemaInfo);
		params.put("evidence", evidence != null ? evidence : "");
		params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
		return PromptConstant.getFeasibilityAssessmentPromptTemplate().render(params);
	}

	/**
	 * 构建查询重写提示词
	 * @param multiTurn 多轮对话历史
	 * @param latestQuery 最新用户输入
	 * @return 查询重写提示词
	 */
	public static String buildEvidenceQueryRewritePrompt(String multiTurn, String latestQuery) {
		Map<String, Object> params = new HashMap<>();
		params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
		params.put("latest_query", latestQuery);
		return PromptConstant.getEvidenceQueryRewritePromptTemplate().render(params);
	}

	/**
	 * 渲染优化提示词模板
	 * @param optimizationPrompt 优化提示词模板
	 * @param params 参数
	 * @return 渲染后的内容
	 */
	private static String renderOptimizationPrompt(String optimizationPrompt, Map<String, Object> params) {
		if (optimizationPrompt == null || optimizationPrompt.trim().isEmpty()) {
			return "";
		}
		try {
			return new PromptTemplate(optimizationPrompt).render(params);
		}
		catch (Exception e) {
			// 如果模板渲染失败，直接返回原始内容
			return optimizationPrompt;
		}
	}

}
