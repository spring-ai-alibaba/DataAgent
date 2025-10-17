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

package com.alibaba.cloud.ai.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.alibaba.cloud.ai.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * @author zhangshenghang
 */
public class QueryRewriteDispatcher implements EdgeAction {

	private static final Logger logger = LoggerFactory.getLogger(QueryRewriteDispatcher.class);

	@Override
	public String apply(OverAllState state) {
		// value的值是和 resources/init-rewrite.txt的输出一致，例如
		// 需求类型：《自由闲聊》.........
		// 语种类型：《中文》
		// 需求内容：2025-10-17天气如何
		String value = state.value(QUERY_REWRITE_NODE_OUTPUT, END);
		logger.info("[QueryRewriteDispatcher]apply方法被调用，QUERY_REWRITE_NODE_OUTPUT的value如下\n {}", value);

		if (value != null && value.contains("需求类型：《数据分析》")) {
			logger.info("[QueryRewriteDispatcher]需求类型为数据分析，进入KEYWORD_EXTRACT_NODE节点");
			return KEYWORD_EXTRACT_NODE;
		}
		else {
			logger.info("[QueryRewriteDispatcher]需求类型非数据分析，返回END节点");
			return END;
		}

	}

}
