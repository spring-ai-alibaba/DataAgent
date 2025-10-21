/*
 * Copyright 2024-2025 the original author or authors.
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
package com.alibaba.cloud.ai.service.graph;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.vo.Nl2SqlProcessVO;

import java.util.function.Consumer;

public interface GraphService {

	/**
	 * 自然语言转SQL，仅返回SQL代码结果
	 * @param naturalQuery 自然语言
	 * @param agentId Agent Id
	 * @return SQL结果
	 * @throws GraphRunnerException 图运行异常
	 */
	String nl2sql(String naturalQuery, String agentId) throws GraphRunnerException;

	// todo: 遗留方法，待删除
	default String nl2sql(String naturalQuery) throws GraphRunnerException {
		return this.nl2sql(naturalQuery, "");
	}

	/**
	 * 自然语言转SQL，允许记录中间执行过程
	 * @param nl2SqlProcessConsumer 处理节点运行结果的Consumer
	 * @param naturalQuery 自然语言
	 * @param agentId Agent Id
	 * @param runnableConfig Runnable Config
	 */
	void nl2sqlWithProcess(Consumer<Nl2SqlProcessVO> nl2SqlProcessConsumer, String naturalQuery, String agentId,
			RunnableConfig runnableConfig);

	/**
	 * 自然语言转SQL，允许记录中间执行过程
	 * @param nl2SqlProcessConsumer 处理节点运行结果的Consumer
	 * @param naturalQuery 自然语言
	 * @param agentId Agent Id
	 */
	default void nl2sqlWithProcess(Consumer<Nl2SqlProcessVO> nl2SqlProcessConsumer, String naturalQuery,
			String agentId) {
		this.nl2sqlWithProcess(nl2SqlProcessConsumer, naturalQuery, agentId, RunnableConfig.builder().build());
	}

}
