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
package com.alibaba.cloud.ai.dataagent.agentscope.tool.datasource;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;

public enum DatasourceExplorerAction {

	LIST_TABLES,

	FIND_TABLES,

	GET_TABLE_SCHEMA,

	GET_RELATED_TABLES,

	PREVIEW_ROWS,

	SEARCH;

	@JsonCreator
	public static DatasourceExplorerAction fromValue(String value) {
		if (value == null) {
			return null;
		}
		return Arrays.stream(values())
			.filter(action -> action.name().equalsIgnoreCase(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unsupported datasource explorer action: " + value));
	}

}
