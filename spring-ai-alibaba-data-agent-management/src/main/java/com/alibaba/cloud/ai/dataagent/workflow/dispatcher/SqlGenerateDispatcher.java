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

import com.alibaba.cloud.ai.dataagent.config.DataAgentProperties;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.alibaba.cloud.ai.dataagent.common.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * @author zhangshenghang
 */
@Slf4j
@Component
@AllArgsConstructor
public class SqlGenerateDispatcher implements EdgeAction {

	private final DataAgentProperties properties;

	@Override
	public String apply(OverAllState state) {
		Optional<Object> optional = state.value(SQL_GENERATE_OUTPUT);
		if (optional.isEmpty()) {
			int currentCount = state.value(SQL_GENERATE_COUNT, properties.getMaxSqlRetryCount());
			// 生成失败，重新生成
			if (currentCount < properties.getMaxSqlRetryCount()) {
				log.info("SQL 生成失败，开始重试，当前次数: {}", currentCount);
				return SQL_GENERATE_NODE;
			}
			log.error("SQL 生成失败，达到最大重试次数，结束流程");
			return END;
		}
		String sqlGenerateOutput = (String) optional.get();
		log.info("SQL 生成结果: {}", sqlGenerateOutput);
		return switch (sqlGenerateOutput) {
			case END -> {
				log.info("检测到流程结束标志: {}", END);
				yield END;
			}
			// TODO 需要优化，不能简单跳转
			case SQL_GENERATE_SCHEMA_MISSING -> {
				log.warn("SQL生成缺少Schema，跳转到{}节点", FEASIBILITY_ASSESSMENT_NODE);
				yield FEASIBILITY_ASSESSMENT_NODE;
			}
			default -> {
				log.info("SQL生成成功，进入SQL检查节点: {}", SQL_OPTIMIZE_NODE);
				yield SQL_OPTIMIZE_NODE;
			}
		};
	}

}
