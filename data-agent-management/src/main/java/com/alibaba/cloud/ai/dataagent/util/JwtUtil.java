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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtUtil {

	private final SecretKey secretKey;

	private final AuthProperties authProperties;

	public JwtUtil(AuthProperties authProperties) {
		this.authProperties = authProperties;
		this.secretKey = Keys.hmacShaKeyFor(authProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
	}

	public String generateAccessToken(Long userId, String username, List<String> roles, String sessionId) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + authProperties.getJwt().getAccessTokenExpiration());

		return Jwts.builder()
			.subject(String.valueOf(userId))
			.claim("username", username)
			.claim("roles", roles)
			.claim("sessionId", sessionId)
			.claim("type", "access")
			.issuedAt(now)
			.expiration(expiry)
			.signWith(secretKey)
			.compact();
	}

	public String generateRefreshToken(Long userId, String sessionId) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + authProperties.getJwt().getRefreshTokenExpiration());

		return Jwts.builder()
			.subject(String.valueOf(userId))
			.claim("sessionId", sessionId)
			.claim("type", "refresh")
			.issuedAt(now)
			.expiration(expiry)
			.signWith(secretKey)
			.compact();
	}

	public Claims parseToken(String token) {
		return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
	}

	public Long getUserIdFromToken(String token) {
		return Long.parseLong(parseToken(token).getSubject());
	}

	public String getSessionIdFromToken(String token) {
		return parseToken(token).get("sessionId", String.class);
	}

	public long getAccessTokenExpirationSeconds() {
		return authProperties.getJwt().getAccessTokenExpiration() / 1000;
	}

	public String generateSessionId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

}
