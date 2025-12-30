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

package com.alibaba.cloud.ai.dataagent.service.datasource.handler.registry;

import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DatasourceTypeHandlerRegistry {

	private final Map<String, DatasourceTypeHandler> handlerMap = new ConcurrentHashMap<>();

	public DatasourceTypeHandlerRegistry(List<DatasourceTypeHandler> handlers) {
		handlers.forEach(this::register);
	}

	public void register(DatasourceTypeHandler handler) {
		handlerMap.put(normalizeType(handler.typeName()), handler);
	}

	public boolean isRegistered(String type) {
		return handlerMap.containsKey(normalizeType(type));
	}

	public DatasourceTypeHandler getRequired(String type) {
		if (!StringUtils.hasText(type)) {
			throw new IllegalArgumentException("Datasource type cannot be blank");
		}
		DatasourceTypeHandler handler = handlerMap.get(normalizeType(type));
		if (handler == null) {
			throw new IllegalStateException("Unsupported datasource type: " + type);
		}
		return handler;
	}

	private String normalizeType(String type) {
		if (!StringUtils.hasText(type)) {
			return "";
		}
		return type.trim().toLowerCase(Locale.ROOT);
	}

}
