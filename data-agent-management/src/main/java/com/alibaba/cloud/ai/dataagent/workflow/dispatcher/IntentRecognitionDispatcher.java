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

package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.dataagent.dto.prompt.IntentRecognitionOutputDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.dataagent.common.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.common.constant.Constant.EVIDENCE_RECALL_NODE;
import static com.alibaba.cloud.ai.dataagent.common.constant.Constant.INTENT_RECOGNITION_NODE_OUTPUT;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * 根据意图识别结果决定下一个节点的分发器
 */
@Slf4j
public class IntentRecognitionDispatcher implements EdgeAction {

	@Override
	public String apply(OverAllState state) throws Exception {
		// 获取意图识别结果
		IntentRecognitionOutputDTO intentResult = StateUtil.getObjectValue(state, INTENT_RECOGNITION_NODE_OUTPUT,
				IntentRecognitionOutputDTO.class);

		if (intentResult == null || intentResult.getClassification() == null
				|| intentResult.getClassification().trim().isEmpty()) {
			log.warn("Intent recognition result is null or empty, defaulting to END");
			return END;
		}

		String classification = intentResult.getClassification();

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

}
