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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonUtil {

	@Getter
	private static final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * 将对象转换为JSON字符串
	 * @param obj 对象
	 * @return JSON字符串
	 */
	public static String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		}
		catch (Exception e) {
			log.error(e.getMessage(), e);
			return null;
		}
	}

	/**
	 * 将JSON字符串转换为指定类型的对象
	 * @param json JSON字符串
	 * @param clazz 目标对象类型
	 * @return 目标对象
	 */
	public static <T> T parse(String json, Class<T> clazz) {
		try {
			return objectMapper.readValue(json, clazz);
		}
		catch (Exception e) {
			log.error(e.getMessage(), e);
			return null;
		}
	}

}
