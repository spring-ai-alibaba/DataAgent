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
package com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge;

import com.alibaba.cloud.ai.dataagent.annotation.InEnum;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 创建知识DTO
 */
@Data
public class CreateKnowledgeDTO {

	/**
	 * 智能体ID
	 */
	@NotNull(message = "智能体ID不能为空")
	private Integer agentId;

	/**
	 * 知识标题
	 */
	@NotBlank(message = "知识标题不能为空")
	private String title;

	/**
	 * 知识类型：DOCUMENT, QA, FAQ
	 */
	@NotBlank(message = "知识类型不能为空")
	@InEnum(value = KnowledgeType.class, message = "type只能是DOCUMENT/QA/FAQ 之一")
	private String type;

	/**
	 * 问题（FAQ和QA类型时必填）
	 */
	private String question;

	/**
	 * 内容（当type=QA, FAQ时必填）
	 */
	private String content;

	/**
	 * 上传的文件（当type=DOCUMENT时必填）
	 */
	private MultipartFile file;

}
