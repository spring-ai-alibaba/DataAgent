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

package com.alibaba.cloud.ai.dataagent.dto.prompt;

/**
 * Prompt configuration request DTO
 *
 * @author Makoto
 */

public record PromptConfigDTO(String id, // Configuration ID (required for update)
		String name, // Configuration name
		String promptType, // Prompt type
		Long agentId, // Associated agent ID, null means global
		String optimizationPrompt, // User-defined system prompt content
		Boolean enabled, // Whether to enable this configuration
		String description, // Configuration description
		String creator, // Creator
		Integer priority, // Configuration priority
		Integer displayOrder // Configuration display order
) {

}
