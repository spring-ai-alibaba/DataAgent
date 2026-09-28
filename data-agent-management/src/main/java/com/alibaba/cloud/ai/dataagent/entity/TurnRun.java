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

import com.alibaba.cloud.ai.dataagent.enums.TurnStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One graph execution associated with a logical turn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnRun {

	private String runId;

	private String turnId;

	@Builder.Default
	private Integer attempt = 1;

	@Builder.Default
	private TurnStatus status = TurnStatus.CREATED;

	private String errorMessage;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
