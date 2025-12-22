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
import com.alibaba.cloud.ai.dataagent.common.util.JsonParseUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorService;
import com.alibaba.cloud.ai.dataagent.common.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.common.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.common.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.common.util.StateUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.common.constant.Constant.*;

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

	public PythonExecuteNode(CodePoolExecutorService codePoolExecutor, JsonParseUtil jsonParseUtil) {
		this.codePoolExecutor = codePoolExecutor;
		this.objectMapper = JsonUtil.getObjectMapper();
		this.jsonParseUtil = jsonParseUtil;
	}

	private static final String CHART_DIR = "/tmp/dataagent_charts";

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		try {
			// Clean up old charts before execution
			cleanChartDirectory();
			
			// Get context
			String pythonCode = StateUtil.getStringValue(state, PYTHON_GENERATE_NODE_OUTPUT);
			List<Map<String, String>> sqlResults = StateUtil.hasValue(state, SQL_RESULT_LIST_MEMORY)
					? StateUtil.getListValue(state, SQL_RESULT_LIST_MEMORY) : new ArrayList<>();
			CodePoolExecutorService.TaskRequest taskRequest = new CodePoolExecutorService.TaskRequest(pythonCode,
					objectMapper.writeValueAsString(sqlResults), null);

			// Run Python code
			CodePoolExecutorService.TaskResponse taskResponse = this.codePoolExecutor.runTask(taskRequest);
			if (!taskResponse.isSuccess()) {
				String errorMsg = "Python Execute Failed!\nStdOut: " + taskResponse.stdOut() + "\nStdErr: "
						+ taskResponse.stdErr() + "\nExceptionMsg: " + taskResponse.exceptionMsg();
				log.error(errorMsg);
				throw new RuntimeException(errorMsg);
			}

			// Python输出的JSON字符串可能有Unicode转义形式，需要解析回汉字
			String stdout = taskResponse.stdOut();
			Object value = jsonParseUtil.tryConvertToObject(stdout, Object.class);
			if (value != null) {
				stdout = objectMapper.writeValueAsString(value);
			}
			String finalStdout = stdout;

			List<String> chartImages = readChartImages();

			log.info("Python Execute Success! StdOut: {}, Chart Count: {}", finalStdout, chartImages.size());

			// Create display flux for user experience only
			Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
				emitter.next(ChatResponseUtil.createResponse("开始执行Python代码..."));
				emitter.next(ChatResponseUtil.createResponse("标准输出："));
				emitter.next(ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()));
				emitter.next(ChatResponseUtil.createResponse(finalStdout));
				emitter.next(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()));
				
				// Output charts if any
				if (!chartImages.isEmpty()) {
					for (int i = 0; i < chartImages.size(); i++) {
						emitter.next(ChatResponseUtil.createResponse("生成的图表 " + (i + 1) + "："));
						emitter.next(ChatResponseUtil.createPureResponse(TextType.IMAGE.getStartSign()));
						emitter.next(ChatResponseUtil.createResponse(chartImages.get(i)));
						emitter.next(ChatResponseUtil.createPureResponse(TextType.IMAGE.getEndSign()));
					}
				}
				
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

	/**
	 * Clean chart directory before execution
	 */
	private void cleanChartDirectory() {
		try {
			Path chartPath = Paths.get(CHART_DIR);
			if (Files.exists(chartPath)) {
				Files.walk(chartPath)
					.filter(Files::isRegularFile)
					.forEach(file -> {
						try {
							Files.deleteIfExists(file);
						}
						catch (Exception e) {
							log.warn("Failed to delete chart file: {}", file, e);
						}
					});
			}
			else {
				Files.createDirectories(chartPath);
			}
		}
		catch (Exception e) {
			log.warn("Failed to clean chart directory", e);
		}
	}

	/**
	 * Read generated chart images from file system and convert to Base64
	 */
	private List<String> readChartImages() {
		List<String> chartImages = new ArrayList<>();
		try {
			// Try multiple possible paths for cross-platform compatibility
			Path[] possiblePaths = {
				Paths.get(CHART_DIR),  // Linux/Mac: /tmp/dataagent_charts
				Paths.get(System.getProperty("java.io.tmpdir"), "dataagent_charts"),  // Windows: %TEMP%/dataagent_charts
				Paths.get("C:/tmp/dataagent_charts"),  // Windows alternative
			};

			for (Path chartPath : possiblePaths) {
				if (Files.exists(chartPath)) {
					// List all PNG files in the directory
					Files.list(chartPath)
						.filter(path -> path.toString().toLowerCase().endsWith(".png"))
						.sorted()
						.forEach(imagePath -> {
							try {
								byte[] imageBytes = Files.readAllBytes(imagePath);
								String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
								chartImages.add(base64Image);
								log.info("Read chart image: {}, size: {} bytes", imagePath.getFileName(),
										imageBytes.length);
							}
							catch (Exception e) {
								log.error("Failed to read chart image: {}", imagePath, e);
							}
						});
					// If we found charts in one path, don't check others
					if (!chartImages.isEmpty()) {
						break;
					}
				}
			}
		}
		catch (Exception e) {
			log.error("Failed to read chart images from directory", e);
		}
		return chartImages;
	}

}
