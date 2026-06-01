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

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserRoleMapper {

	@Insert("""
			INSERT IGNORE INTO sys_user_role (user_id, role_id, created_time)
			VALUES (#{userId}, #{roleId}, NOW())
			""")
	int insert(@Param("userId") Long userId, @Param("roleId") Long roleId);

	@Select("SELECT id FROM sys_role WHERE role_code = #{roleCode} AND status = 1 AND is_deleted = 0")
	Long findRoleIdByCode(String roleCode);

}
