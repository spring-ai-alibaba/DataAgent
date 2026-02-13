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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.entity.UserSession;
import com.alibaba.cloud.ai.dataagent.mapper.UserSessionMapper;
import com.alibaba.cloud.ai.dataagent.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;


/**
 * @author zihenzzz
 * @date 2026/2/14 0:25
 */

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

	private final JwtUtil jwtUtil;

	private final UserSessionMapper userSessionMapper;

	private static final List<String> PUBLIC_PATHS = List.of("/api/auth/login", "/api/auth/register",
			"/api/auth/refresh", "/api/echo");

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getPath().value();

		// 非 API 路径直接放行（静态资源等）
		if (!path.startsWith("/api/")) {
			return chain.filter(exchange);
		}

		// 白名单路径放行
		if (isPublicPath(path)) {
			return chain.filter(exchange);
		}

		// 提取 token
		String token = extractToken(exchange.getRequest());
		if (token == null) {
			return unauthorizedResponse(exchange, "未提供认证令牌");
		}

		try {
			Claims claims = jwtUtil.parseToken(token);
			Long userId = Long.parseLong(claims.getSubject());
			String sessionId = claims.get("sessionId", String.class);
			String username = claims.get("username", String.class);

			// 验证会话是否活跃
			UserSession session = userSessionMapper.findBySessionId(sessionId);
			if (session == null || session.getIsActive() != 1) {
				return unauthorizedResponse(exchange, "会话已过期或已失效");
			}

			// 更新最后活动时间
			userSessionMapper.updateLastActivity(sessionId);

			// 将用户信息放入请求属性，供下游使用
			exchange.getAttributes().put("userId", userId);
			exchange.getAttributes().put("sessionId", sessionId);
			exchange.getAttributes().put("username", username);

			return chain.filter(exchange);
		}
		catch (ExpiredJwtException e) {
			return unauthorizedResponse(exchange, "令牌已过期");
		}
		catch (Exception e) {
			log.warn("JWT验证失败: {}", e.getMessage());
			return unauthorizedResponse(exchange, "无效的认证令牌");
		}
	}

	private boolean isPublicPath(String path) {
		return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
	}

	private String extractToken(ServerHttpRequest request) {
		String bearer = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (bearer != null && bearer.startsWith("Bearer ")) {
			return bearer.substring(7);
		}
		return null;
	}

	private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
		exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
		String body = "{\"success\":false,\"message\":\"" + message + "\"}";
		DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
		return exchange.getResponse().writeWith(Mono.just(buffer));
	}

}
