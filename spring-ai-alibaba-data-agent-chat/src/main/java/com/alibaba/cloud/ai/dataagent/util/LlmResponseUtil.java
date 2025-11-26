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
package com.alibaba.cloud.ai.dataagent.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class LlmResponseUtil {

	public static String extractJson(String rawResponse) {
		if (rawResponse == null || rawResponse.trim().isEmpty()) {
			return "";
		}

		String cleaned = MarkdownParserUtil.extractText(rawResponse);
		cleaned = cleaned.replaceAll("(?s)<[^>]+>.*?</[^>]+>", "");
		cleaned = cleaned.replaceAll("</?[^>]+>", "");

		Pattern jsonPattern = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}");
		Matcher matcher = jsonPattern.matcher(cleaned);
		if (matcher.find()) {
			return matcher.group().trim();
		}

		int jsonStart = cleaned.indexOf('{');
		int jsonEnd = cleaned.lastIndexOf('}');
		if (jsonStart >= 0 && jsonEnd > jsonStart) {
			return cleaned.substring(jsonStart, jsonEnd + 1).trim();
		}

		return cleaned.trim();
	}

}
