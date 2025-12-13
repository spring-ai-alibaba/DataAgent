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

package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.dto.knowledge.agentknowledge.AgentKnowledgeQueryDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentKnowledgeMapper {

	@Select("""
			SELECT * FROM agent_knowledge WHERE id = #{id} AND is_deleted = 0
			""")
	AgentKnowledge selectById(@Param("id") Integer id);

	@Select("""
			    SELECT * FROM agent_knowledge WHERE id = #{id}
			""")
	AgentKnowledge selectByIdIncludeDeleted(@Param("id") Integer id);

	@Insert("""

			INSERT INTO agent_knowledge (agent_id, title, content, type, question, is_recall, embedding_status, source_filename, file_path, file_size, file_type, is_deleted, is_resource_cleaned, created_time, updated_time)
			VALUES (#{agentId}, #{title}, #{content}, #{type}, #{question}, #{isRecall}, #{embeddingStatus}, #{sourceFilename}, #{filePath}, #{fileSize}, #{fileType}, #{isDeleted}, #{isResourceCleaned}, #{createdTime}, #{updatedTime})

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
				<if test="question != null">question = #{question},</if>
				<if test="isRecall != null">is_recall = #{isRecall},</if>
				<if test="embeddingStatus != null">embedding_status = #{embeddingStatus},</if>
				<if test="errorMsg != null">error_msg = #{errorMsg},</if>
				<if test="sourceFilename != null">source_filename = #{sourceFilename},</if>
				<if test="filePath != null">file_path = #{filePath},</if>
				<if test="fileSize != null">file_size = #{fileSize},</if>
				<if test="fileType != null">file_type = #{fileType},</if>
				<if test="isDeleted != null">is_deleted = #{isDeleted},</if>
				<if test="isResourceCleaned != null">is_resource_cleaned = #{isResourceCleaned},</if>
				updated_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int update(AgentKnowledge knowledge);

	@Select("""
			<script>
			SELECT * FROM agent_knowledge
			WHERE agent_id = #{queryDTO.agentId}
			<if test="queryDTO.title != null and queryDTO.title != ''">
				AND title LIKE CONCAT('%', #{queryDTO.title}, '%')
			</if>
			<if test="queryDTO.type != null and queryDTO.type != ''">
				AND type = #{queryDTO.type}
			</if>
			<if test="queryDTO.embeddingStatus != null and queryDTO.embeddingStatus != ''">
				AND embedding_status = #{queryDTO.embeddingStatus}
			</if>
			AND is_deleted = 0
			LIMIT #{offset}, #{queryDTO.pageSize}
			</script>
			""")
	List<AgentKnowledge> selectByConditionsWithPage(@Param("queryDTO") AgentKnowledgeQueryDTO queryDTO,
			@Param("offset") Integer offset);

	@Select("""
			<script>
			SELECT COUNT(*) FROM agent_knowledge
			WHERE agent_id = #{queryDTO.agentId}
			<if test="queryDTO.title != null and queryDTO.title != ''">
				AND title LIKE CONCAT('%', #{queryDTO.title}, '%')
			</if>
			<if test="queryDTO.type != null and queryDTO.type != ''">
				AND type = #{queryDTO.type}
			</if>
			<if test="queryDTO.embeddingStatus != null and queryDTO.embeddingStatus != ''">
				AND embedding_status = #{queryDTO.embeddingStatus}
			</if>
			AND is_deleted = 0
			</script>
			""")
	Long countByConditions(@Param("queryDTO") AgentKnowledgeQueryDTO queryDTO);

	@Select("""
			SELECT id FROM agent_knowledge WHERE agent_id = #{agentId} AND is_recall = 1 AND is_deleted = 0
			""")
	List<Integer> selectRecalledKnowledgeIds(@Param("agentId") Integer agentId);

	/**
	 * 查询待清理的“僵尸”记录 条件：is_deleted = 1 AND is_resource_cleaned = 0 AND updated_time <(当前时间
	 * - N分钟)
	 */
	@Select("""
			    SELECT * FROM agent_knowledge
			    WHERE is_deleted = 1
			      AND is_resource_cleaned = 0
			      AND updated_time < #{beforeTime}
			    LIMIT #{limit}
			""")
	List<AgentKnowledge> selectDirtyRecords(@Param("beforeTime") LocalDateTime beforeTime, @Param("limit") int limit);

}
