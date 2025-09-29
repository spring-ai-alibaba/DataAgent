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

import com.alibaba.cloud.ai.entity.AgentKnowledge;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentKnowledgeMapper {

	@Select("""
			SELECT * FROM agent_knowledge WHERE agent_id = #{agentId} ORDER BY create_time DESC
			""")
	List<AgentKnowledge> selectByAgentId(@Param("agentId") Integer agentId);

	@Select("""
			SELECT * FROM agent_knowledge WHERE id = #{id}
			""")
	AgentKnowledge selectById(@Param("id") Integer id);

	@Insert("""
			INSERT INTO agent_knowledge (agent_id, title, content, type, category, tags, status,
			source_url, file_path, file_size, file_type, embedding_status, creator_id, create_time, update_time)
			VALUES (#{agentId}, #{title}, #{content}, #{type}, #{category}, #{tags}, #{status},
			#{sourceUrl}, #{filePath}, #{fileSize}, #{fileType}, #{embeddingStatus}, #{creatorId}, #{createTime}, #{updateTime})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(AgentKnowledge knowledge);

	@Update("""
			<script>
			UPDATE agent_knowledge
			<set>
				<if test="title != null">title = #{title},</if>
				<if test="content != null">content = #{content},</if>
				<if test="type != null">type = #{type},</if>
				<if test="category != null">category = #{category},</if>
				<if test="tags != null">tags = #{tags},</if>
				<if test="status != null">status = #{status},</if>
				<if test="sourceUrl != null">source_url = #{sourceUrl},</if>
				<if test="filePath != null">file_path = #{filePath},</if>
				<if test="fileSize != null">file_size = #{fileSize},</if>
				<if test="fileType != null">file_type = #{fileType},</if>
				<if test="embeddingStatus != null">embedding_status = #{embeddingStatus},</if>
				update_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int update(AgentKnowledge knowledge);

	@Delete("""
			DELETE FROM agent_knowledge WHERE id = #{id}
			""")
	int deleteById(@Param("id") Integer id);

	@Select("""
			SELECT * FROM agent_knowledge WHERE agent_id = #{agentId} AND type = #{type} ORDER BY create_time DESC
			""")
	List<AgentKnowledge> selectByAgentIdAndType(@Param("agentId") Integer agentId, @Param("type") String type);

	@Select("""
			SELECT * FROM agent_knowledge WHERE agent_id = #{agentId} AND status = #{status} ORDER BY create_time DESC
			""")
	List<AgentKnowledge> selectByAgentIdAndStatus(@Param("agentId") Integer agentId, @Param("status") String status);

	@Select("""
			SELECT * FROM agent_knowledge WHERE agent_id = #{agentId} AND
			(title LIKE CONCAT('%', #{keyword}, '%') OR content LIKE CONCAT('%', #{keyword}, '%') OR tags LIKE CONCAT('%', #{keyword}, '%'))
			ORDER BY create_time DESC
			""")
	List<AgentKnowledge> searchByAgentIdAndKeyword(@Param("agentId") Integer agentId, @Param("keyword") String keyword);

	@Update("""
			UPDATE agent_knowledge SET status = #{status}, update_time = #{now} WHERE id = #{id}
			""")
	int updateStatus(@Param("id") Integer id, @Param("status") String status, @Param("now") LocalDateTime now);

	@Select("""
			SELECT COUNT(*) FROM agent_knowledge WHERE agent_id = #{agentId}
			""")
	int countByAgentId(@Param("agentId") Integer agentId);

	@Select("""
			SELECT type, COUNT(*) as count FROM agent_knowledge WHERE agent_id = #{agentId} GROUP BY type
			""")
	List<Object[]> countByType(@Param("agentId") Integer agentId);

}
