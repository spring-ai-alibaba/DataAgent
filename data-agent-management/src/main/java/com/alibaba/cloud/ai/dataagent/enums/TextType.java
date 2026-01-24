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

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TextType {

	JSON("$$$json", "$$$"),

	PYTHON("$$$python", "$$$"),

	// LLM模型爱输出```sql，那就换一个标记
	SQL("$$$sql", "$$$"),

	MARK_DOWN("$$$markdown-report", "$$$/markdown-report"),

	RESULT_SET("$$$result_set", "$$$"),

	TEXT(null, null);

	private final String startSign;

	private final String endSign;

	public static TextType getType(TextType origin, String chuck) {
		if (origin == TEXT) {
			for (TextType type : TextType.values()) {
				if (chuck.equals(type.startSign)) {
					return type;
				}
			}
		}
		else {
			if (chuck.equals(origin.endSign)) {
				return TextType.TEXT;
			}
		}
		return origin;
	}

	public static TextType getTypeByStratSign(String startSign) {
		for (TextType type : TextType.values()) {
			if (startSign.equals(type.startSign)) {
				return type;
			}
		}
		return TextType.TEXT;
	}

}
