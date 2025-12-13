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

import com.alibaba.cloud.ai.dataagent.common.constant.Constant;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;

public class SqlOptimizeDispatcher implements EdgeAction {

	@Override
	public String apply(OverAllState state) throws Exception {
		Boolean b = state.value(Constant.SQL_OPTIMIZE_FINISHED, false);
		if (b) {
			return Constant.SEMANTIC_CONSISTENCY_NODE;
		}
		else {
			return Constant.SQL_OPTIMIZE_NODE;
		}
	}

}
