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

package com.alibaba.cloud.ai.dataagent.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.dataagent.common.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.util.MarkdownParserUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * 根据意图识别结果决定下一个节点的分发器
 */
@Slf4j
public class IntentRecognitionDispatcher implements EdgeAction {

	@Override
	public String apply(OverAllState state) throws Exception {
		// 获取意图识别结果
		String intentResult = StateUtil.getStringValue(state, INTENT_RECOGNITION_NODE_OUTPUT);

		if (intentResult == null || intentResult.trim().isEmpty()) {
			log.warn("Intent recognition result is null or empty, defaulting to END");
			return END;
		}

		try {
			// 解析JSON格式的意图识别结果
			JsonNode jsonNode = JsonUtil.getObjectMapper()
				.readTree(MarkdownParserUtil.extractText(intentResult.trim()));
			String classification = jsonNode.path("classification").asText();

			// 根据分类结果决定下一个节点
			if ("《闲聊或无关指令》".equals(classification)) {
				log.warn("Intent classified as chat or irrelevant, ending conversation");
				return END;
			}
			else {
				log.info("Intent classified as potential data analysis request, proceeding to evidence recall");
				return EVIDENCE_RECALL_NODE;
			}
		}
		catch (JsonProcessingException e) {
			log.error("Failed to parse intent recognition result as JSON: {}", intentResult, e);
			return END;
		}
	}

}
