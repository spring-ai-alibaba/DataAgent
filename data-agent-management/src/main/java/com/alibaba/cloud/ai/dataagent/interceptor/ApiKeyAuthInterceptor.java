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

package com.alibaba.cloud.ai.dataagent.interceptor;

import com.alibaba.cloud.ai.dataagent.common.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.config.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.service.agent.AgentService;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatSessionService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

	private final AgentService agentService;

	private final ChatSessionService chatSessionService;

	private final DataAgentProperties properties;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		if (!properties.getApiKey().isEnabled()) {
			return true;
		}
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return true;
		}

		String apiKeyHeader = properties.getApiKey().getHeader();
		String apiKey = request.getHeader(apiKeyHeader);
		if (!StringUtils.hasText(apiKey)) {
			return writeUnauthorized(response, "缺少 API Key");
		}

		Long agentId = resolveAgentId(request);
		if (agentId == null) {
			return writeUnauthorized(response, "无法识别 Agent");
		}

		if (!agentService.validateApiKey(agentId, apiKey)) {
			return writeUnauthorized(response, "API Key 无效或未启用");
		}

		return true;
	}

	private Long resolveAgentId(HttpServletRequest request) {
		String agentIdParam = request.getParameter("agentId");
		Long agentId = parseLong(agentIdParam);
		if (agentId != null) {
			return agentId;
		}

		Map<String, String> uriVars = getUriVariables(request);
		if (uriVars == null || uriVars.isEmpty()) {
			return null;
		}

		agentId = parseLong(uriVars.get("agentId"));
		if (agentId != null) {
			return agentId;
		}

		agentId = parseLong(uriVars.get("id"));
		if (agentId != null) {
			return agentId;
		}

		String sessionId = uriVars.get("sessionId");
		if (StringUtils.hasText(sessionId)) {
			ChatSession session = chatSessionService.findBySessionId(sessionId);
			if (session != null && session.getAgentId() != null) {
				return session.getAgentId().longValue();
			}
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> getUriVariables(HttpServletRequest request) {
		Object attr = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		if (attr instanceof Map<?, ?> map) {
			return (Map<String, String>) map;
		}
		return null;
	}

	private Long parseLong(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private boolean writeUnauthorized(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		ApiResponse<Void> body = ApiResponse.error(message);
		response.getWriter().write(JsonUtil.getObjectMapper().writeValueAsString(body));
		return false;
	}

}
