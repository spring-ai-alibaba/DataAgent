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

import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticRelation;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SemanticRelationMapper {

	@Select("""
			SELECT * FROM semantic_relation
			WHERE agent_id = #{agentId}
			  AND datasource_id = #{datasourceId}
			  AND status = 1
			ORDER BY updated_time DESC, created_time DESC
			""")
	List<SemanticRelation> listActiveByAgentIdAndDatasourceId(@Param("agentId") Long agentId,
			@Param("datasourceId") Integer datasourceId);

	@Select("""
			SELECT * FROM semantic_relation
			WHERE agent_id = #{agentId}
			  AND datasource_id = #{datasourceId}
			ORDER BY updated_time DESC, created_time DESC
			""")
	List<SemanticRelation> listByAgentIdAndDatasourceId(@Param("agentId") Long agentId,
			@Param("datasourceId") Integer datasourceId);

	@Select("""
			SELECT * FROM semantic_relation
			WHERE id = #{id}
			LIMIT 1
			""")
	SemanticRelation selectById(@Param("id") Long id);

	@Insert("""
			INSERT INTO semantic_relation
			(agent_id, datasource_id, source_table_name, source_column_names, target_table_name, target_column_names,
			 relation_type, description, status, created_time, updated_time)
			VALUES
			(#{agentId}, #{datasourceId}, #{sourceTableName}, #{sourceColumnNames}, #{targetTableName},
			 #{targetColumnNames}, #{relationType}, #{description}, #{status}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SemanticRelation relation);

	@Update("""
			<script>
			UPDATE semantic_relation
			<set>
				<if test="sourceTableName != null">source_table_name = #{sourceTableName},</if>
				<if test="sourceColumnNames != null">source_column_names = #{sourceColumnNames},</if>
				<if test="targetTableName != null">target_table_name = #{targetTableName},</if>
				<if test="targetColumnNames != null">target_column_names = #{targetColumnNames},</if>
				<if test="relationType != null">relation_type = #{relationType},</if>
				<if test="description != null">description = #{description},</if>
				<if test="status != null">status = #{status},</if>
				updated_time = CURRENT_TIMESTAMP
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(SemanticRelation relation);

	@Delete("""
			DELETE FROM semantic_relation
			WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id);

	@Delete("""
			DELETE FROM semantic_relation
			WHERE datasource_id = #{datasourceId}
			""")
	int deleteByDatasourceId(@Param("datasourceId") Integer datasourceId);

}
