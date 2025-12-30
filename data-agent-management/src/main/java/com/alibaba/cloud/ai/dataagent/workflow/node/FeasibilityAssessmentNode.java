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
package com.alibaba.cloud.ai.dataagent.workflow.node;

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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

// 可行性评估节点，看需求是 数据分析/需要澄清 或者最终确认为自由闲聊
@Slf4j
@Component
@AllArgsConstructor
public class FeasibilityAssessmentNode implements NodeAction {

	private final LlmService llmService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		// 获取canonical_query
		String canonicalQuery = StateUtil.getCanonicalQuery(state);

		// 获取召回的Schema
		SchemaDTO recalledSchema = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);

		// 获取证据信息
		String evidence = StateUtil.getStringValue(state, EVIDENCE);

		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		// 构建可行性评估提示词
		String prompt = PromptHelper.buildFeasibilityAssessmentPrompt(canonicalQuery, recalledSchema, evidence,
				multiTurn);
		log.debug("Built feasibility assessment prompt as follows \n {} \n", prompt);

		// 调用LLM进行可行性评估
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "正在进行可行性评估...", "可行性评估完成！", llmOutput -> {
					// 获取评估结果
					String assessmentResult = llmOutput.trim();
					log.info("Feasibility assessment result: {}", assessmentResult);
					// 返回评估结果
					return Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, assessmentResult);
				}, responseFlux);
		return Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, generator);
	}

}
