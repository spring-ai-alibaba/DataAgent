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
package com.alibaba.cloud.ai.dataagent.vo;

import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentKnowledgeVO {

	private Integer id;

	private Integer agentId;

	private String title;

	// DOCUMENT, QA, FAQ
	private String type;

	// FAQ QA 问题
	private String question;

	// 当type=QA, FAQ时有内容
	private String content;

	@JsonFormat(shape = JsonFormat.Shape.BOOLEAN)
	private Boolean isRecall;

	// 向量化状态：PENDING待处理，PROCESSING处理中，COMPLETED已完成，FAILED失败
	private EmbeddingStatus embeddingStatus;

	// 操作失败的错误信息
	private String errorMsg;

	// 分块策略类型：token, recursive
	private String splitterType;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updatedTime;

}
