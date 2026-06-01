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
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.UserSession;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserSessionMapper {

	@Insert("""
			INSERT INTO sys_user_session (session_id, user_id, token, refresh_token,
				device_type, device_info, ip_address, login_time, expires_at, last_activity_time, is_active)
			VALUES (#{sessionId}, #{userId}, #{token}, #{refreshToken},
				#{deviceType}, #{deviceInfo}, #{ipAddress}, NOW(), #{expiresAt}, NOW(), 1)
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(UserSession session);

	@Select("SELECT * FROM sys_user_session WHERE session_id = #{sessionId} AND is_active = 1")
	UserSession findBySessionId(String sessionId);

	@Select("SELECT * FROM sys_user_session WHERE refresh_token = #{refreshToken} AND is_active = 1")
	UserSession findByRefreshToken(String refreshToken);

	@Update("""
			UPDATE sys_user_session SET is_active = 0, logout_time = NOW(), logout_type = #{logoutType}
			WHERE session_id = #{sessionId}
			""")
	int deactivateSession(@Param("sessionId") String sessionId, @Param("logoutType") Integer logoutType);

	@Update("""
			UPDATE sys_user_session SET is_active = 0, logout_time = NOW(), logout_type = 3
			WHERE user_id = #{userId} AND is_active = 1
			""")
	int deactivateAllUserSessions(Long userId);

	@Update("UPDATE sys_user_session SET last_activity_time = NOW() WHERE session_id = #{sessionId}")
	int updateLastActivity(String sessionId);

}
