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
package com.alibaba.cloud.ai.dataagent.enums;

import lombok.Getter;

@Getter
public enum ReasoningEffort {

	HIGH("high"), MAX("max");

	private final String code;

	ReasoningEffort(String code) {
		this.code = code;
	}

	public static ReasoningEffort fromCode(String code) {
		if ("low".equalsIgnoreCase(code) || "medium".equalsIgnoreCase(code)) {
			return HIGH;
		}
		if ("xhigh".equalsIgnoreCase(code)) {
			return MAX;
		}
		for (ReasoningEffort effort : values()) {
			if (effort.code.equalsIgnoreCase(code) || effort.name().equalsIgnoreCase(code)) {
				return effort;
			}
		}
		throw new IllegalArgumentException("不支持的思考强度: " + code + "，仅支持 high、max");
	}
}
