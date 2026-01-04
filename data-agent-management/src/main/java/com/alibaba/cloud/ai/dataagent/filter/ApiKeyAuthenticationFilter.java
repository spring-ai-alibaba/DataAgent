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
package com.alibaba.cloud.ai.dataagent.filter;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.service.agent.AgentService;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	static final String API_KEY_HEADER = "X-API-Key";

	private static final String AGENT_SESSIONS_PATTERN = "/api/agent/{agentId}/sessions/**";

	private static final String AGENT_SESSIONS_BASE_PATTERN = "/api/agent/{agentId}/sessions";

	private static final String SESSIONS_PATTERN = "/api/sessions/{sessionId}/**";

	private static final String SESSIONS_BASE_PATTERN = "/api/sessions/{sessionId}";

	private final List<RequestMatcher> protectedMatchers;

	private final AgentService agentService;

	private final ChatSessionService chatSessionService;

	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	public ApiKeyAuthenticationFilter(List<RequestMatcher> protectedMatchers, AgentService agentService,
			ChatSessionService chatSessionService) {
		this.protectedMatchers = protectedMatchers;
		this.agentService = agentService;
		this.chatSessionService = chatSessionService;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return true;
		}
		return protectedMatchers.stream().noneMatch(matcher -> matcher.matches(request));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String apiKey = request.getHeader(API_KEY_HEADER);
		if (!StringUtils.hasText(apiKey)) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-API-Key header");
			return;
		}

		Agent agent = agentService.findByApiKey(apiKey.trim());
		if (agent == null) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid API key");
			return;
		}

		String path = resolvePath(request);
		if (!isAuthorizedForPath(path, agent)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid API key");
			return;
		}

		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(agent.getId(),
				apiKey, Collections.singletonList(new SimpleGrantedAuthority("ROLE_API")));
		SecurityContextHolder.getContext().setAuthentication(authentication);
		filterChain.doFilter(request, response);
	}

	private String resolvePath(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
			return uri.substring(contextPath.length());
		}
		return uri;
	}

	private boolean isAuthorizedForPath(String path, Agent agent) {
		String agentId = extractPathVariable(path, AGENT_SESSIONS_PATTERN, AGENT_SESSIONS_BASE_PATTERN, "agentId");
		if (agentId != null) {
			return matchesAgent(agent, agentId);
		}

		String sessionId = extractPathVariable(path, SESSIONS_PATTERN, SESSIONS_BASE_PATTERN, "sessionId");
		if (sessionId != null) {
			ChatSession session = chatSessionService.findBySessionId(sessionId);
			if (session == null || session.getAgentId() == null) {
				return false;
			}
			return matchesAgent(agent, String.valueOf(session.getAgentId()));
		}

		return true;
	}

	private String extractPathVariable(String path, String primaryPattern, String basePattern, String variable) {
		if (pathMatcher.match(primaryPattern, path)) {
			Map<String, String> variables = pathMatcher.extractUriTemplateVariables(primaryPattern, path);
			return variables.get(variable);
		}
		if (pathMatcher.match(basePattern, path)) {
			Map<String, String> variables = pathMatcher.extractUriTemplateVariables(basePattern, path);
			return variables.get(variable);
		}
		return null;
	}

	private boolean matchesAgent(Agent agent, String agentId) {
		if (agent == null || agent.getId() == null || !StringUtils.hasText(agentId)) {
			return false;
		}
		try {
			return agent.getId().longValue() == Long.parseLong(agentId);
		}
		catch (NumberFormatException ex) {
			return false;
		}
	}

}
