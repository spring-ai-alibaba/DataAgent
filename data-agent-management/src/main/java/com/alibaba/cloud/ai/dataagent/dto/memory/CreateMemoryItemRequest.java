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
package com.alibaba.cloud.ai.dataagent.dto.memory;

import com.alibaba.cloud.ai.dataagent.enums.MemoryKind;
import com.alibaba.cloud.ai.dataagent.enums.MemoryScopeType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateMemoryItemRequest {

	@NotNull
	private MemoryScopeType scopeType;

	private Integer datasourceId;

	@NotNull
	private MemoryKind memoryKind;

	@NotBlank
	@Size(max = 255)
	private String memoryKey;

	@NotNull
	private JsonNode value;

	@Size(max = 36)
	private String sourceTurnId;

	@DecimalMin("0.0")
	@DecimalMax("1.0")
	private BigDecimal confidence;

	@Size(max = 128)
	private String schemaFingerprint;

	private LocalDateTime validUntil;

	private Long supersedesId;

}
