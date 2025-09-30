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

package com.alibaba.cloud.ai.mapper;

import com.alibaba.cloud.ai.entity.UserPromptConfig;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserPromptConfigMapper {

	/**
	 * Query configuration list by prompt type
	 */
	@Select("SELECT * FROM user_prompt_config WHERE prompt_type = #{promptType} ORDER BY update_time DESC")
	List<UserPromptConfig> selectByPromptType(@Param("promptType") String promptType);

	/**
	 * Query enabled configuration by prompt type
	 */
	@Select("SELECT * FROM user_prompt_config WHERE prompt_type = #{promptType} AND enabled = 1 LIMIT 1")
	UserPromptConfig selectActiveByPromptType(@Param("promptType") String promptType);

	/**
	 * Disable all configurations of a specified type
	 */
	@Update("UPDATE user_prompt_config SET enabled = 0 WHERE prompt_type = #{promptType}")
	int disableAllByPromptType(@Param("promptType") String promptType);

	/**
	 * Enable a specified configuration
	 */
	@Update("UPDATE user_prompt_config SET enabled = 1 WHERE id = #{id}")
	int enableById(@Param("id") String id);

	/**
	 * Disable a specified configuration
	 */
	@Update("UPDATE user_prompt_config SET enabled = 0 WHERE id = #{id}")
	int disableById(@Param("id") String id);

	@Select("SELECT * FROM user_prompt_config WHERE id = #{id}")
	UserPromptConfig selectById(String id);

	@Update("""
			<script>
			UPDATE user_prompt_config
			<set>
			  <if test='name != null'>name = #{name},</if>
			  <if test='promptType != null'>prompt_type = #{promptType},</if>
			  <if test='systemPrompt != null'>system_prompt = #{systemPrompt},</if>
			  <if test='enabled != null'>enabled = #{enabled},</if>
			  <if test='description != null'>description = #{description},</if>
			  <if test='priority != null'>priority = #{priority},</if>
			  <if test='displayOrder != null'>display_order = #{displayOrder},</if>
			  update_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(UserPromptConfig config);

	@Insert("""
			INSERT INTO user_prompt_config
			(id, name, prompt_type, system_prompt, enabled, description, priority, display_order, create_time, update_time, creator)
			VALUES (#{id}, #{name}, #{promptType}, #{systemPrompt}, #{enabled}, #{description}, #{priority}, #{displayOrder}, NOW(), NOW(), #{creator})
			""")
	int insert(UserPromptConfig config);

	@Select("""
			SELECT * FROM user_prompt_config WHERE prompt_type = #{promptType} AND enabled = true
			ORDER BY priority DESC, display_order, update_time DESC
			""")
	List<UserPromptConfig> getActiveConfigsByType(String promptType);

	@Select("""
			SELECT * FROM user_prompt_config WHERE prompt_type = #{promptType}
			ORDER BY priority DESC, display_order, update_time DESC
			""")
	List<UserPromptConfig> getConfigsByType(String promptType);

	@Select("SELECT * FROM user_prompt_config ORDER BY priority DESC, display_order, update_time DESC")
	List<UserPromptConfig> selectAll();

	@Delete("DELETE FROM user_prompt_config WHERE id = #{id}")
	int deleteById(String id);

}
