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
package com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge;

import com.alibaba.cloud.ai.dataagent.common.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.common.enums.InEnum;
import com.alibaba.cloud.ai.dataagent.common.enums.KnowledgeType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentKnowledgeQueryDTO {

	/**
	 * 智能体ID（必填）
	 */
	@NotNull(message = "agentId不能为空")
	private Integer agentId;

	/**
	 * 知识标题（模糊查询）
	 */
	private String title;

	/**
	 * 知识类型：document-文档，qa-问答，faq-常见问题
	 */
	@InEnum(value = KnowledgeType.class, message = "type只能是DOCUMENT/QA/FAQ 之一")
	private String type;

	/**
	 * 向量化状态：pending-待处理，processing-处理中，completed-已完成，failed-失败
	 */
	@InEnum(value = EmbeddingStatus.class, message = "embeddingStatus只能是PENDING/PROCESSING/COMPLETED/FAILED之一")
	private String embeddingStatus;

	/**
	 * 当前页码（默认第1页）
	 */
	@NotNull(message = "pageNum不能为空")
	@Min(value = 1, message = "pageNum不能小于1")
	private Integer pageNum = 1;

	/**
	 * 每页大小（默认10条）
	 */
	@NotNull(message = "pageSize不能为空")
	@Min(value = 1, message = "pageSize不能小于1")
	private Integer pageSize = 10;

}
