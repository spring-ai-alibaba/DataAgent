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
