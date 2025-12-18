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

import com.alibaba.cloud.ai.dataagent.common.enums.TextType;
import com.alibaba.cloud.ai.dataagent.common.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.common.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.common.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.common.util.MarkdownParserUtil;
import com.alibaba.cloud.ai.dataagent.common.util.StateUtil;
import com.alibaba.cloud.ai.dataagent.dto.prompt.GuessOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.prompt.PromptLoader;
import org.springframework.ai.chat.prompt.PromptTemplate;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.common.constant.Constant.*;

/**
 * 推测用户可能提问的下一个问题的节点
 *
 * @author fudawei
 */
@Slf4j
@Component
@AllArgsConstructor
public class GuessNode implements NodeAction {

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 获取输入信息
		SchemaDTO schemaDTO = StateUtil.hasValue(state, TABLE_RELATION_OUTPUT)
				? StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class)
				: null;
		String canonicalQuery = StateUtil.getCanonicalQuery(state);
		String multiTurnContext = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		log.info("GuessNode: schema tables count: {}, canonical query: {}", 
				schemaDTO != null && schemaDTO.getTable() != null ? schemaDTO.getTable().size() : 0, 
				canonicalQuery);

		// 格式化 schema 信息
		String schemaStr = schemaDTO != null ? PromptHelper.buildMixMacSqlDbPrompt(schemaDTO, true) : "(无)";
		
		// 从多轮对话上下文中提取历史提问
		// 这里简化处理，将多轮对话上下文作为历史提问
		// 如果需要更精确的提取，可以解析 MULTI_TURN_CONTEXT 格式
		String oldQuestions = extractOldQuestions(multiTurnContext);

		// 加载完整的提示词文件
		String fullPrompt = PromptLoader.loadPrompt("guess");
		
		// 分割系统提示词和用户提示词模板
		// 系统提示词：从开头到 "---" 之前
		String[] parts = fullPrompt.split("---", 2);
		String systemPromptTemplate = parts[0].trim();

		// 默认推测 4 个问题，后续可以根据配置调整
		int articlesNumber = 4;
		
		// 渲染系统提示词（替换 articles_number）
		PromptTemplate systemTemplate = new PromptTemplate(systemPromptTemplate);
		String systemPrompt = systemTemplate.render(Map.of("articles_number", String.valueOf(articlesNumber)));
		
		// 在代码中拼接用户提示词（参考 guess.txt 107-120 行的格式）
		String userPrompt = String.format("""
				# 正式任务
				
				【表结构】
				%s
				
				【当前问题】
				%s
				
				【以往提问】
				%s
				
				# 输出
				""", 
				schemaStr != null ? schemaStr : "(无)",
				canonicalQuery != null ? canonicalQuery : "(无)",
				oldQuestions
		);

		log.debug("Built guess system prompt as follows \n {} \n", systemPrompt);
		log.debug("Built guess user prompt as follows \n {} \n", userPrompt);

		// 调用 LLM 进行问题推测（使用系统提示词和用户提示词）
		Flux<ChatResponse> responseFlux = llmService.call(systemPrompt, userPrompt);

		// 创建流式生成器
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(
				this.getClass(), 
				state,
				responseFlux,
				Flux.just(ChatResponseUtil.createResponse("正在推测可能的问题..."),
						ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
						ChatResponseUtil.createResponse("\n问题推测完成！")),
				this::handleGuessResult);

		return Map.of(GUESS_NODE_OUTPUT, generator);
	}

	/**
	 * 处理推测结果
	 */
	private Map<String, Object> handleGuessResult(String llmOutput) {
		// 提取原始文本
		String guessResult = MarkdownParserUtil.extractRawText(llmOutput.trim());
		log.info("Guess result: {}", guessResult);

		// 解析 JSON 结果
		GuessOutputDTO guessOutputDTO = null;
		try {
			guessOutputDTO = jsonParseUtil.tryConvertToObject(guessResult, GuessOutputDTO.class);
			log.info("Successfully parsed guess result, questions count: {}", 
					guessOutputDTO != null && guessOutputDTO.getQuestions() != null 
							? guessOutputDTO.getQuestions().size() : 0);
		}
		catch (Exception e) {
			log.error("Failed to parse guess result: {}", guessResult, e);
		}

		if (guessOutputDTO == null) {
			// 如果解析失败，返回空结果
			guessOutputDTO = new GuessOutputDTO();
		}

		// 返回处理结果
		return Map.of(GUESS_NODE_OUTPUT, guessOutputDTO);
	}

	/**
	 * 从多轮对话上下文中提取历史提问
	 * 这里简化处理，如果需要更精确的提取，可以解析 MULTI_TURN_CONTEXT 的具体格式
	 */
	private String extractOldQuestions(String multiTurnContext) {
		if (multiTurnContext == null || multiTurnContext.trim().isEmpty() || "(无)".equals(multiTurnContext)) {
			return "(无)";
		}

		// 简化处理：直接返回多轮对话上下文
		// 如果需要更精确的提取，可以解析 MULTI_TURN_CONTEXT 格式，提取历史问题列表
		// 例如：从格式化的对话历史中提取用户的问题
		return multiTurnContext;
	}


}
