/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.dataagent.service.knowledge;

import com.alibaba.cloud.ai.dataagent.enums.SplitterType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.stereotype.Component;

import java.util.Map;

// TODO 后续需改造 AgentKnowledgeResourceManager 使用该类获取对应的 TextSplitter，然后前端提供页面让用户选择不同的切割方式
@Slf4j
@Component
@RequiredArgsConstructor
public class TextSplitterFactory {

	private final Map<String, TextSplitter> splitterMap;

	/**
	 * 根据类型字符串获取对应的 Splitter
	 * @param type 前端传入的类型，例如 "token", "recursive"
	 * @return 对应的 TextSplitter 实例
	 */
	public TextSplitter getSplitter(String type) {
		// 1. 尝试直接获取
		TextSplitter splitter = splitterMap.get(type);

		// 2. 如果没找到，尝返回默认
		if (splitter == null) {
			log.warn("Splitter type '{}' not found, falling back to default 'token'", type);
			return splitterMap.get(SplitterType.TOKEN.getValue());
		}

		return splitter;
	}

}
