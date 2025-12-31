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

import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
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
 * 根据SQL查询结果生成Python代码，并运行Python代码获取运行结果。
 *
 * @author vlsmb
 * @since 2025/7/29
 */
@Slf4j
@Component
public class PythonExecuteNode implements NodeAction {

	private final CodePoolExecutorService codePoolExecutor;

	private final ObjectMapper objectMapper;

	private final JsonParseUtil jsonParseUtil;

	private final CodeExecutorProperties codeExecutorProperties;

	public PythonExecuteNode(CodePoolExecutorService codePoolExecutor, JsonParseUtil jsonParseUtil,
			CodeExecutorProperties codeExecutorProperties) {
		this.codePoolExecutor = codePoolExecutor;
		this.objectMapper = JsonUtil.getObjectMapper();
		this.jsonParseUtil = jsonParseUtil;
		this.codeExecutorProperties = codeExecutorProperties;
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		try {
			// Get context
			String pythonCode = StateUtil.getStringValue(state, PYTHON_GENERATE_NODE_OUTPUT);
			List<Map<String, String>> sqlResults = StateUtil.hasValue(state, SQL_RESULT_LIST_MEMORY)
					? StateUtil.getListValue(state, SQL_RESULT_LIST_MEMORY) : new ArrayList<>();

			// 检查重试次数
			int triesCount = StateUtil.getObjectValue(state, PYTHON_TRIES_COUNT, Integer.class, 0);

			CodePoolExecutorService.TaskRequest taskRequest = new CodePoolExecutorService.TaskRequest(pythonCode,
					objectMapper.writeValueAsString(sqlResults), null);

			// Run Python code
			CodePoolExecutorService.TaskResponse taskResponse = this.codePoolExecutor.runTask(taskRequest);
			if (!taskResponse.isSuccess()) {
				String errorMsg = "Python Execute Failed!\nStdOut: " + taskResponse.stdOut() + "\nStdErr: "
						+ taskResponse.stdErr() + "\nExceptionMsg: " + taskResponse.exceptionMsg();
				log.error(errorMsg);

				// 检查是否超过最大重试次数
				if (triesCount >= codeExecutorProperties.getPythonMaxTriesCount()) {
					log.error("Python执行失败且已超过最大重试次数（已尝试次数：{}），启动降级兜底逻辑。错误信息: {}", triesCount, errorMsg);

					String fallbackOutput = "{}";

					Flux<ChatResponse> fallbackDisplayFlux = Flux.create(emitter -> {
						emitter.next(ChatResponseUtil.createResponse("开始执行Python代码..."));
						emitter.next(ChatResponseUtil.createResponse("Python代码执行失败已超过最大重试次数，采用降级策略继续处理。"));
						emitter.complete();
					});

					Flux<GraphResponse<StreamingOutput>> fallbackGenerator = FluxUtil
						.createStreamingGeneratorWithMessages(this.getClass(), state,
								v -> Map.of(PYTHON_EXECUTE_NODE_OUTPUT, fallbackOutput, PYTHON_IS_SUCCESS, false,
										PYTHON_FALLBACK_MODE, true),
								fallbackDisplayFlux);

					return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, fallbackGenerator);
				}

				throw new RuntimeException(errorMsg);
			}

			// Python输出的JSON字符串可能有Unicode转义形式，需要解析回汉字
			String stdout = taskResponse.stdOut();
			Object value = jsonParseUtil.tryConvertToObject(stdout, Object.class);
			if (value != null) {
				stdout = objectMapper.writeValueAsString(value);
			}
			String finalStdout = stdout;

			log.info("Python Execute Success! StdOut: {}", finalStdout);

			// Create display flux for user experience only
			Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
				emitter.next(ChatResponseUtil.createResponse("开始执行Python代码..."));
				emitter.next(ChatResponseUtil.createResponse("标准输出："));
				emitter.next(ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()));
				emitter.next(ChatResponseUtil.createResponse(finalStdout));
				emitter.next(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()));
				emitter.next(ChatResponseUtil.createResponse("Python代码执行成功！"));
				emitter.complete();
			});

			// Create generator using utility class, returning pre-computed business logic
			// result
			Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(
					this.getClass(), state,
					v -> Map.of(PYTHON_EXECUTE_NODE_OUTPUT, finalStdout, PYTHON_IS_SUCCESS, true), displayFlux);

			return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, generator);
		}
		catch (Exception e) {
			String errorMessage = e.getMessage();
			log.error("Python Execute Exception: {}", errorMessage);

			// Prepare error result
			Map<String, Object> errorResult = Map.of(PYTHON_EXECUTE_NODE_OUTPUT, errorMessage, PYTHON_IS_SUCCESS,
					false);

			// Create error display flux
			Flux<ChatResponse> errorDisplayFlux = Flux.create(emitter -> {
				emitter.next(ChatResponseUtil.createResponse("开始执行Python代码..."));
				emitter.next(ChatResponseUtil.createResponse("Python代码执行失败: " + errorMessage));
				emitter.complete();
			});

			// Create error generator using utility class
			var generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(), state, v -> errorResult,
					errorDisplayFlux);

			return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, generator);
		}
	}

}
