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
package com.alibaba.cloud.ai.dataagent.service.graph;

import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

/**
 * @author vlsmb
 * @since 2025/10/30
 */
public interface GraphService {

	/**
	 * 自然语言转SQL，仅返回SQL代码结果
	 * @param naturalQuery 自然语言
	 * @param agentId Agent Id
	 * @return SQL结果
	 * @throws GraphRunnerException 图运行异常
	 */
	String nl2sql(String naturalQuery, String agentId) throws GraphRunnerException;

	/**
	 * 流式处理NL2SQL或者DataAgent请求
	 * @param sink 输出Sink
	 * @param graphRequest 请求体
	 */
	void graphStreamProcess(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest graphRequest);

	/**
	 * 停止指定 threadId 的流式处理
	 * @param threadId 线程ID
	 */
	void stopStreamProcessing(String threadId);

}
