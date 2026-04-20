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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 报告生成结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportGenerationResponseVO {

	/**
	 * 报告生成任务ID
	 */
	private String taskId;

	/**
	 * 会话ID
	 */
	private String sessionId;

	/**
	 * 报告下载链接
	 */
	private String downloadUrl;

	/**
	 * 报告文件名
	 */
	private String filename;

	/**
	 * 报告生成状态：pending, processing, completed, failed
	 */
	private String status;

	/**
	 * 报告内容摘要
	 */
	private String summary;

	/**
	 * 创建时间
	 */
	private LocalDateTime createTime;

	/**
	 * 完成时间
	 */
	private LocalDateTime completeTime;

	/**
	 * 错误信息（如果生成失败）
	 */
	private String errorMessage;

}
