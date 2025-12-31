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

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * Human feedback node for plan review and modification.
 *
 * @author Makoto
 */
@Slf4j
@Component
public class HumanFeedbackNode implements NodeAction {

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		Map<String, Object> updated = new HashMap<>();

		// 检查最大修复次数
		int repairCount = StateUtil.getObjectValue(state, PLAN_REPAIR_COUNT, Integer.class, 0);
		if (repairCount >= 3) {
			log.warn("Max repair attempts (3) exceeded, ending process");
			updated.put("human_next_node", "END");
			return updated;
		}

		// 等待用户反馈
		OverAllState.HumanFeedback humanFeedback = state.humanFeedback();
		if (humanFeedback == null) {
			updated.put("human_next_node", "WAIT_FOR_FEEDBACK");
			return updated;
		}

		// 处理反馈结果
		Map<String, Object> feedbackData = humanFeedback.data();
		boolean approved = (boolean) feedbackData.getOrDefault("feedback", true);

		if (approved) {
			log.info("Plan approved → execution");
			updated.put("human_next_node", PLAN_EXECUTOR_NODE);
			updated.put(HUMAN_REVIEW_ENABLED, false);
		}
		else {
			log.info("Plan rejected → regeneration (attempt {})", repairCount + 1);
			updated.put("human_next_node", PLANNER_NODE);
			updated.put(PLAN_REPAIR_COUNT, repairCount + 1);
			updated.put(PLAN_CURRENT_STEP, 1);
			updated.put(HUMAN_REVIEW_ENABLED, true);

			// 保存用户反馈内容
			String feedbackContent = feedbackData.getOrDefault("feedback_content", "").toString();
			updated.put(PLAN_VALIDATION_ERROR,
					StringUtils.hasLength(feedbackContent) ? feedbackContent : "Plan rejected by user");
			// 这边清空旧的计划输出
			updated.put(PLANNER_NODE_OUTPUT, "");
			state.withoutResume();
		}

		return updated;
	}

}
