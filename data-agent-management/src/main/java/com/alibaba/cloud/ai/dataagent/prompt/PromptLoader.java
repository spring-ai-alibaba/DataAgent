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
package com.alibaba.cloud.ai.dataagent.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StreamUtils;

/**
 * Prompt loader, used to load prompt templates from file system
 *
 * @author zhangshenghang
 */
@Slf4j
public class PromptLoader {

	private static final String PROMPT_PATH_PREFIX = "prompts/";

	private static final ConcurrentHashMap<String, String> promptCache = new ConcurrentHashMap<>();

	/**
	 * Load prompt template from file
	 * @param promptName prompt file name (without path and extension)
	 * @return prompt content
	 */
	public static String loadPrompt(String promptName) {
		return loadPrompt(promptName, "md");
	}

	/**
	 * Load prompt template from file with explicit extension
	 * @param promptName prompt file name (without path and extension)
	 * @param extension prompt file extension without leading dot
	 * @return prompt content
	 */
	public static String loadPrompt(String promptName, String extension) {
		String normalizedExtension = normalizeExtension(extension);
		String cacheKey = promptName + "#" + normalizedExtension;
		return promptCache.computeIfAbsent(cacheKey, key -> {
			String fileName = PROMPT_PATH_PREFIX + promptName + "." + normalizedExtension;
			// 使用本类的类加载器获取资源（避免jar包中无法获取资源）
			try (InputStream inputStream = PromptLoader.class.getClassLoader().getResourceAsStream(fileName)) {
				if (inputStream == null) {
					throw new IllegalStateException("Prompt resource not found: " + fileName);
				}
				return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
			}
			catch (IOException | IllegalStateException e) {
				log.error("加载提示词失败！{}", e.getMessage(), e);
				throw new RuntimeException("加载提示词失败: " + promptName, e);
			}
		});
	}

	private static String normalizeExtension(String extension) {
		if (extension == null || extension.isBlank()) {
			return "md";
		}
		String normalized = extension.trim();
		return normalized.startsWith(".") ? normalized.substring(1) : normalized;
	}

	/**
	 * Clear prompt cache
	 */
	public static void clearCache() {
		promptCache.clear();
	}

	/**
	 * Get cache size
	 * @return number of prompts in cache
	 */
	public static int getCacheSize() {
		return promptCache.size();
	}

}
