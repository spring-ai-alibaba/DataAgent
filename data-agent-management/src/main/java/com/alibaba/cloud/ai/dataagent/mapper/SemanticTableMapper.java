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

import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticTable;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SemanticTableMapper {

	@Select("""
			SELECT * FROM semantic_table
			WHERE agent_id = #{agentId}
			  AND datasource_id = #{datasourceId}
			  AND status = 1
			ORDER BY updated_time DESC, created_time DESC
			""")
	List<SemanticTable> listActiveByAgentIdAndDatasourceId(@Param("agentId") Long agentId,
			@Param("datasourceId") Integer datasourceId);

	@Select("""
			SELECT * FROM semantic_table
			WHERE agent_id = #{agentId}
			  AND datasource_id = #{datasourceId}
			ORDER BY updated_time DESC, created_time DESC
			""")
	List<SemanticTable> listByAgentIdAndDatasourceId(@Param("agentId") Long agentId,
			@Param("datasourceId") Integer datasourceId);

	@Select("""
			SELECT * FROM semantic_table
			WHERE agent_id = #{agentId}
			  AND datasource_id = #{datasourceId}
			  AND LOWER(table_name) = LOWER(#{tableName})
			LIMIT 1
			""")
	SemanticTable selectByAgentIdAndDatasourceIdAndTableName(@Param("agentId") Long agentId,
			@Param("datasourceId") Integer datasourceId, @Param("tableName") String tableName);

	@Select("""
			SELECT * FROM semantic_table
			WHERE id = #{id}
			LIMIT 1
			""")
	SemanticTable selectById(@Param("id") Long id);

	@Insert("""
			INSERT INTO semantic_table
			(agent_id, datasource_id, table_name, business_name, synonyms, business_description, table_comment,
			 is_visible, status, created_time, updated_time)
			VALUES
			(#{agentId}, #{datasourceId}, #{tableName}, #{businessName}, #{synonyms}, #{businessDescription},
			 #{tableComment}, #{isVisible}, #{status}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SemanticTable semanticTable);

	@Update("""
			<script>
			UPDATE semantic_table
			<set>
				<if test="tableName != null">table_name = #{tableName},</if>
				<if test="businessName != null">business_name = #{businessName},</if>
				<if test="synonyms != null">synonyms = #{synonyms},</if>
				<if test="businessDescription != null">business_description = #{businessDescription},</if>
				<if test="tableComment != null">table_comment = #{tableComment},</if>
				<if test="isVisible != null">is_visible = #{isVisible},</if>
				<if test="status != null">status = #{status},</if>
				updated_time = CURRENT_TIMESTAMP
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(SemanticTable semanticTable);

	@Delete("""
			DELETE FROM semantic_table
			WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id);

	@Delete("""
			DELETE FROM semantic_table
			WHERE datasource_id = #{datasourceId}
			""")
	int deleteByDatasourceId(@Param("datasourceId") Integer datasourceId);

}
