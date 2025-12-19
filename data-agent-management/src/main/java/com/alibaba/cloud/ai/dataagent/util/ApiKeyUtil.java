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

import java.security.SecureRandom;

/**
 * Utility for generating and masking API keys.
 */
public final class ApiKeyUtil {

	private static final String API_KEY_PREFIX = "sk-";

	private static final String API_KEY_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	private static final int API_KEY_LENGTH = 32;

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private ApiKeyUtil() {
	}

	public static String generate() {
		StringBuilder builder = new StringBuilder(API_KEY_PREFIX);
		for (int i = 0; i < API_KEY_LENGTH; i++) {
			int idx = SECURE_RANDOM.nextInt(API_KEY_CHARS.length());
			builder.append(API_KEY_CHARS.charAt(idx));
		}
		return builder.toString();
	}

	public static String mask(String apiKey) {
		if (apiKey == null || apiKey.length() <= 8) {
			return "****";
		}
		String suffix = apiKey.substring(apiKey.length() - 4);
		return "****" + suffix;
	}

}
