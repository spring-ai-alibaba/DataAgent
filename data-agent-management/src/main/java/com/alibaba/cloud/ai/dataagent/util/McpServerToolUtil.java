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

import com.alibaba.cloud.ai.dataagent.annotation.McpServerTool;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class McpServerToolUtil {

	public static <T> List<T> excludeMcpServerTool(GenericApplicationContext context, Class<T> type) {
		String[] namesForType = context.getBeanNamesForType(type);
		Set<String> namesForAnnotation = Set.of(context.getBeanNamesForAnnotation(McpServerTool.class));
		return Arrays.stream(namesForType)
			.filter(name -> !namesForAnnotation.contains(name))
			.map(name -> context.getBean(name, type))
			.toList();
	}

}
