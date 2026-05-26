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

import com.alibaba.cloud.ai.dataagent.entity.semantic.SemanticColumn;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SemanticColumnMapper {

	@Select("SELECT * FROM semantic_column ORDER BY created_time DESC")
	List<SemanticColumn> selectAll();

	@Select("""
			SELECT * FROM semantic_column
			WHERE agent_id = #{agentId}
			ORDER BY created_time DESC
			""")
	List<SemanticColumn> selectByAgentId(@Param("agentId") Long agentId);

	@Select("""
			SELECT * FROM semantic_column
			WHERE id = #{id}
			""")
	SemanticColumn selectById(@Param("id") Long id);

	@Select("""
			SELECT * FROM semantic_column
			WHERE (
			       column_name LIKE CONCAT('%', #{keyword}, '%')
			    OR table_name LIKE CONCAT('%', #{keyword}, '%')
			    OR business_name LIKE CONCAT('%', #{keyword}, '%')
			    OR business_description LIKE CONCAT('%', #{keyword}, '%')
			    OR synonyms LIKE CONCAT('%', #{keyword}, '%')
			    OR column_comment LIKE CONCAT('%', #{keyword}, '%')
			    OR data_type LIKE CONCAT('%', #{keyword}, '%')
			  )
			ORDER BY created_time DESC
			""")
	List<SemanticColumn> searchByKeyword(@Param("keyword") String keyword);

	@Select("""
			SELECT * FROM semantic_column
			WHERE agent_id = #{agentId}
			  AND (
			       column_name LIKE CONCAT('%', #{keyword}, '%')
			    OR table_name LIKE CONCAT('%', #{keyword}, '%')
			    OR business_name LIKE CONCAT('%', #{keyword}, '%')
			    OR business_description LIKE CONCAT('%', #{keyword}, '%')
			    OR synonyms LIKE CONCAT('%', #{keyword}, '%')
			    OR column_comment LIKE CONCAT('%', #{keyword}, '%')
			    OR data_type LIKE CONCAT('%', #{keyword}, '%')
			  )
			ORDER BY created_time DESC
			""")
	List<SemanticColumn> searchByKeywordAndAgentId(@Param("agentId") Long agentId,
			@Param("keyword") String keyword);

	@Select("""
			SELECT * FROM semantic_column
			WHERE agent_id = #{agentId}
			  AND status = 1
			ORDER BY created_time DESC
			""")
	List<SemanticColumn> selectActiveByAgentId(@Param("agentId") Long agentId);

	@Select("""
			SELECT * FROM semantic_column
			WHERE agent_id = #{agentId}
			  AND datasource_id = #{datasourceId}
			  AND status = 1
			ORDER BY created_time DESC
			""")
	List<SemanticColumn> selectActiveByAgentIdAndDatasourceId(@Param("agentId") Long agentId,
			@Param("datasourceId") Integer datasourceId);

	@Select("""
			<script>
			SELECT * FROM semantic_column
			WHERE agent_id = #{agentId}
			  AND status = 1
			  AND LOWER(table_name) IN
			  <foreach item='tableName' collection='tableNames' open='(' separator=',' close=')'>
			    #{tableName}
			  </foreach>
			ORDER BY created_time DESC
			</script>
			""")
	List<SemanticColumn> selectActiveByAgentIdAndTableNames(@Param("agentId") Long agentId,
			@Param("tableNames") List<String> tableNames);

	@Select("""
			<script>
			SELECT * FROM semantic_column
			WHERE agent_id = #{agentId}
			  AND datasource_id = #{datasourceId}
			  AND status = 1
			  AND LOWER(table_name) IN
			  <foreach item='tableName' collection='tableNames' open='(' separator=',' close=')'>
			    #{tableName}
			  </foreach>
			ORDER BY created_time DESC
			</script>
			""")
	List<SemanticColumn> selectActiveByAgentIdAndDatasourceIdAndTableNames(@Param("agentId") Long agentId,
			@Param("datasourceId") Integer datasourceId, @Param("tableNames") List<String> tableNames);

	@Select("""
			SELECT * FROM semantic_column
			WHERE agent_id = #{agentId}
			  AND datasource_id = #{datasourceId}
			  AND LOWER(table_name) = LOWER(#{tableName})
			ORDER BY created_time DESC
			""")
	List<SemanticColumn> selectByAgentIdAndDatasourceIdAndTableName(@Param("agentId") Long agentId,
			@Param("datasourceId") Integer datasourceId, @Param("tableName") String tableName);

	@Select("""
			SELECT * FROM semantic_column
			WHERE agent_id = #{agentId}
			  AND datasource_id = #{datasourceId}
			  AND LOWER(table_name) = LOWER(#{tableName})
			  AND LOWER(column_name) = LOWER(#{columnName})
			LIMIT 1
			""")
	SemanticColumn selectByAgentIdAndDatasourceIdAndTableNameAndColumnName(@Param("agentId") Long agentId,
			@Param("datasourceId") Integer datasourceId, @Param("tableName") String tableName,
			@Param("columnName") String columnName);

	@Insert("""
			INSERT INTO semantic_column
			(agent_id, datasource_id, table_name, column_name, business_name, synonyms, business_description,
			 column_comment, data_type, is_visible, status, created_time, updated_time)
			VALUES
			(#{agentId}, #{datasourceId}, #{tableName}, #{columnName}, #{businessName}, #{synonyms},
			 #{businessDescription}, #{columnComment}, #{dataType}, #{isVisible}, #{status},
			 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SemanticColumn model);

	@Update("""
			<script>
			UPDATE semantic_column
			<set>
				<if test="businessName != null">business_name = #{businessName},</if>
				<if test="synonyms != null">synonyms = #{synonyms},</if>
				<if test="businessDescription != null">business_description = #{businessDescription},</if>
				<if test="columnComment != null">column_comment = #{columnComment},</if>
				<if test="dataType != null">data_type = #{dataType},</if>
				<if test="isVisible != null">is_visible = #{isVisible},</if>
				<if test="status != null">status = #{status},</if>
				updated_time = CURRENT_TIMESTAMP
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(SemanticColumn model);

	@Update("""
			UPDATE semantic_column
			SET status = 1, updated_time = CURRENT_TIMESTAMP
			WHERE id = #{id}
			""")
	int enableById(@Param("id") Long id);

	@Update("""
			UPDATE semantic_column
			SET status = 0, updated_time = CURRENT_TIMESTAMP
			WHERE id = #{id}
			""")
	int disableById(@Param("id") Long id);

	@Delete("""
			DELETE FROM semantic_column
			WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id);

	@Delete("""
			DELETE FROM semantic_column
			WHERE datasource_id = #{datasourceId}
			""")
	int deleteByDatasourceId(@Param("datasourceId") Integer datasourceId);

}
