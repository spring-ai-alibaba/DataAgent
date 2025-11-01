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

package com.alibaba.cloud.ai.node;

import com.alibaba.cloud.ai.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.pojo.ExecutionStep;
import com.alibaba.cloud.ai.prompt.PromptHelper;
import com.alibaba.cloud.ai.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.util.FluxUtil;
import com.alibaba.cloud.ai.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.constant.Constant.EVIDENCES;
import static com.alibaba.cloud.ai.constant.Constant.PLAN_CURRENT_STEP;
import static com.alibaba.cloud.ai.constant.Constant.SEMANTIC_CONSISTENCY_NODE_OUTPUT;
import static com.alibaba.cloud.ai.constant.Constant.SEMANTIC_CONSISTENCY_NODE_RECOMMEND_OUTPUT;
import static com.alibaba.cloud.ai.constant.Constant.TABLE_RELATION_OUTPUT;

/**
 * Semantic consistency validation node that checks SQL query semantic consistency.
 *
 * This node is responsible for: - Validating SQL query semantic consistency against
 * schema and evidence - Providing validation results for query refinement - Handling
 * validation failures with recommendations - Managing step progression in execution plan
 *
 * @author zhangshenghang
 */
@Slf4j
@Component
public class SemanticConsistencyNode extends AbstractPlanBasedNode {

	private final Nl2SqlService nl2SqlService;

	public SemanticConsistencyNode(Nl2SqlService nl2SqlService) {
		super();
		this.nl2SqlService = nl2SqlService;
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		logNodeEntry();

		// Get necessary input parameters
		List<String> evidenceList = StateUtil.getListValue(state, EVIDENCES);
		SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);

		// Get current execution step and SQL query
		ExecutionStep executionStep = getCurrentExecutionStep(state);
		Integer currentStep = getCurrentStepNumber(state);
		ExecutionStep.ToolParameters toolParameters = executionStep.getToolParameters();
		String sqlQuery = toolParameters.getSqlQuery();

		log.info("Starting semantic consistency validation - SQL: {}", sqlQuery);
		log.info("Step description: {}", toolParameters.getDescription());

		Flux<ChatResponse> validationResultFlux = performSemanticValidationStream(schemaDTO, evidenceList,
				toolParameters, sqlQuery);

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "开始语义一致性校验", "语义一致性校验完成", validationResult -> {
					boolean isPassed = !validationResult.startsWith("不通过");
					Map<String, Object> result = buildValidationResult(isPassed, validationResult, currentStep);
					log.info("[{}] Semantic consistency validation result: {}, passed: {}",
							this.getClass().getSimpleName(), validationResult, isPassed);
					return result;
				}, validationResultFlux);

		return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, generator);
	}

	/**
	 * Perform streaming semantic consistency validation
	 */
	private Flux<ChatResponse> performSemanticValidationStream(SchemaDTO schemaDTO, List<String> evidenceList,
			ExecutionStep.ToolParameters toolParameters, String sqlQuery) throws Exception {
		// Build validation context
		String schema = PromptHelper.buildMixMacSqlDbPrompt(schemaDTO, true);
		String evidence = StringUtils.join(evidenceList, ";\n");
		String context = String.join("\n", schema, evidence, toolParameters.getDescription());

		// Execute semantic consistency check
		return nl2SqlService.semanticConsistencyStream(sqlQuery, context);
	}

	/**
	 * Build validation result
	 */
	private Map<String, Object> buildValidationResult(boolean passed, String validationResult, Integer currentStep) {
		if (passed) {
			return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, true, PLAN_CURRENT_STEP, currentStep + 1);
		}
		else {
			return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, false, SEMANTIC_CONSISTENCY_NODE_RECOMMEND_OUTPUT,
					validationResult);
		}
	}

}
