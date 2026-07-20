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
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.util.MarkdownParserUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
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

import static com.alibaba.cloud.ai.dataagent.constant.Constant.EVIDENCE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.FEASIBILITY_ASSESSMENT_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.MULTI_TURN_CONTEXT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_RELATION_OUTPUT;

@Slf4j
@Component
@AllArgsConstructor
public class FeasibilityAssessmentNode implements NodeAction {

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		String canonicalQuery = StateUtil.getCanonicalQuery(state);
		SchemaDTO recalledSchema = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
		String evidence = StateUtil.getStringValue(state, EVIDENCE);
		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		String prompt = PromptHelper.buildFeasibilityAssessmentPrompt(canonicalQuery, recalledSchema, evidence,
				multiTurn);
		log.debug("Built feasibility assessment prompt as follows \n {} \n", prompt);

		Flux<ChatResponse> responseFlux = llmService.callUser(prompt, state);

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(this.getClass(), state,
				responseFlux,
				Flux.just(ChatResponseUtil.createResponse("正在进行可行性评估..."),
						ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
						ChatResponseUtil.createResponse("\n可行性评估完成！")),
				this::handleAssessmentResult);
		return Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, generator);
	}

	private Map<String, Object> handleAssessmentResult(String llmOutput) {
		String assessmentResult = MarkdownParserUtil.extractRawText(llmOutput.trim());
		log.info("Feasibility assessment result: {}", assessmentResult);
		FeasibilityAssessmentOutputDTO dto = jsonParseUtil.tryConvertToObject(assessmentResult,
				FeasibilityAssessmentOutputDTO.class);
		return Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, dto);
	}

}
