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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.QUERY_ENHANCE_NODE_OUTPUT;

/**
 * State management utility class, providing type-safe state getting methods
 *
 * @author zhangshenghang
 */
public class StateUtil {

	private static final ObjectMapper OBJECT_MAPPER = JsonUtil.getObjectMapper();

	/**
	 * Safely get string type state value
	 */
	public static String getStringValue(OverAllState state, String key) {
		return state.value(key)
			.map(String.class::cast)
			.orElseThrow(() -> new IllegalStateException("State key not found: " + key));
	}

	/**
	 * Safely get string type state value with default value
	 */
	public static String getStringValue(OverAllState state, String key, String defaultValue) {
		return state.value(key).map(String.class::cast).orElse(defaultValue);
	}

	/**
	 * Safely get list type state value
	 */
	@SuppressWarnings("unchecked")
	public static <T> List<T> getListValue(OverAllState state, String key) {
		return state.value(key)
			.map(v -> (List<T>) v)
			.orElseThrow(() -> new IllegalStateException("State key not found: " + key));
	}

	/**
	 * Safely get object type state value
	 */
	public static <T> T getObjectValue(OverAllState state, String key, Class<T> type) {
		return state.value(key)
			.map(value -> deserializeIfNeeded(value, type))
			.orElseThrow(() -> new IllegalStateException("State key not found: " + key));
	}

	/**
	 * Safely get object type state value with default value
	 */
	public static <T> T getObjectValue(OverAllState state, String key, Class<T> type, T defaultValue) {
		return state.value(key).map(value -> deserializeIfNeeded(value, type)).orElse(defaultValue);
	}

	/**
	 * Handle deserialization of HashMap to target type when needed
	 */
	private static <T> T deserializeIfNeeded(Object value, Class<T> type) {
		// If already the correct type, return as-is
		if (type.isInstance(value)) {
			return type.cast(value);
		}

		// If it's a HashMap but we need a complex object, use JSON conversion
		if (value instanceof HashMap && !type.equals(HashMap.class)) {
			return OBJECT_MAPPER.convertValue(value, type);
		}

		return type.cast(value);
	}

	/**
	 * Safely get object type state value with default value supplier
	 */
	public static <T> T getObjectValue(OverAllState state, String key, Class<T> type, Supplier<T> defaultSupplier) {
		return state.value(key).map(type::cast).orElseGet(defaultSupplier);
	}

	/**
	 * Check if state value exists
	 */
	public static boolean hasValue(OverAllState state, String key) {
		Optional<Object> value = state.value(key);
		if (value.isPresent()) {
			if (value.get() instanceof String content) {
				return StringUtils.isNotEmpty(content);
			}
			return true;
		}
		return false;
	}

	/**
	 * Get Document list
	 */
	public static List<Document> getDocumentList(OverAllState state, String key) {
		return getListValue(state, key);
	}

	/**
	 * Get canonical query
	 */
	public static String getCanonicalQuery(OverAllState state) {
		QueryEnhanceOutputDTO queryEnhanceOutputDTO = getObjectValue(state, QUERY_ENHANCE_NODE_OUTPUT,
				QueryEnhanceOutputDTO.class);
		// 获取canonical_query
		return queryEnhanceOutputDTO.getCanonicalQuery();
	}

}
