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
import com.alibaba.cloud.ai.dataagent.service.graph.Context.ClarificationContextManager;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.AWAITING_CLARIFICATION;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.CLARIFICATION_COUNT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.CLARIFICATION_QUESTION;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.FEASIBILITY_ASSESSMENT_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.ORIGINAL_USER_QUERY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.REFINED_USER_QUERY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TRACE_THREAD_ID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClarificationNode implements NodeAction {

	private static final int MAX_CLARIFICATION_ROUNDS = 4;

	private final ClarificationContextManager clarificationContextManager;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		FeasibilityAssessmentOutputDTO assessment = StateUtil.getObjectValue(state, FEASIBILITY_ASSESSMENT_NODE_OUTPUT,
				FeasibilityAssessmentOutputDTO.class);
		String originalQuery = StateUtil.getStringValue(state, ORIGINAL_USER_QUERY,
				StateUtil.getStringValue(state, INPUT_KEY));
		String refinedQuery = StateUtil.getStringValue(state, REFINED_USER_QUERY,
				StateUtil.getStringValue(state, INPUT_KEY));
		int clarificationCount = StateUtil.getObjectValue(state, CLARIFICATION_COUNT, Integer.class, 0);
		String threadId = StateUtil.getStringValue(state, TRACE_THREAD_ID, "");

		boolean canContinueClarification = clarificationCount < MAX_CLARIFICATION_ROUNDS;
		String responseText;
		if (canContinueClarification) {
			responseText = assessment.getContent();
			clarificationContextManager.startClarification(threadId, originalQuery, responseText, clarificationCount + 1);
			log.info("Clarification required for threadId={}, count={}", threadId, clarificationCount + 1);
		}
		else {
			responseText = "为避免继续误解，请你一次性补充完整查询条件，例如时间范围、统计对象、维度或口径后，我再继续分析。";
			clarificationContextManager.clear(threadId);
			log.info("Clarification limit reached for threadId={}, ending clarification flow", threadId);
		}

		Map<String, Object> result = Map.of(
				ORIGINAL_USER_QUERY, originalQuery,
				REFINED_USER_QUERY, refinedQuery,
				CLARIFICATION_QUESTION, responseText,
				CLARIFICATION_COUNT, canContinueClarification ? clarificationCount + 1 : clarificationCount,
				AWAITING_CLARIFICATION, canContinueClarification);

		Flux<ChatResponse> responseFlux = Flux.just(ChatResponseUtil.createResponse(responseText));
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, ignored -> result, responseFlux);
		return Map.of(CLARIFICATION_QUESTION, generator);
	}

}
