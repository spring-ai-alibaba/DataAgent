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
package com.alibaba.cloud.ai.dataagent.service.semantic.runtime;

import java.util.List;
import org.apache.commons.lang3.StringUtils;

public record ResolvedSemanticRelation(String sourceTableName, List<String> sourceColumnNames, String targetTableName,
		List<String> targetColumnNames, String relationType, String description, String sourceType, boolean virtual,
		boolean declaredInDatabase) {

	public String sourceColumnSummary() {
		return String.join(",", sourceColumnNames);
	}

	public String targetColumnSummary() {
		return String.join(",", targetColumnNames);
	}

	public String foreignKeyText() {
		return sourceTableName + "." + sourceColumnSummary() + "=" + targetTableName + "." + targetColumnSummary();
	}

	public String compatibilityDescription() {
		if (StringUtils.isBlank(description)) {
			return foreignKeyText();
		}
		return foreignKeyText() + " | " + description;
	}

}
