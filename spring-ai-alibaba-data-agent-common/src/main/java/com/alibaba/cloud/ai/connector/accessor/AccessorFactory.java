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
import com.alibaba.cloud.ai.enums.BizDataSourceTypeEnum;
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

	public Accessor getAccessorByDbConfig(DbConfig dbConfig) {
		if (dbConfig == null) {
			return getAccessorByDbTypeEnum(BizDataSourceTypeEnum.MYSQL);
		}
		// FIXME: 目前默认使用mysql，因为用户配置中暂时没有dbConfig的配置
		BizDataSourceTypeEnum typeEnum = Arrays.stream(BizDataSourceTypeEnum.values())
			.filter(e -> e.getDialect().equals(dbConfig.getDialectType()))
			.filter(e -> e.getProtocol().equals(dbConfig.getConnectionType()))
			.findFirst()
			.orElse(BizDataSourceTypeEnum.MYSQL);
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
