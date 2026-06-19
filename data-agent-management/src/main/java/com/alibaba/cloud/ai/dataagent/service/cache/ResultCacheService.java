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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

// 新增 ResultCacheService 类
@Service
public class ResultCacheService {

	// 设置最大缓存大小和过期策略（例如，写入后10分钟过期）
	Cache<String, String> cache = CacheBuilder.newBuilder()
		.maximumSize(100) // 设置最大缓存大小（仅为示例）
		.expireAfterWrite(30, TimeUnit.MINUTES) // 设置过期时间
		.build();

	/**
	 * 存储执行结果并返回摘要码
	 */
	public String storeResult(String key, String result) {
		String cacheKey = generateCacheKey(key);
		cache.put(cacheKey, result);
		return cacheKey;
	}

	/**
	 * 根据摘要码获取完整结果
	 */
	public String getResult(String cacheKey) {
		return cache.getIfPresent(cacheKey);
	}

	public boolean exists(String cacheKey) {
		return cache.getIfPresent(cacheKey) != null;
	}

	private String generateCacheKey(String key) {
		return "result_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 10);
	}

}
