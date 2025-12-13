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

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.prompt.PromptConstant;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.common.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.common.util.PlanProcessUtil;
import com.alibaba.cloud.ai.dataagent.common.util.StateUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.common.constant.Constant.*;

/**
 * 根据Python代码的运行结果做总结分析
 *
 * @author vlsmb
 * @since 2025/7/30
 */
@Slf4j
@Component
@AllArgsConstructor
public class PythonAnalyzeNode implements NodeAction {

	private final LlmService llmService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// Get context
		String userQuery = StateUtil.getCanonicalQuery(state);
		String pythonOutput = StateUtil.getStringValue(state, PYTHON_EXECUTE_NODE_OUTPUT);
		int currentStep = PlanProcessUtil.getCurrentStepNumber(state);
		@SuppressWarnings("unchecked")
		Map<String, String> sqlExecuteResult = StateUtil.getObjectValue(state, SQL_EXECUTE_NODE_OUTPUT, Map.class,
				new HashMap<>());

		// Load Python code generation template
		String systemPrompt = PromptConstant.getPythonAnalyzePromptTemplate()
			.render(Map.of("python_output", pythonOutput, "user_query", userQuery));

		Flux<ChatResponse> pythonAnalyzeFlux = llmService.callSystem(systemPrompt);

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "正在分析代码运行结果...\n", "\n结果分析完成。", aiResponse -> {
					Map<String, String> updatedSqlResult = PlanProcessUtil.addStepResult(sqlExecuteResult, currentStep,
							aiResponse);
					log.info("python analyze result: {}", aiResponse);
					return Map.of(SQL_EXECUTE_NODE_OUTPUT, updatedSqlResult, PLAN_CURRENT_STEP, currentStep + 1);
				}, pythonAnalyzeFlux);

		return Map.of(PYTHON_ANALYSIS_NODE_OUTPUT, generator);
	}

}
