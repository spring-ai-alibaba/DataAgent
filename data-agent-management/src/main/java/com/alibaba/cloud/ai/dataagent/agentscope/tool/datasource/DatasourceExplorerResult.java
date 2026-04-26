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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DatasourceExplorerResult {

	private String datasource;

	private String action;

	private String summary;

	@Builder.Default
	private List<Map<String, Object>> tables = new ArrayList<>();

	@Builder.Default
	private List<Map<String, Object>> columns = new ArrayList<>();

	@Builder.Default
	private List<Map<String, Object>> rows = new ArrayList<>();

	@Builder.Default
	private List<Map<String, Object>> relations = new ArrayList<>();

	private String sql;

	private String sqlExplanation;

	@Builder.Default
	private List<String> usedTables = new ArrayList<>();

	@Builder.Default
	private List<String> usedColumns = new ArrayList<>();

	@Builder.Default
	private Map<String, Object> permissions = new java.util.LinkedHashMap<>();

	@Builder.Default
	private Map<String, Object> stats = new java.util.LinkedHashMap<>();

	@Builder.Default
	private List<String> nextSuggestedActions = new ArrayList<>();

	private boolean truncated;

}
