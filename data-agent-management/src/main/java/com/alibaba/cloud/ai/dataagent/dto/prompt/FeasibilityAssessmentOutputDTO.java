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

	public static final String DATA_ANALYSIS = "DATA_ANALYSIS";

	public static final String NEED_CLARIFICATION = "NEED_CLARIFICATION";

	public static final String CHIT_CHAT = "CHIT_CHAT";

	@JsonProperty("request_type")
	@JsonPropertyDescription("需求类型，可选值：DATA_ANALYSIS、NEED_CLARIFICATION、CHIT_CHAT")
	private String requestType;

	@JsonProperty("language")
	@JsonPropertyDescription("用户问题的语言，例如中文、英文")
	private String language;

	@JsonProperty("content")
	@JsonPropertyDescription("如果可直接分析，则填写规范化后的需求内容；如果需要澄清，则填写单个聚焦的澄清问题；如果无法处理，则填写礼貌回复")
	private String content;

}
