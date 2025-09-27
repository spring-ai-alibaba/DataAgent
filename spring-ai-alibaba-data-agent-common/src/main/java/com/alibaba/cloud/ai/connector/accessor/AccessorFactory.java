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

package com.alibaba.cloud.ai.connector.accessor;

import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.enums.DatabaseDialectEnum;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author vlsmb
 * @since 2025/9/27
 */
@Component
public class AccessorFactory {

	private static final Map<String, Accessor> accessorMap = new ConcurrentHashMap<>();

	public static void register(Accessor accessor) {
		accessorMap.put(accessor.getDbAccessorType(), accessor);
	}

	public static boolean isRegistered(String type) {
		return accessorMap.containsKey(type);
	}

	public Accessor getAccessor(DbConfig dbConfig) {
		DatabaseDialectEnum dialectEnum = DatabaseDialectEnum.getByCode(dbConfig.getDialectType())
			.orElseThrow(() -> new IllegalStateException("unknown db dialect type: " + dbConfig.getDialectType()));
		return getAccessor(dialectEnum);
	}

	public Accessor getAccessor(DatabaseDialectEnum dialectEnum) {
		return accessorMap.values()
			.stream()
			.filter(a -> a.supportedDialect(dialectEnum))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no accessor registered for dialect: " + dialectEnum));
	}

	public Accessor getAccessor(String type) {
		return accessorMap.get(type);
	}

}
