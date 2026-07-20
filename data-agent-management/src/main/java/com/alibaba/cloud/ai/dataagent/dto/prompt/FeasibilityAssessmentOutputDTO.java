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
package com.alibaba.cloud.ai.dataagent.dto.prompt;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FeasibilityAssessmentOutputDTO {

	@JsonProperty("requirementType")
	@JsonPropertyDescription("Requirement type: DATA_ANALYSIS, NEED_CLARIFICATION, or FREE_CHAT")
	private RequirementType requirementType;

	@JsonProperty("language")
	@JsonPropertyDescription("Language used for the response, for example zh-CN or en-US")
	private String language;

	@JsonProperty("content")
	@JsonPropertyDescription("Normalized analysis requirement, clarification question, or chat response")
	private String content;

	public enum RequirementType {

		DATA_ANALYSIS,

		NEED_CLARIFICATION,

		FREE_CHAT

	}

}
