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
package com.alibaba.cloud.ai.dataagent.service.graph.Context;

import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import lombok.Data;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

/**
 * 流式处理上下文，封装每个 threadId 的所有相关状态
 *
 * @author Makoto
 * @since 2025/11/28
 */
@Data
public class StreamContext {

	private Disposable disposable;

	private Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink;

	private TextType textType;

	/**
	 * 清理所有资源
	 */
	public void cleanup() {
		if (disposable != null && !disposable.isDisposed()) {
			disposable.dispose();
		}
		if (sink != null) {
			sink.tryEmitComplete();
		}
	}

}
