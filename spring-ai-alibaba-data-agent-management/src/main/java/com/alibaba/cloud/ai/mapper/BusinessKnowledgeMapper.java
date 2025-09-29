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

import com.alibaba.cloud.ai.entity.BusinessKnowledge;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface BusinessKnowledgeMapper {

	/**
	 * Query business knowledge list by dataset ID
	 */
	@Select("""
			SELECT * FROM business_knowledge
			WHERE data_set_id = #{datasetId}
			ORDER BY created_time DESC
			""")
	List<BusinessKnowledge> selectByDatasetId(@Param("datasetId") String datasetId);

	/**
	 * Query by primary key id
	 */
	@Select("""
			SELECT * FROM business_knowledge
			WHERE id = #{id}
			""")
	BusinessKnowledge selectById(@Param("id") Long id);

	/**
	 * Get all dataset ID list
	 */
	@Select("""
			SELECT DISTINCT data_set_id
			FROM business_knowledge
			WHERE data_set_id IS NOT NULL
			ORDER BY data_set_id
			""")
	List<String> selectDistinctDatasetIds();

	/**
	 * Search business knowledge by keyword
	 */
	@Select("""
			SELECT * FROM business_knowledge
			WHERE business_term LIKE CONCAT('%', #{keyword}, '%')
			   OR description LIKE CONCAT('%', #{keyword}, '%')
			   OR synonyms LIKE CONCAT('%', #{keyword}, '%')
			ORDER BY created_time DESC
			""")
	List<BusinessKnowledge> searchByKeyword(@Param("keyword") String keyword);

	/**
	 * Query business knowledge list by agent ID
	 */
	@Select("""
			SELECT * FROM business_knowledge
			WHERE agent_id = #{agentId}
			ORDER BY created_time DESC
			""")
	List<BusinessKnowledge> selectByAgentId(@Param("agentId") String agentId);

	/**
	 * Query business knowledge by dataset ID and default recall status
	 */
	@Select("""
			SELECT * FROM business_knowledge
			WHERE data_set_id = #{datasetId}
			  AND is_recall = #{defaultRecall}
			ORDER BY created_time DESC
			""")
	List<BusinessKnowledge> selectByDatasetIdAndDefaultRecall(@Param("datasetId") String datasetId,
			@Param("defaultRecall") Boolean defaultRecall);

	@Insert("""
			INSERT INTO business_knowledge (business_term, description, synonyms, is_recall, data_set_id, agent_id, created_time, updated_time)
			VALUES (#{businessTerm}, #{description}, #{synonyms}, #{defaultRecall}, #{datasetId}, #{agentId}, NOW(), NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(BusinessKnowledge knowledge);

	@Update("""
			<script>
			UPDATE business_knowledge
			<set>
				<if test="businessTerm != null">business_term = #{businessTerm},</if>
				<if test="description != null">description = #{description},</if>
				<if test="synonyms != null">synonyms = #{synonyms},</if>
				<if test="defaultRecall != null">is_recall = #{defaultRecall},</if>
				<if test="datasetId != null">data_set_id = #{datasetId},</if>
				<if test="agentId != null">agent_id = #{agentId},</if>
				updated_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(BusinessKnowledge knowledge);

	@Delete("""
			DELETE FROM business_knowledge
			WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id);

	/**
	 * Delete all business knowledge by agent ID
	 */
	@Delete("""
			DELETE FROM business_knowledge
			WHERE agent_id = #{agentId}
			""")
	int deleteByAgentId(@Param("agentId") String agentId);

	/**
	 * Search in a specific agent scope by keyword
	 */
	@Select("""
			SELECT * FROM business_knowledge
			WHERE agent_id = #{agentId}
			  AND (business_term LIKE CONCAT('%', #{keyword}, '%')
			    OR description LIKE CONCAT('%', #{keyword}, '%')
			    OR synonyms LIKE CONCAT('%', #{keyword}, '%'))
			ORDER BY created_time DESC
			""")
	List<BusinessKnowledge> searchInAgent(@Param("agentId") String agentId, @Param("keyword") String keyword);

}
