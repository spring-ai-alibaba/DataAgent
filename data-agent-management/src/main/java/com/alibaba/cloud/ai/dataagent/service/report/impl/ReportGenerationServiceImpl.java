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
package com.alibaba.cloud.ai.dataagent.service.report.impl;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.dto.ReportGenerationRequestDTO;
import com.alibaba.cloud.ai.dataagent.entity.ChatMessage;
import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatMessageService;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatSessionService;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.service.report.ReportGenerationService;
import com.alibaba.cloud.ai.dataagent.util.ReportTemplateUtil;
import com.alibaba.cloud.ai.dataagent.vo.ReportGenerationResponseVO;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 报告生成服务实现类
 */
@Slf4j
@Service
public class ReportGenerationServiceImpl implements ReportGenerationService {

	private final CompiledGraph compiledGraph;

	private final StateGraph stateGraph;

	private final ChatSessionService chatSessionService;

	private final ChatMessageService chatMessageService;

	private final ReportTemplateUtil reportTemplateUtil;

	private final FileStorageService fileStorageService;

	private final FileStorageProperties fileStorageProperties;

	private final ExecutorService executorService;

	// 内存中存储任务状态
	private final ConcurrentHashMap<String, ReportGenerationResponseVO> taskStatusMap = new ConcurrentHashMap<>();

	@Value("classpath:prompts/report-generator.txt")
	private Resource reportGeneratorPrompt;

	public ReportGenerationServiceImpl(StateGraph stateGraph, ChatSessionService chatSessionService,
			ChatMessageService chatMessageService, ReportTemplateUtil reportTemplateUtil,
			FileStorageService fileStorageService, FileStorageProperties fileStorageProperties,
			ExecutorService executorService) throws Exception {
		this.stateGraph = stateGraph;
		this.compiledGraph = stateGraph
			.compile(CompileConfig.builder().interruptBefore(Constant.HUMAN_FEEDBACK_NODE).build());
		this.chatSessionService = chatSessionService;
		this.chatMessageService = chatMessageService;
		this.reportTemplateUtil = reportTemplateUtil;
		this.fileStorageService = fileStorageService;
		this.fileStorageProperties = fileStorageProperties;
		this.executorService = executorService;
	}

	@Override
	public CompletableFuture<ReportGenerationResponseVO> generateReportAsync(ReportGenerationRequestDTO request) {
		return CompletableFuture.supplyAsync(() -> generateReport(request), executorService);
	}

