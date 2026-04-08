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
package com.alibaba.cloud.ai.dataagent.management.mapper;

import com.alibaba.cloud.ai.dataagent.management.entity.UserPromptConfig;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserPromptConfigMapper {

	@Select("""
			<script>
			SELECT * FROM user_prompt_config
			WHERE agent_type = #{agentType}
			  AND prompt_type = #{promptType}
			<if test='agentId != null'> AND agent_id = #{agentId}</if>
			ORDER BY priority DESC, display_order ASC, update_time DESC
			</script>
			""")
	List<UserPromptConfig> selectByPromptType(@Param("agentType") String agentType,
			@Param("promptType") String promptType, @Param("agentId") Long agentId);

	@Select("""
			<script>
			SELECT * FROM user_prompt_config
			WHERE agent_type = #{agentType}
			  AND prompt_type = #{promptType}
			  AND enabled = 1
			<if test='agentId != null'> AND agent_id = #{agentId}</if>
			ORDER BY priority DESC, display_order ASC, update_time DESC
			LIMIT 1
			</script>
			""")
	UserPromptConfig selectActiveByPromptType(@Param("agentType") String agentType,
			@Param("promptType") String promptType, @Param("agentId") Long agentId);

	@Update("""
			<script>
			UPDATE user_prompt_config
			SET enabled = 0
			WHERE agent_type = #{agentType}
			  AND prompt_type = #{promptType}
			<if test='agentId != null'> AND agent_id = #{agentId}</if>
			</script>
			""")
	int disableAllByPromptType(@Param("agentType") String agentType,
			@Param("promptType") String promptType, @Param("agentId") Long agentId);

	@Update("UPDATE user_prompt_config SET enabled = 1 WHERE id = #{id}")
	int enableById(@Param("id") String id);

	@Update("UPDATE user_prompt_config SET enabled = 0 WHERE id = #{id}")
	int disableById(@Param("id") String id);

	@Select("SELECT * FROM user_prompt_config WHERE id = #{id}")
	UserPromptConfig selectById(String id);

	@Update("""
			<script>
			UPDATE user_prompt_config
			<set>
			  <if test='name != null'>name = #{name},</if>
			  <if test='agentType != null'>agent_type = #{agentType},</if>
			  <if test='promptType != null'>prompt_type = #{promptType},</if>
			  agent_id = #{agentId},
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
			(id, name, agent_type, prompt_type, agent_id, system_prompt, enabled, description, priority, display_order, create_time, update_time, creator)
			VALUES (#{id}, #{name}, #{agentType}, #{promptType}, #{agentId}, #{systemPrompt}, #{enabled}, #{description}, #{priority}, #{displayOrder}, NOW(), NOW(), #{creator})
			""")
	int insert(UserPromptConfig config);

	@Select("""
			<script>
			SELECT * FROM user_prompt_config
			WHERE agent_type = #{agentType}
			  AND prompt_type = #{promptType}
			  AND enabled = true
			<if test='agentId != null'> AND agent_id = #{agentId}</if>
			ORDER BY priority DESC, display_order ASC, update_time DESC
			</script>
			""")
	List<UserPromptConfig> getActiveConfigs(@Param("agentType") String agentType,
			@Param("promptType") String promptType, @Param("agentId") Long agentId);

	@Select("""
			<script>
			SELECT * FROM user_prompt_config
			WHERE agent_type = #{agentType}
			  AND prompt_type = #{promptType}
			<if test='agentId != null'> AND agent_id = #{agentId}</if>
			ORDER BY priority DESC, display_order ASC, update_time DESC
			</script>
			""")
	List<UserPromptConfig> getConfigs(@Param("agentType") String agentType,
			@Param("promptType") String promptType, @Param("agentId") Long agentId);

	@Select("SELECT * FROM user_prompt_config ORDER BY priority DESC, display_order, update_time DESC")
	List<UserPromptConfig> selectAll();

	@Delete("DELETE FROM user_prompt_config WHERE id = #{id}")
	int deleteById(String id);

}
