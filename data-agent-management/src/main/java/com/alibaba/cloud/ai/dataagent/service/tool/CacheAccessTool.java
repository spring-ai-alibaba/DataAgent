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
    @Tool(name="getFullData", description = "根据缓存键获取完整数据")
    public String getFullData(String cacheKey) {
        if (resultCacheService.exists(cacheKey)) {
            return resultCacheService.getResult(cacheKey);
        } else {
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
