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

import com.alibaba.cloud.ai.dataagent.dto.prompt.FeasibilityAssessmentOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.HashMap;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

// 可行性评估节点，看需求是 数据分析/需要澄清 或者最终确认为自由闲聊
@Slf4j
@Component
@AllArgsConstructor
public class FeasibilityAssessmentNode implements NodeAction {

	private static final BeanOutputConverter<FeasibilityAssessmentOutputDTO> OUTPUT_CONVERTER = new BeanOutputConverter<>(
			FeasibilityAssessmentOutputDTO.class);

	private final LlmService llmService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		// 获取canonical_query
		String canonicalQuery = StateUtil.getCanonicalQuery(state);

		// 获取召回的Schema
		SchemaDTO recalledSchema = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);

		// 获取证据信息
		String evidence = StateUtil.getStringValue(state, EVIDENCE);

		// 获取语义模型（TableRelationNode 已计算）
		String semanticModel = (String) state.value(GENEGRATED_SEMANTIC_MODEL_PROMPT).orElse("");

		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		// 构建可行性评估提示词
		String prompt = PromptHelper.buildFeasibilityAssessmentPrompt(canonicalQuery, recalledSchema, evidence,
				semanticModel, multiTurn);
		log.debug("Built feasibility assessment prompt as follows \n {} \n", prompt);

		// 调用LLM进行可行性评估
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt, FeasibilityAssessmentOutputDTO.class);

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "正在进行可行性评估...", "可行性评估完成！", llmOutput -> {
					FeasibilityAssessmentOutputDTO assessmentResult = OUTPUT_CONVERTER.convert(llmOutput);
					log.debug("Feasibility assessment result: {}", assessmentResult);
					Map<String, Object> output = new HashMap<>();
					output.put(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, assessmentResult);
					if (assessmentResult
						.getRequirementType() != FeasibilityAssessmentOutputDTO.RequirementType.DATA_ANALYSIS
							&& org.springframework.util.StringUtils.hasText(assessmentResult.getContent())) {
						output.put(FINAL_ANSWER, assessmentResult.getContent().trim());
					}
					return output;
				}, responseFlux);
		return Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, generator);
	}

}
