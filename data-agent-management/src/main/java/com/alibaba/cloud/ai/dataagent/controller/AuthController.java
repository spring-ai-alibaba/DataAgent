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

import com.alibaba.cloud.ai.dataagent.dto.auth.*;
import com.alibaba.cloud.ai.dataagent.service.auth.AuthService;
import com.alibaba.cloud.ai.dataagent.util.JwtUtil;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * @author zihenzzz
 * @date 2026/2/13 23:47
 */

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	private final JwtUtil jwtUtil;

	@PostMapping("/login")
	public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request, ServerHttpRequest httpRequest) {
		String ip = extractIp(httpRequest);
		String userAgent = httpRequest.getHeaders().getFirst(HttpHeaders.USER_AGENT);
		TokenResponse token = authService.login(request, ip, userAgent);
		return ApiResponse.success("登录成功", token);
	}

	@PostMapping("/register")
	public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
		TokenResponse token = authService.register(request);
		return ApiResponse.success("注册成功", token);
	}

	@PostMapping("/refresh")
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
		TokenResponse token = authService.refreshToken(request);
		return ApiResponse.success("刷新成功", token);
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(ServerWebExchange exchange) {
		String sessionId = extractSessionId(exchange);
		authService.logout(sessionId);
		return ApiResponse.success("登出成功");
	}

	@GetMapping("/me")
	public ApiResponse<UserInfoResponse> me(ServerWebExchange exchange) {
		Long userId = (Long) exchange.getAttributes().get("userId");
		if (userId == null) {
			String token = extractToken(exchange.getRequest());
			if (token != null) {
				userId = jwtUtil.getUserIdFromToken(token);
			}
		}
		UserInfoResponse user = authService.getCurrentUser(userId);
		return ApiResponse.success("获取成功", user);
	}

	private String extractIp(ServerHttpRequest request) {
		String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
		if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
			return xForwardedFor.split(",")[0].trim();
		}
		InetSocketAddress remoteAddress = request.getRemoteAddress();
		return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
	}

	private String extractToken(ServerHttpRequest request) {
		String bearer = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (bearer != null && bearer.startsWith("Bearer ")) {
			return bearer.substring(7);
		}
		return null;
	}

	private String extractSessionId(ServerWebExchange exchange) {
		String sessionId = (String) exchange.getAttributes().get("sessionId");
		if (sessionId != null) {
			return sessionId;
		}
		String token = extractToken(exchange.getRequest());
		if (token != null) {
			try {
				return jwtUtil.getSessionIdFromToken(token);
			}
			catch (Exception e) {
				log.warn("从token解析sessionId失败", e);
			}
		}
		return null;
	}

}
