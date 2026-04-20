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
package com.alibaba.cloud.ai.dataagent.service.auth;

import com.alibaba.cloud.ai.dataagent.config.AuthProperties;
import com.alibaba.cloud.ai.dataagent.dto.auth.*;
import com.alibaba.cloud.ai.dataagent.entity.LoginLog;
import com.alibaba.cloud.ai.dataagent.entity.User;
import com.alibaba.cloud.ai.dataagent.entity.UserSession;
import com.alibaba.cloud.ai.dataagent.exception.InvalidInputException;
import com.alibaba.cloud.ai.dataagent.mapper.*;
import com.alibaba.cloud.ai.dataagent.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

	private final UserMapper userMapper;

	private final UserSessionMapper userSessionMapper;

	private final LoginLogMapper loginLogMapper;

	private final RoleMapper roleMapper;

	private final UserRoleMapper userRoleMapper;

	private final JwtUtil jwtUtil;

	private final AuthProperties authProperties;

	@Override
	public TokenResponse login(LoginRequest request, String ipAddress, String userAgent) {
		User user = userMapper.findByUsername(request.getUsername());

		// 用户不存在
		if (user == null) {
			recordLoginLog(null, request.getUsername(), 0, "用户不存在", ipAddress, userAgent);
			throw new InvalidInputException("用户名或密码错误");
		}

		// 检查用户状态
		if (user.getStatus() == 0) {
			recordLoginLog(user.getId(), request.getUsername(), 0, "账号已禁用", ipAddress, userAgent);
			throw new InvalidInputException("账号已被禁用，请联系管理员");
		}

		// 检查是否被锁定
		if (user.getStatus() == 2 && user.getLockedUntil() != null
				&& LocalDateTime.now().isBefore(user.getLockedUntil())) {
			recordLoginLog(user.getId(), request.getUsername(), 0, "账号已锁定", ipAddress, userAgent);
			throw new InvalidInputException("账号已锁定，请稍后再试");
		}

		// 如果锁定时间已过，解锁账号
		if (user.getStatus() == 2 && user.getLockedUntil() != null
				&& LocalDateTime.now().isAfter(user.getLockedUntil())) {
			user.setStatus(1);
			userMapper.updateById(User.builder().id(user.getId()).status(1).build());
			userMapper.updateFailedLoginCount(user.getId(), 0);
			userMapper.updateLockedUntil(user.getId(), null);
		}

		// 验证密码
		if (!BCrypt.checkpw(request.getPassword(), user.getPassword())) {
			handleFailedLogin(user, ipAddress, userAgent);
			throw new InvalidInputException("用户名或密码错误");
		}

		// 登录成功
		return handleSuccessfulLogin(user, ipAddress, userAgent);
	}

	@Override
	public TokenResponse register(RegisterRequest request) {
		// 检查用户名唯一性
		if (userMapper.findByUsername(request.getUsername()) != null) {
			throw new InvalidInputException("用户名已存在");
		}

		// 检查邮箱唯一性
		if (userMapper.findByEmail(request.getEmail()) != null) {
			throw new InvalidInputException("邮箱已被注册");
		}

		// 创建用户
		User user = User.builder()
			.username(request.getUsername())
			.password(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()))
			.email(request.getEmail())
			.phone(request.getPhone())
			.realName(request.getRealName())
			.status(1)
			.userType(request.getUserType() != null ? request.getUserType() : 0)
			.failedLoginCount(0)
			.loginCount(0)
			.isDeleted(0)
			.build();
		userMapper.insert(user);

		// 分配角色
		String roleCode = request.getRoleCode() != null ? request.getRoleCode() : "ANALYST";
		Long roleId = userRoleMapper.findRoleIdByCode(roleCode);
		if (roleId != null) {
			userRoleMapper.insert(user.getId(), roleId);
		}

		// 自动登录并返回 token
		List<String> roles = roleMapper.findRoleCodesByUserId(user.getId());
		if (roles == null || roles.isEmpty()) {
			roles = Collections.emptyList();
		}

		String sessionId = jwtUtil.generateSessionId();
		String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), roles, sessionId);
		String refreshToken = jwtUtil.generateRefreshToken(user.getId(), sessionId);

		// 创建会话
		createSession(user.getId(), sessionId, accessToken, refreshToken, "web", null, null);

		return buildTokenResponse(accessToken, refreshToken, user, roles);
	}

	@Override
	public TokenResponse refreshToken(RefreshTokenRequest request) {
		// 查找活跃会话
		UserSession session = userSessionMapper.findByRefreshToken(request.getRefreshToken());
		if (session == null) {
			throw new InvalidInputException("无效的刷新令牌");
		}

		// 验证 refresh token
		try {
			jwtUtil.parseToken(request.getRefreshToken());
		}
		catch (Exception e) {
			userSessionMapper.deactivateSession(session.getSessionId(), 2);
			throw new InvalidInputException("刷新令牌已过期，请重新登录");
		}

		// 废弃旧会话
		userSessionMapper.deactivateSession(session.getSessionId(), 2);

		// 查询用户和角色
		User user = userMapper.findById(session.getUserId());
		if (user == null || user.getStatus() != 1) {
			throw new InvalidInputException("用户状态异常");
		}

		List<String> roles = roleMapper.findRoleCodesByUserId(user.getId());
		if (roles == null) {
			roles = Collections.emptyList();
		}

		// 生成新 token 对
		String newSessionId = jwtUtil.generateSessionId();
		String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), roles, newSessionId);
		String newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), newSessionId);

		createSession(user.getId(), newSessionId, newAccessToken, newRefreshToken, session.getDeviceType(),
				session.getDeviceInfo(), session.getIpAddress());

		return buildTokenResponse(newAccessToken, newRefreshToken, user, roles);
	}

	@Override
	public void logout(String sessionId) {
		if (sessionId != null) {
			userSessionMapper.deactivateSession(sessionId, 1);
		}
	}

	@Override
	public UserInfoResponse getCurrentUser(Long userId) {
		User user = userMapper.findById(userId);
		if (user == null) {
			throw new InvalidInputException("用户不存在");
		}

		List<String> roles = roleMapper.findRoleCodesByUserId(userId);
		if (roles == null) {
			roles = Collections.emptyList();
		}

		return UserInfoResponse.builder()
			.id(user.getId())
			.username(user.getUsername())
			.email(user.getEmail())
			.realName(user.getRealName())
			.avatar(user.getAvatar())
			.userType(user.getUserType())
			.roles(roles)
			.build();
	}

	private void handleFailedLogin(User user, String ipAddress, String userAgent) {
		int failedCount = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
		userMapper.updateFailedLoginCount(user.getId(), failedCount);

		// 达到最大失败次数，锁定账号
		if (failedCount >= authProperties.getLockout().getMaxAttempts()) {
			LocalDateTime lockedUntil = LocalDateTime.now()
				.plusSeconds(authProperties.getLockout().getLockDuration() / 1000);
			userMapper.updateLockedUntil(user.getId(), lockedUntil);
			userMapper.updateById(User.builder().id(user.getId()).status(2).build());
			log.warn("用户 {} 因连续{}次登录失败已被锁定", user.getUsername(), failedCount);
		}

		recordLoginLog(user.getId(), user.getUsername(), 0, "密码错误", ipAddress, userAgent);
	}

	private TokenResponse handleSuccessfulLogin(User user, String ipAddress, String userAgent) {
		// 更新登录信息
		userMapper.updateLoginInfo(user.getId(), LocalDateTime.now(), ipAddress);

		List<String> roles = roleMapper.findRoleCodesByUserId(user.getId());
		if (roles == null) {
			roles = Collections.emptyList();
		}

		// 生成 token
		String sessionId = jwtUtil.generateSessionId();
		String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), roles, sessionId);
		String refreshToken = jwtUtil.generateRefreshToken(user.getId(), sessionId);

		// 创建会话
		createSession(user.getId(), sessionId, accessToken, refreshToken, "web", userAgent, ipAddress);

		// 记录登录日志
		recordLoginLog(user.getId(), user.getUsername(), 1, null, ipAddress, userAgent);

		return buildTokenResponse(accessToken, refreshToken, user, roles);
	}

	private void createSession(Long userId, String sessionId, String accessToken, String refreshToken,
			String deviceType, String deviceInfo, String ipAddress) {
		LocalDateTime expiresAt = LocalDateTime.now()
			.plusSeconds(authProperties.getJwt().getAccessTokenExpiration() / 1000);

		UserSession session = UserSession.builder()
			.sessionId(sessionId)
			.userId(userId)
			.token(accessToken)
			.refreshToken(refreshToken)
			.deviceType(deviceType)
			.deviceInfo(deviceInfo)
			.ipAddress(ipAddress)
			.expiresAt(expiresAt)
			.isActive(1)
			.build();
		userSessionMapper.insert(session);
	}

	private TokenResponse buildTokenResponse(String accessToken, String refreshToken, User user, List<String> roles) {
		UserInfoResponse userInfo = UserInfoResponse.builder()
			.id(user.getId())
			.username(user.getUsername())
			.email(user.getEmail())
			.realName(user.getRealName())
			.avatar(user.getAvatar())
			.userType(user.getUserType())
			.roles(roles)
			.build();

		return TokenResponse.builder()
			.accessToken(accessToken)
			.refreshToken(refreshToken)
			.expiresIn(jwtUtil.getAccessTokenExpirationSeconds())
			.tokenType("Bearer")
			.user(userInfo)
			.build();
	}

	private void recordLoginLog(Long userId, String username, int status, String failureReason, String ipAddress,
			String userAgent) {
		try {
			LoginLog loginLog = LoginLog.builder()
				.userId(userId)
				.username(username)
				.loginType("password")
				.loginStatus(status)
				.failureReason(failureReason)
				.ipAddress(ipAddress)
				.deviceInfo(userAgent)
				.build();
			loginLogMapper.insert(loginLog);
		}
		catch (Exception e) {
			log.error("记录登录日志失败", e);
		}
	}

}
