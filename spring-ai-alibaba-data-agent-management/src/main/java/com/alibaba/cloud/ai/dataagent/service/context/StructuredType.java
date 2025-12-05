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

package com.alibaba.cloud.ai.dataagent.service.context;

import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Enum for Structured Types
 *
 * @author Makoto
 */
public enum StructuredType {

	SQL("sql"), PYTHON("python"), SUMMARY("summary");

	private final String typeName;

	StructuredType(String typeName) {
		this.typeName = typeName;
	}

	static StructuredType from(String messageType) {
		if (!StringUtils.hasText(messageType)) {
			return null;
		}
		String normalized = messageType.toLowerCase(Locale.ROOT);
		for (StructuredType value : EnumSet.allOf(StructuredType.class)) {
			if (value.typeName.equals(normalized)) {
				return value;
			}
		}
		return null;
	}

}
