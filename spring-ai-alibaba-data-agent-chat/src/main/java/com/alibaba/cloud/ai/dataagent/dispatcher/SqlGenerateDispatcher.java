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
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * @author zhangshenghang
 */
@Slf4j
public class SqlGenerateDispatcher implements EdgeAction {

	@Override
	public String apply(OverAllState state) {
		String sqlGenerateOutput = (String) state.value(SQL_GENERATE_OUTPUT).orElseThrow();
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
				log.info("SQL生成成功，进入SQL执行节点: {}", SQL_EXECUTE_NODE);
				yield SQL_EXECUTE_NODE;
			}
		};
	}

}
