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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.ReportGenerationRequestDTO;
import com.alibaba.cloud.ai.dataagent.service.report.ReportGenerationService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import com.alibaba.cloud.ai.dataagent.vo.ReportGenerationResponseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * 报告生成控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ReportGenerationController {

	private final ReportGenerationService reportGenerationService;

	/**
	 * 同步生成报告
	 *
	 * 该接口会执行完整的数据分析工作流，生成报告并返回下载链接。 由于报告生成可能需要较长时间，建议使用异步接口。
	 * @param request 报告生成请求
	 * @return 报告生成结果，包含下载链接
	 */
	@PostMapping("/generate")
	public ResponseEntity<ApiResponse<ReportGenerationResponseVO>> generateReport(
			@RequestBody ReportGenerationRequestDTO request) {
		try {
			// 参数校验
			if (request.getAgentId() == null) {
				return ResponseEntity.badRequest().body(ApiResponse.error("智能体ID不能为空"));
			}
			if (!StringUtils.hasText(request.getQuery())) {
				return ResponseEntity.badRequest().body(ApiResponse.error("查询内容不能为空"));
			}

			log.info("收到报告生成请求，智能体ID: {}, 查询内容: {}", request.getAgentId(), request.getQuery());

			// 执行报告生成
			ReportGenerationResponseVO result = reportGenerationService.generateReport(request);

			if ("failed".equals(result.getStatus())) {
				return ResponseEntity.internalServerError()
					.body(ApiResponse.error("报告生成失败: " + result.getErrorMessage()));
			}

			return ResponseEntity.ok(ApiResponse.success("报告生成成功", result));

		}
		catch (Exception e) {
			log.error("报告生成接口异常", e);
			return ResponseEntity.internalServerError().body(ApiResponse.error("报告生成失败: " + e.getMessage()));
		}
	}

	/**
	 * 异步生成报告
	 *
	 * 该接口会立即返回任务ID，报告生成在后台进行。 可以通过任务ID查询报告生成状态。
	 * @param request 报告生成请求
	 * @return 任务ID和初始状态
	 */
	@PostMapping("/generate/async")
	public ResponseEntity<ApiResponse<ReportGenerationResponseVO>> generateReportAsync(
			@RequestBody ReportGenerationRequestDTO request) {
		try {
			// 参数校验
			if (request.getAgentId() == null) {
				return ResponseEntity.badRequest().body(ApiResponse.error("智能体ID不能为空"));
			}
			if (!StringUtils.hasText(request.getQuery())) {
				return ResponseEntity.badRequest().body(ApiResponse.error("查询内容不能为空"));
			}

			log.info("收到异步报告生成请求，智能体ID: {}, 查询内容: {}", request.getAgentId(), request.getQuery());

			// 异步执行报告生成
			CompletableFuture<ReportGenerationResponseVO> future = reportGenerationService.generateReportAsync(request);

			// 立即返回任务ID（future完成后状态会更新）
			ReportGenerationResponseVO initialResponse = ReportGenerationResponseVO.builder()
				.taskId(future.getNow(null) != null ? future.getNow(null).getTaskId() : null)
				.status("pending")
				.build();

			return ResponseEntity.ok(ApiResponse.success("报告生成任务已创建", initialResponse));

		}
		catch (Exception e) {
			log.error("异步报告生成接口异常", e);
			return ResponseEntity.internalServerError().body(ApiResponse.error("报告生成任务创建失败: " + e.getMessage()));
		}
	}

	/**
	 * 查询报告生成状态
	 * @param taskId 任务ID
	 * @return 报告生成状态和结果
	 */
	@GetMapping("/status/{taskId}")
	public ResponseEntity<ApiResponse<ReportGenerationResponseVO>> getReportStatus(
			@PathVariable("taskId") String taskId) {
		try {
			if (!StringUtils.hasText(taskId)) {
				return ResponseEntity.badRequest().body(ApiResponse.error("任务ID不能为空"));
			}

			ReportGenerationResponseVO result = reportGenerationService.getReportStatus(taskId);

			if (result == null) {
				return ResponseEntity.notFound().build();
			}

			return ResponseEntity.ok(ApiResponse.success("查询成功", result));

		}
		catch (Exception e) {
			log.error("查询报告状态接口异常", e);
			return ResponseEntity.internalServerError().body(ApiResponse.error("查询失败: " + e.getMessage()));
		}
	}

}
