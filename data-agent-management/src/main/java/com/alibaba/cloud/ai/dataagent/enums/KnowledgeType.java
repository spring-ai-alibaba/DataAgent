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

/**
 * 知识类型枚举
 */
@Getter
public enum KnowledgeType {

	/**
	 * 文档类型
	 */
	DOCUMENT("DOCUMENT", "文档类型"),

	/**
	 * 问答类型
	 */
	QA("QA", "问答类型"),

	/**
	 * 常见问题类型
	 */
	FAQ("FAQ", "常见问题类型");

	private final String code;

	private final String description;

	KnowledgeType(String code, String description) {
		this.code = code;
		this.description = description;
	}

	/**
	 * 根据代码获取枚举
	 */
	public static KnowledgeType fromCode(String code) {
		for (KnowledgeType type : values()) {
			// 严格比对
			if (type.getCode().equals(code)) {
				return type;
			}
		}
		throw new IllegalArgumentException("未知的知识类型代码: " + code);
	}

}
