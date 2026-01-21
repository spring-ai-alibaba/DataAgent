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

import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
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

	// DOCUMENT, QA, FAQ
	private KnowledgeType type;

	// FAQ QA 问题
	private String question;

	// 当type=QA, FAQ时有内容
	private String content;

	// 业务状态: 1=召回, 0=非召回
	private Integer isRecall;

	// 向量化状态：PENDING待处理，PROCESSING处理中，COMPLETED已完成，FAILED失败
	private EmbeddingStatus embeddingStatus;

	// 操作失败的错误信息
	private String errorMsg;

	private String sourceFilename;

	// 文件路径
	private String filePath;

	// 文件大小（字节）
	private Long fileSize;

	// 文件类型
	private String fileType;

	// 分块策略类型：token, recursive
	// 默认值是 token
	private String splitterType;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updatedTime;

	// 0=未删除, 1=已删除
	private Integer isDeleted;

	// 0=物理资源（文件和向量）未清理, 1=物理资源已清理
	// 默认值是 0
	private Integer isResourceCleaned;

}
