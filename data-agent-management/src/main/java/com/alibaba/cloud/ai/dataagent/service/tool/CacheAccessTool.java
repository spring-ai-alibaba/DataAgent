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
package com.alibaba.cloud.ai.dataagent.service.tool;

import com.alibaba.cloud.ai.dataagent.service.cache.ResultCacheService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CacheAccessTool {

	private final ResultCacheService resultCacheService;

	public CacheAccessTool(ResultCacheService resultCacheService) {
		this.resultCacheService = resultCacheService;
	}

	/**
	 * 工具方法：根据缓存键获取完整数据
	 */
	@Tool(name = "getFullData", description = "根据缓存键获取完整数据")
	public String getFullData(String cacheKey) {
		if (resultCacheService.exists(cacheKey)) {
			return resultCacheService.getResult(cacheKey);
		}
		else {
			return "缓存键不存在或已过期";
		}
	}

	/**
	 * 工具方法：批量获取多个缓存键的数据
	 */
	@Tool
	public Map<String, String> getMultipleData(List<String> cacheKeys) {
		Map<String, String> results = new HashMap<>();
		for (String key : cacheKeys) {
			results.put(key, getFullData(key));
		}
		return results;
	}

}
