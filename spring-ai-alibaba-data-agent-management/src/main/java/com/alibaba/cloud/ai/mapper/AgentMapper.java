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

import com.alibaba.cloud.ai.entity.Agent;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AgentMapper {

	@Select("""
			SELECT * FROM agent ORDER BY create_time DESC
			""")
	List<Agent> findAll();

	@Select("""
			SELECT * FROM agent WHERE id = #{id}
			""")
	Agent findById(Long id);

	@Select("""
			SELECT * FROM agent WHERE status = #{status} ORDER BY create_time DESC
			""")
	List<Agent> findByStatus(String status);

	@Select("""
			SELECT * FROM agent
			WHERE (name LIKE CONCAT('%', #{keyword}, '%')
				   OR description LIKE CONCAT('%', #{keyword}, '%')
				   OR tags LIKE CONCAT('%', #{keyword}, '%'))
			ORDER BY create_time DESC
			""")
	List<Agent> searchByKeyword(@Param("keyword") String keyword);

	@Insert("""
			INSERT INTO agent (name, description, avatar, status, prompt, category, admin_id, tags, create_time, update_time, human_review_enabled)
			VALUES (#{name}, #{description}, #{avatar}, #{status}, #{prompt}, #{category}, #{adminId}, #{tags}, #{createTime}, #{updateTime}, #{humanReviewEnabled})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Agent agent);

	@Update("""
			<script>
			          UPDATE agent
			          <trim prefix="SET" suffixOverrides=",">
			            <if test='name != null'>name = #{name},</if>
			            <if test='description != null'>description = #{description},</if>
			            <if test='avatar != null'>avatar = #{avatar},</if>
			            <if test='status != null'>status = #{status},</if>
			            <if test='prompt != null'>prompt = #{prompt},</if>
			            <if test='category != null'>category = #{category},</if>
			            <if test='adminId != null'>admin_id = #{adminId},</if>
			            <if test='tags != null'>tags = #{tags},</if>
			            <if test='humanReviewEnabled != null'>human_review_enabled = #{humanReviewEnabled},</if>
			            update_time = NOW()
			          </trim>
			          WHERE id = #{id}
			</script>
			""")
	int updateById(Agent agent);

	@Delete("""
			DELETE FROM agent WHERE id = #{id}
			""")
	int deleteById(Long id);

}
