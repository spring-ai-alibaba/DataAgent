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

package com.alibaba.cloud.ai.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.service.processing.QueryProcessingService;
import com.alibaba.cloud.ai.util.FluxUtil;
import com.alibaba.cloud.ai.util.StateUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.constant.Constant.QUERY_REWRITE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.constant.Constant.RESULT;

/**
 * Query rewriting and intent clarification node to improve intent understanding accuracy.
 *
 * This node is responsible for: - Rewriting user queries to clarify intent - Improving
 * understanding accuracy through query transformation - Providing streaming feedback
 * during rewriting process
 *
 * @author zhangshenghang
 */
@Slf4j
@Component
@AllArgsConstructor
public class QueryRewriteNode implements NodeAction {

	private final QueryProcessingService queryProcessingService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		log.info("Entering {} node", this.getClass().getSimpleName());

		String input = StateUtil.getStringValue(state, INPUT_KEY);
		String agentId = StateUtil.getStringValue(state, AGENT_ID); // Get agent ID
		log.info("[{}] Processing user input: {} for agentId: {}", this.getClass().getSimpleName(), input, agentId);

		// Use streaming utility class for content collection and result mapping
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "开始进行问题重写...", "\n\n问题重写完成！",
				finalResult -> Map.of(QUERY_REWRITE_NODE_OUTPUT, finalResult, RESULT, finalResult),
				queryProcessingService.rewriteStream(input, agentId));

		return Map.of(QUERY_REWRITE_NODE_OUTPUT, generator);
	}

}
