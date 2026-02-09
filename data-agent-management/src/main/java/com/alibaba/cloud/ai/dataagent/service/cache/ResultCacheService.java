/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.cache;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 新增 ResultCacheService 类
@Service
public class ResultCacheService {

	private final Map<String, String> cache = new ConcurrentHashMap<>();

	private static final int MAX_CACHE_SIZE = 1000; // 最大缓存数量

	private static final long CACHE_EXPIRY_TIME = 30 * 60 * 1000; // 30分钟过期

	/**
	 * 存储执行结果并返回缓存键
	 */
	public String storeResult(String key, String result) {
		String cacheKey = generateCacheKey(key);

		// 检查缓存大小，必要时清理
		if (cache.size() >= MAX_CACHE_SIZE) {
			cleanupExpiredEntries();
		}

		cache.put(cacheKey, result);
		return cacheKey;
	}

	/**
	 * 根据缓存键获取完整结果
	 */
	public String getResult(String cacheKey) {
		return cache.get(cacheKey);
	}

	public boolean exists(String cacheKey) {
		return cache.containsKey(cacheKey);
	}

	private String generateCacheKey(String key) {
		return "result_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
	}

	private void cleanupExpiredEntries() {
		// 清理过期条目，简化实现
		cache.entrySet().removeIf(entry -> shouldExpire(entry.getKey()));
	}

	private boolean shouldExpire(String key) {
		// 简化过期判断逻辑
		return cache.size() > MAX_CACHE_SIZE * 0.8;
	}

}