	@Override
	public ReportGenerationResponseVO generateReport(ReportGenerationRequestDTO request) {
		String taskId = UUID.randomUUID().toString();
		LocalDateTime createTime = LocalDateTime.now();

		// 初始化响应对象
		ReportGenerationResponseVO response = ReportGenerationResponseVO.builder()
			.taskId(taskId)
			.status("processing")
			.createTime(createTime)
			.build();

		taskStatusMap.put(taskId, response);

		// 使用重试机制执行报告生成
		int maxRetries = 3;
		int retryCount = 0;
		Exception lastException = null;

		while (retryCount < maxRetries) {
			try {
				if (retryCount > 0) {
					log.info("[{}] 第 {} 次重试生成报告...", taskId, retryCount);
				}

				// 执行报告生成（单次的逻辑）
				return doGenerateReport(request, taskId, response);

			}
			catch (Exception e) {
				lastException = e;
				retryCount++;
				log.warn("[{}] 报告生成失败（第 {} 次尝试）: {}", taskId, retryCount, e.getMessage());

				if (retryCount < maxRetries) {
					// 等待一段时间后重试（指数退避）
					long waitTime = (long) Math.pow(2, retryCount) * 1000; // 2秒, 4秒
					log.info("[{}] 等待 {} 毫秒后重试...", taskId, waitTime);
					try {
						Thread.sleep(waitTime);
					}
					catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}

		// 所有重试都失败了
		log.error("[{}] 报告生成失败，已重试 {} 次: {}", taskId, maxRetries - 1,
				lastException != null ? lastException.getMessage() : "未知错误", lastException);
		response.setStatus("failed");
		response.setErrorMessage("报告生成失败（已重试 " + (maxRetries - 1) + " 次）: "
				+ (lastException != null ? lastException.getMessage() : "未知错误"));
		response.setCompleteTime(LocalDateTime.now());
		taskStatusMap.put(taskId, response);
		return response;
	}

	/**
	 * 执行单次报告生成（不带重试逻辑）
	 */
	private ReportGenerationResponseVO doGenerateReport(ReportGenerationRequestDTO request, String taskId,
			ReportGenerationResponseVO response) throws Exception {
		log.info("[{}] 开始生成报告，查询内容: {}", taskId, request.getQuery());

		// 1. 创建或获取会话
		String sessionId = getOrCreateSession(request);
		response.setSessionId(sessionId);
		log.info("[{}] 会话创建成功: {}", taskId, sessionId);

		// 2. 执行工作流生成报告内容
		log.info("[{}] 开始执行工作流...", taskId);
		String reportContent = executeWorkflowAndGenerateReport(request);
		log.info("[{}] 工作流执行完成，报告内容长度: {}", taskId, reportContent.length());

		// 3. 保存用户查询消息
		saveUserMessage(sessionId, request.getQuery());

		// 4. 生成HTML报告并保存
		String downloadUrl = generateAndSaveHtmlReport(reportContent, taskId);

		// 5. 保存助手回复消息
		saveAssistantMessage(sessionId, reportContent, downloadUrl);

		// 6. 更新响应
		response.setStatus("completed");
		response.setDownloadUrl(downloadUrl);
		response.setFilename(downloadUrl.substring(downloadUrl.lastIndexOf("/") + 1));
		response.setSummary(generateSummary(reportContent));
		response.setCompleteTime(LocalDateTime.now());

		taskStatusMap.put(taskId, response);

		log.info("[{}] 报告生成完成，下载链接: {}", taskId, downloadUrl);

		return response;
	}

	@Override
	public ReportGenerationResponseVO getReportStatus(String taskId) {
		return taskStatusMap.get(taskId);
	}

	/**
	 * 获取或创建会话
	 */
	private String getOrCreateSession(ReportGenerationRequestDTO request) {
		if (StringUtils.hasText(request.getSessionId())) {
			// 验证会话是否存在
			// 这里简化处理，实际应该查询数据库验证
			return request.getSessionId();
		}

		// 创建新会话
		ChatSession session = chatSessionService.createSession(request.getAgentId(), request.getReportTitle(),
				request.getUserId());
		return session.getId();
	}

	/**
	 * 执行工作流生成报告内容 - 在独立线程中执行
	 */
	private String executeWorkflowAndGenerateReport(ReportGenerationRequestDTO request) throws Exception {
		return CompletableFuture.supplyAsync(() -> {
			try {
				String agentId = String.valueOf(request.getAgentId());
				String query = request.getQuery();

				// 构建初始状态
				Map<String, Object> initialState = new HashMap<>();
				initialState.put(Constant.INPUT_KEY, query);
				initialState.put(Constant.AGENT_ID, agentId);
				initialState.put(Constant.IS_ONLY_NL2SQL, false);
				initialState.put(Constant.HUMAN_REVIEW_ENABLED, false);

				// 执行工作流
				OverAllState finalState = compiledGraph.invoke(initialState, RunnableConfig.builder().build())
					.orElseThrow(() -> new RuntimeException("工作流执行失败"));

				// 获取报告内容
				Object result = finalState.value(Constant.RESULT);
				log.info("工作流返回结果类型: {}, 值: {}", result != null ? result.getClass().getName() : "null", result);

				if (result == null) {
					throw new RuntimeException("报告生成失败：工作流未返回结果");
				}

				// 处理Optional类型
				if (result instanceof java.util.Optional<?> optionalResult) {
					if (optionalResult.isEmpty()) {
						throw new RuntimeException("报告生成失败：工作流返回空Optional");
					}
					result = optionalResult.get();
					log.info("从Optional中提取结果，类型: {}", result.getClass().getName());
				}

				// 处理Flux类型结果
				if (result instanceof Flux) {
					return collectFluxContent((Flux<?>) result);
				}

				// 直接返回字符串结果
				String resultStr = result.toString();
				if (resultStr.isEmpty()) {
					throw new RuntimeException("报告生成失败：工作流返回空结果");
				}
				return resultStr;
			}
			catch (Exception e) {
				throw new RuntimeException("工作流执行失败: " + e.getMessage(), e);
			}
		}, executorService).get();
	}

	/**
	 * 收集Flux流内容 - 在独立线程中执行避免阻塞
	 */
	private String collectFluxContent(Flux<?> flux) {
		return CompletableFuture.supplyAsync(() -> {
			StringBuilder contentBuilder = new StringBuilder();

			flux.collectList().map(list -> {
				for (Object item : list) {
					if (item instanceof ChatResponse chatResponse) {
						chatResponse.getResults().forEach(result -> {
							if (result.getOutput() != null && result.getOutput().getText() != null) {
								contentBuilder.append(result.getOutput().getText());
							}
						});
					}
					else if (item instanceof String str) {
						contentBuilder.append(str);
					}
					else {
						// 尝试其他类型转换
						contentBuilder.append(item.toString());
					}
				}
				return contentBuilder.toString();
			}).block();

			return contentBuilder.toString();
		}, executorService).join();
	}

	/**
	 * 生成并保存HTML报告
	 */
	private String generateAndSaveHtmlReport(String reportContent, String taskId) throws IOException {
		// 构建完整HTML
		StringBuilder htmlContent = new StringBuilder();
		htmlContent.append(reportTemplateUtil.getHeader());
		htmlContent.append(reportContent);
		htmlContent.append(reportTemplateUtil.getFooter());

		// 生成文件名
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		String filename = "report_" + timestamp + "_" + taskId.substring(0, 8) + ".html";

		// 保存到本地存储
		Path tempDir = Files.createTempDirectory("reports");
		Path tempFile = tempDir.resolve(filename);
		Files.write(tempFile, htmlContent.toString().getBytes(StandardCharsets.UTF_8));

		// 使用FileStorageService保存文件
		String storagePath = saveHtmlFile(tempFile, filename);

		// 删除临时文件
		Files.deleteIfExists(tempFile);
		Files.deleteIfExists(tempDir);

		// 返回下载链接
		return fileStorageService.getFileUrl(storagePath);
	}

	/**
	 * 保存HTML文件到存储
	 */
	private String saveHtmlFile(Path tempFile, String filename) throws IOException {
		// 构建存储路径
		String subPath = "reports";
		String storagePath = subPath + "/" + filename;

		// 获取本地存储路径
		Path localBasePath = fileStorageProperties.getLocalBasePath();
		Path targetPath = localBasePath.resolve(storagePath);

		// 确保目录存在
		Path parentDir = targetPath.getParent();
		if (!Files.exists(parentDir)) {
			Files.createDirectories(parentDir);
		}

		// 复制文件
		Files.copy(tempFile, targetPath);

		log.info("HTML报告已保存到: {}", targetPath);
		return storagePath;
	}

	/**
	 * 生成内容摘要
	 */
	private String generateSummary(String content) {
		if (content == null || content.isEmpty()) {
			return "";
		}

		// 移除Markdown标记，提取纯文本
		String plainText = content.replaceAll("#+", "")
			.replaceAll("\\*\\*", "")
			.replaceAll("\\*", "")
			.replaceAll("`", "")
			.replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", "$1")
			.trim();

		// 返回前200个字符作为摘要
		if (plainText.length() > 200) {
			return plainText.substring(0, 200) + "...";
		}
		return plainText;
	}

	/**
	 * 保存用户消息
	 */
	private void saveUserMessage(String sessionId, String content) {
		try {
			ChatMessage message = ChatMessage.builder()
				.sessionId(sessionId)
				.role("user")
				.content(content)
				.messageType("text")
				.build();
			chatMessageService.saveMessage(message);
		}
		catch (Exception e) {
			log.warn("保存用户消息失败: {}", e.getMessage());
		}
	}

	/**
	 * 保存助手消息
	 */
	private void saveAssistantMessage(String sessionId, String content, String downloadUrl) {
		try {
			String metadata = "{\"downloadUrl\": \"" + downloadUrl + "\"}";
			ChatMessage message = ChatMessage.builder()
				.sessionId(sessionId)
				.role("assistant")
				.content(content)
				.messageType("report")
				.metadata(metadata)
				.build();
			chatMessageService.saveMessage(message);
		}
		catch (Exception e) {
			log.warn("保存助手消息失败: {}", e.getMessage());
		}
	}

}
