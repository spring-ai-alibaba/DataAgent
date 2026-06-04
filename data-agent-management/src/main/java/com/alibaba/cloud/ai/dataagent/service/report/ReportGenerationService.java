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
package com.alibaba.cloud.ai.dataagent.service.report;

import com.alibaba.cloud.ai.dataagent.dto.ReportGenerationRequestDTO;
import com.alibaba.cloud.ai.dataagent.vo.ReportGenerationResponseVO;

import java.util.concurrent.CompletableFuture;

/**
 * 报告生成服务接口
 */
public interface ReportGenerationService {

	/**
	 * 异步生成报告
	 * @param request 报告生成请求
	 * @return 报告生成结果（包含下载链接）
	 */
	CompletableFuture<ReportGenerationResponseVO> generateReportAsync(ReportGenerationRequestDTO request);

	/**
	 * 同步生成报告
	 * @param request 报告生成请求
	 * @return 报告生成结果（包含下载链接）
	 */
	ReportGenerationResponseVO generateReport(ReportGenerationRequestDTO request);

	/**
	 * 根据任务ID查询报告生成状态
	 * @param taskId 任务ID
	 * @return 报告生成结果
	 */
	ReportGenerationResponseVO getReportStatus(String taskId);

}
