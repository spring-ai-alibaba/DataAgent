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
package com.alibaba.cloud.ai.dataagent.service.chat;

import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Generate session titles asynchronously via LLM and push results to frontend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTitleService {

	private static final String DEFAULT_TITLE = "新会话";

	private final ChatSessionService chatSessionService;

	private final SessionEventPublisher sessionEventPublisher;

	private final LlmService llmService;

	@Qualifier("dbOperationExecutor")
	private final ExecutorService executorService;

	private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();

	public void scheduleTitleGeneration(String sessionId, String userMessage) {
		if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(userMessage)) {
			return;
		}
		if (!runningTasks.add(sessionId)) {
			return;
		}
		CompletableFuture.runAsync(() -> generateAndPersist(sessionId, userMessage), executorService)
			.whenComplete((unused, throwable) -> runningTasks.remove(sessionId));
	}

	private void generateAndPersist(String sessionId, String userMessage) {
		try {
			ChatSession session = chatSessionService.findBySessionId(sessionId);
			if (session == null) {
				log.warn("Session {} not found when generating title", sessionId);
				return;
			}
			if (hasCustomTitle(session)) {
				log.debug("Session {} already has custom title, skip generating", sessionId);
				return;
			}

			String title = requestSummary(userMessage);
			if (!StringUtils.hasText(title)) {
				title = fallbackTitle(userMessage);
			}
			title = normalizeTitle(title);
			if (!StringUtils.hasText(title)) {
				log.warn("LLM returned empty title for session {}", sessionId);
				return;
			}

			chatSessionService.renameSession(sessionId, title);
			sessionEventPublisher.publishTitleUpdated(session.getAgentId(), sessionId, title);
			log.info("Generated session title '{}' for session {}", title, sessionId);
		}
		catch (Exception ex) {
			log.error("Failed to generate session title for session {}: {}", sessionId, ex.getMessage());
		}
	}

	private boolean hasCustomTitle(ChatSession session) {
		return StringUtils.hasText(session.getTitle()) && !DEFAULT_TITLE.equals(session.getTitle());
	}

	private String requestSummary(String userMessage) {
		try {
			String systemPrompt = """
					你是一名对话助手，请根据用户的第一条输入生成不超过20个字的会话标题。
					使用中文输出，避免使用标点或引号，仅保留核心主题。
					""";
			String userPrompt = "用户输入：" + userMessage;
			Flux<String> responseFlux = llmService.toStringFlux(llmService.call(systemPrompt, userPrompt));
			return responseFlux.collect(StringBuilder::new, StringBuilder::append)
				.map(StringBuilder::toString)
				.block(Duration.ofSeconds(15));
		}
		catch (Exception ex) {
			log.warn("LLM title generation failed: {}", ex.getMessage());
			return null;
		}
	}

	private String normalizeTitle(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		String sanitized = raw.replaceAll("[\\r\\n]+", " ").replaceAll("[\"“”]+", "").trim();
		if (sanitized.length() > 20) {
			sanitized = sanitized.substring(0, 20);
		}
		return sanitized;
	}

	private String fallbackTitle(String userMessage) {
		String text = userMessage.replaceAll("\\s+", " ").trim();
		if (text.length() > 20) {
			text = text.substring(0, 20);
		}
		return StringUtils.hasText(text) ? text : DEFAULT_TITLE;
	}

}
