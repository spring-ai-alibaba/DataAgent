/**
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.dataagent.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent Knowledge Entity Class
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentKnowledge {

	private Integer id;

	private Integer agentId;

	private String title;

	private String content;

	private String type; // document, qa, faq

	private String category;

	private String tags;

	private String status; // active, inactive

	private String sourceUrl;

	private String filePath;

	private Long fileSize;

	private String fileType;

	private String embeddingStatus; // pending, processing, completed, failed

	private Long creatorId;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime createTime;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime updateTime;

}
