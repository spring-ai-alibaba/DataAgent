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
package com.alibaba.cloud.ai.dataagent.entity;

import com.alibaba.cloud.ai.dataagent.enums.MemoryKind;
import com.alibaba.cloud.ai.dataagent.enums.MemoryScopeType;
import com.alibaba.cloud.ai.dataagent.enums.MemoryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Relational source of truth for stable cross-session memory.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryItem {

	private Long id;

	private MemoryScopeType scopeType;

	private Long ownerId;

	private Integer agentId;

	private Integer datasourceId;

	private MemoryKind memoryKind;

	private String memoryKey;

	private String valueJson;

	/**
	 * Stable hash of scope, kind and key. It is computed by the server and used to
	 * enforce one active confirmed value per logical memory identity.
	 */
	private String identityHash;

	/**
	 * Equals {@link #identityHash} only while the item is CONFIRMED; otherwise null.
	 */
	private String activeIdentityHash;

	private String sourceTurnId;

	@Builder.Default
	private MemoryStatus status = MemoryStatus.CANDIDATE;

	@Builder.Default
	private BigDecimal confidence = BigDecimal.ONE;

	private String schemaFingerprint;

	private LocalDateTime validUntil;

	private Long supersedesId;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
