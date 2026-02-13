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

import com.alibaba.cloud.ai.dataagent.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {

	@Select("""
			SELECT * FROM sys_user WHERE is_deleted = 0 ORDER BY created_time DESC
			""")
	List<User> findAll();

	@Select("""
			SELECT * FROM sys_user WHERE id = #{id} AND is_deleted = 0
			""")
	User findById(Long id);

	@Select("""
			SELECT * FROM sys_user WHERE username = #{username} AND is_deleted = 0
			""")
	User findByUsername(String username);

	@Select("""
			SELECT * FROM sys_user WHERE email = #{email} AND is_deleted = 0
			""")
	User findByEmail(String email);

	@Select("""
			<script>
				SELECT * FROM sys_user
				<where>
					is_deleted = 0
					<if test='status != null'>
						AND status = #{status}
					</if>
					<if test='userType != null'>
						AND user_type = #{userType}
					</if>
					<if test='keyword != null and keyword != ""'>
						AND (username LIKE CONCAT('%', #{keyword}, '%')
							 OR real_name LIKE CONCAT('%', #{keyword}, '%')
							 OR email LIKE CONCAT('%', #{keyword}, '%'))
					</if>
				</where>
				ORDER BY created_time DESC
			</script>
			""")
	List<User> findByConditions(@Param("status") Integer status,
								 @Param("userType") Integer userType,
								 @Param("keyword") String keyword);

	@Insert("""
			INSERT INTO sys_user (username, password, email, phone, real_name, avatar, status, user_type,
								  created_time, updated_time, created_by)
			VALUES (#{username}, #{password}, #{email}, #{phone}, #{realName}, #{avatar}, #{status}, #{userType},
					NOW(), NOW(), #{createdBy})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(User user);

	@Update("""
			<script>
				UPDATE sys_user
				<trim prefix="SET" suffixOverrides=",">
					<if test='email != null'>email = #{email},</if>
					<if test='phone != null'>phone = #{phone},</if>
					<if test='realName != null'>real_name = #{realName},</if>
					<if test='avatar != null'>avatar = #{avatar},</if>
					<if test='status != null'>status = #{status},</if>
					<if test='userType != null'>user_type = #{userType},</if>
					<if test='updatedBy != null'>updated_by = #{updatedBy},</if>
					updated_time = NOW()
				</trim>
				WHERE id = #{id}
			</script>
			""")
	int updateById(User user);

	@Update("""
			UPDATE sys_user
			SET password = #{password}, password_changed_time = NOW(), updated_time = NOW()
			WHERE id = #{id}
			""")
	int updatePassword(@Param("id") Long id, @Param("password") String password);

	@Update("""
			UPDATE sys_user
			SET failed_login_count = #{count}, updated_time = NOW()
			WHERE id = #{id}
			""")
	int updateFailedLoginCount(@Param("id") Long id, @Param("count") Integer count);

	@Update("""
			UPDATE sys_user
			SET locked_until = #{lockedUntil}, updated_time = NOW()
			WHERE id = #{id}
			""")
	int updateLockedUntil(@Param("id") Long id, @Param("lockedUntil") java.time.LocalDateTime lockedUntil);

	@Update("""
			UPDATE sys_user
			SET last_login_time = #{lastLoginTime}, last_login_ip = #{lastLoginIp},
				login_count = login_count + 1, failed_login_count = 0, updated_time = NOW()
			WHERE id = #{id}
			""")
	int updateLoginInfo(@Param("id") Long id,
						@Param("lastLoginTime") java.time.LocalDateTime lastLoginTime,
						@Param("lastLoginIp") String lastLoginIp);

	@Update("""
			UPDATE sys_user
			SET is_deleted = 1, updated_time = NOW(), updated_by = #{updatedBy}
			WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id, @Param("updatedBy") Long updatedBy);

	@Delete("""
			DELETE FROM sys_user WHERE id = #{id}
			""")
	int hardDeleteById(Long id);

}
