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

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.prompt.PromptConstant;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.MarkdownParserUtil;
import com.alibaba.cloud.ai.dataagent.util.PlanProcessUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 生成Python代码的节点
 *
 * @author vlsmb
 * @since 2025/7/30
 */
@Slf4j
@Component
public class PythonGenerateNode implements NodeAction {

	private static final int SAMPLE_DATA_NUMBER = 5;

	private final ObjectMapper objectMapper;

	private final CodeExecutorProperties codeExecutorProperties;

	private final LlmService llmService;

	public PythonGenerateNode(CodeExecutorProperties codeExecutorProperties, LlmService llmService) {
		this.codeExecutorProperties = codeExecutorProperties;
		this.llmService = llmService;
		this.objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// Get context
		SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
		List<Map<String, String>> sqlResults = StateUtil.hasValue(state, SQL_RESULT_LIST_MEMORY)
				? StateUtil.getListValue(state, SQL_RESULT_LIST_MEMORY) : new ArrayList<>();
		boolean codeRunSuccess = StateUtil.getObjectValue(state, PYTHON_IS_SUCCESS, Boolean.class, true);
		int triesCount = StateUtil.getObjectValue(state, PYTHON_TRIES_COUNT, Integer.class, 0);

		String userPrompt = StateUtil.getCanonicalQuery(state);
		if (!codeRunSuccess) {
			// Last generated Python code failed to run, inform AI model of this
			// information
			String lastCode = StateUtil.getStringValue(state, PYTHON_GENERATE_NODE_OUTPUT);
			String lastError = StateUtil.getStringValue(state, PYTHON_EXECUTE_NODE_OUTPUT);
			userPrompt += String.format("""
					上次尝试生成的Python代码运行失败，请你重新生成符合要求的Python代码。
					【上次生成代码】
					```python
					%s
					```
					【运行错误信息】
					```
					%s
					```
					""", lastCode, lastError);
		}

		ExecutionStep executionStep = PlanProcessUtil.getCurrentExecutionStep(state);

		ExecutionStep.ToolParameters toolParameters = executionStep.getToolParameters();

		// Load Python code generation template
		String systemPrompt = PromptConstant.getPythonGeneratorPromptTemplate()
			.render(Map.of("python_memory", codeExecutorProperties.getLimitMemory().toString(), "python_timeout",
					codeExecutorProperties.getCodeTimeout(), "database_schema",
					objectMapper.writeValueAsString(schemaDTO), "sample_input",
					objectMapper.writeValueAsString(sqlResults.stream().limit(SAMPLE_DATA_NUMBER).toList()),
					"plan_description", objectMapper.writeValueAsString(toolParameters)));

		Flux<ChatResponse> pythonGenerateFlux = llmService.call(systemPrompt, userPrompt);

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, aiResponse -> {
					// Some AI models still output Markdown markup (even though Prompt has
					// emphasized this)
					aiResponse = aiResponse.substring(TextType.PYTHON.getStartSign().length(),
							aiResponse.length() - TextType.PYTHON.getEndSign().length());
					aiResponse = MarkdownParserUtil.extractRawText(aiResponse);
					log.info("Python Generate Code: {}", aiResponse);
					return Map.of(PYTHON_GENERATE_NODE_OUTPUT, aiResponse, PYTHON_TRIES_COUNT, triesCount + 1);
				},
				Flux.concat(Flux.just(ChatResponseUtil.createPureResponse(TextType.PYTHON.getStartSign())),
						pythonGenerateFlux,
						Flux.just(ChatResponseUtil.createPureResponse(TextType.PYTHON.getEndSign()))));

		return Map.of(PYTHON_GENERATE_NODE_OUTPUT, generator);
	}

}
