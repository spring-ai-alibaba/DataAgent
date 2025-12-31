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

package com.alibaba.cloud.ai.dataagent.connector.accessor;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author vlsmb
 * @since 2025/9/27
 */
@Component
public class AccessorFactory {

	public AccessorFactory(List<Accessor> accessors) {
		accessors.forEach(this::register);
	}

	private final Map<String, Accessor> accessorMap = new ConcurrentHashMap<>();

	public void register(Accessor accessor) {
		accessorMap.put(accessor.getAccessorType(), accessor);
	}

	public boolean isRegistered(String type) {
		return accessorMap.containsKey(type);
	}

	public Accessor getAccessorByDbConfig(DbConfigBO dbConfig) {
		if (dbConfig == null) {
			throw new IllegalArgumentException("dbConfig cannot be null");
		}
		BizDataSourceTypeEnum typeEnum = Arrays.stream(BizDataSourceTypeEnum.values())
			.filter(e -> e.getDialect().equalsIgnoreCase(dbConfig.getDialectType()))
			.filter(e -> e.getProtocol().equalsIgnoreCase(dbConfig.getConnectionType()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
					"no accessor registered for dialect: " + dbConfig.getDialectType()));
		return getAccessorByDbTypeEnum(typeEnum);
	}

	// todo: 写一层缓存
	public Accessor getAccessorByDbTypeEnum(BizDataSourceTypeEnum typeEnum) {
		return accessorMap.values()
			.stream()
			.filter(a -> a.supportedDataSourceType(typeEnum.getTypeName()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no accessor registered for dialect: " + typeEnum));
	}

	public Accessor getAccessorByType(String type) {
		return accessorMap.get(type);
	}

}
