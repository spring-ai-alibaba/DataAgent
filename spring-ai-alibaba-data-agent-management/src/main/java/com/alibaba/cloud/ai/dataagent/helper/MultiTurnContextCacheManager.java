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

package com.alibaba.cloud.ai.dataagent.helper;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话上下文缓存，避免每次构建多轮对话都重新处理全部消息。
 */
@Component
public class MultiTurnContextCacheManager {

	private final Map<String, MultiTurnContextSnapshot> cache = new ConcurrentHashMap<>();

	public MultiTurnContextSnapshot getSnapshot(String sessionId) {
		if (!StringUtils.hasText(sessionId)) {
			return null;
		}
		return cache.get(sessionId);
	}

	public void updateSnapshot(String sessionId, MultiTurnContextSnapshot snapshot) {
		if (!StringUtils.hasText(sessionId) || snapshot == null) {
			return;
		}
		cache.put(sessionId, snapshot);
	}

	public void removeSnapshot(String sessionId) {
		if (StringUtils.hasText(sessionId)) {
			cache.remove(sessionId);
		}
	}

}
