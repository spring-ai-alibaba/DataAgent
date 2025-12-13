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

import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.common.enums.TextType;
import com.alibaba.cloud.ai.dataagent.common.util.JsonParseUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.common.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.common.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.common.util.StateUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.common.constant.Constant.*;

/**
 * 查询丰富节点，用于根据evidence信息把业务翻译。查询改写、分解和关键词提取
 */
@Slf4j
@Component
@AllArgsConstructor
public class QueryEnhanceNode implements NodeAction {

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 获取用户输入
		String userInput = StateUtil.getStringValue(state, INPUT_KEY);
		log.info("User input for query enhance: {}", userInput);

		String evidence = StateUtil.getStringValue(state, EVIDENCE);
		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		// 构建查询处理提示
		String prompt = PromptHelper.buildQueryEnhancePrompt(multiTurn, userInput, evidence);
		log.debug("Built query enhance prompt as follows \n {} \n", prompt);

		// 调用LLM进行查询处理
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(this.getClass(), state,
				responseFlux,
				Flux.just(ChatResponseUtil.createResponse("正在进行问题增强..."),
						ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
						ChatResponseUtil.createResponse("\n问题增强完成！")),
				this::handleQueryEnhance);

		return Map.of(QUERY_ENHANCE_NODE_OUTPUT, generator);
	}

	private Map<String, Object> handleQueryEnhance(String llmOutput) {
		// 获取处理结果
		String enhanceResult = llmOutput.trim();
		log.info("Query enhance result: {}", enhanceResult);

		// 解析处理结果，转成 QueryProcessOutputDTO
		QueryEnhanceOutputDTO queryEnhanceOutputDTO = null;
		try {
			queryEnhanceOutputDTO = jsonParseUtil.tryConvertToObject(enhanceResult, QueryEnhanceOutputDTO.class);
			log.info("Successfully parsed query enhance result: {}", queryEnhanceOutputDTO);
		}
		catch (Exception e) {
			log.error("Failed to parse query enhance result: {}", enhanceResult, e);
		}

		if (queryEnhanceOutputDTO == null)
			return Map.of();
		// 返回处理结果
		return Map.of(QUERY_ENHANCE_NODE_OUTPUT, queryEnhanceOutputDTO);
	}

}
