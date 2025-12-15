/*
 * Copyright 2025 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.dto.prompt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 对应 模板query-enhancement.txt的输出
@Data
@NoArgsConstructor
public class QueryEnhanceOutputDTO {

	// 经LLM重写后的 规范化查询
	@JsonProperty("canonical_query")
	private String canonicalQuery;

	// 基于canonicalQuery的扩展查询
	@JsonProperty("expanded_queries")
	private List<String> expandedQueries;

}
