package com.alibaba.cloud.ai.dataagent.dispatcher;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
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
