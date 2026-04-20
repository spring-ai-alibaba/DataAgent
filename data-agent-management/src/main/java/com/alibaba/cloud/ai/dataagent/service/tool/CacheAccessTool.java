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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class CacheAccessTool {

	private final ResultCacheService resultCacheService;

	public CacheAccessTool(ResultCacheService resultCacheService) {
		this.resultCacheService = resultCacheService;
	}

	/**
	 * 工具方法：根据摘要码获取完整数据
	 */
	@Tool(name = "getFullData", description = "根据摘要码获取完整数据")
	public String getFullData(@ToolParam(description = "摘要码") String cacheKey) {
		if (StringUtils.isNotBlank(cacheKey) && resultCacheService.exists(cacheKey)) {
			String fullData = resultCacheService.getResult(cacheKey);
			log.info("获取完整数据成功: {}", fullData);
			return fullData;
		}
		else {
			return "摘要码不存在或已过期";
		}
	}

	/**
	 * 工具方法：批量获取多个摘要码的数据
	 */
	public Map<String, String> getMultipleData(List<String> cacheKeys) {
		Map<String, String> results = new HashMap<>();
		for (String key : cacheKeys) {
			results.put(key, getFullData(key));
		}
		return results;
	}

}
