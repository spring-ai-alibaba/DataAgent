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

import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SemanticConsistencyDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.util.PlanProcessUtil.getCurrentExecutionStepInstruction;
import static com.alibaba.cloud.ai.dataagent.prompt.PromptHelper.buildMixMacSqlDbPrompt;

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
@AllArgsConstructor
public class SemanticConsistencyNode implements NodeAction {

	private final Nl2SqlService nl2SqlService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// Get necessary input parameters
		String evidence = StateUtil.getStringValue(state, EVIDENCE);
		SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
		String dialect = StateUtil.getStringValue(state, DB_DIALECT_TYPE);
		// Get current execution step and SQL query
		String sql = StateUtil.getStringValue(state, SQL_GENERATE_OUTPUT);
		String userQuery = StateUtil.getCanonicalQuery(state);

		SemanticConsistencyDTO semanticConsistencyDTO = SemanticConsistencyDTO.builder()
			.dialect(dialect)
			.sql(sql)
			.executionDescription(getCurrentExecutionStepInstruction(state))
			.schemaInfo(buildMixMacSqlDbPrompt(schemaDTO, true))
			.userQuery(userQuery)
			.evidence(evidence)
			.build();
		log.info("Starting semantic consistency validation - SQL: {}", sql);
		Flux<ChatResponse> validationResultFlux = nl2SqlService.performSemanticConsistency(semanticConsistencyDTO);

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "开始语义一致性校验", "语义一致性校验完成", validationResult -> {
					boolean isPassed = !validationResult.startsWith("不通过");
					Map<String, Object> result = buildValidationResult(isPassed, validationResult);
					log.info("[{}] Semantic consistency validation result: {}, passed: {}",
							this.getClass().getSimpleName(), validationResult, isPassed);
					return result;
				}, validationResultFlux);

		return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, generator);
	}

	/**
	 * Build validation result
	 */
	private Map<String, Object> buildValidationResult(boolean passed, String validationResult) {
		if (passed) {
			return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, true);
		}
		else {
			return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, false, SQL_REGENERATE_REASON,
					SqlRetryDto.semantic(validationResult));
		}
	}

}
