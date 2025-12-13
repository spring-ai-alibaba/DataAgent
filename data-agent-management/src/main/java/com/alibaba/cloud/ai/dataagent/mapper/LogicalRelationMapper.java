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

import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 逻辑外键配置 Mapper 接口
 */
@Mapper
public interface LogicalRelationMapper {

	/**
	 * 根据ID查询逻辑外键
	 */
	@Select("SELECT * FROM logical_relation WHERE id = #{id} AND is_deleted = 0")
	LogicalRelation selectById(@Param("id") Integer id);

	/**
	 * 根据数据源ID查询逻辑外键列表（未删除的）
	 */
	@Select("SELECT * FROM logical_relation WHERE datasource_id = #{datasourceId} AND is_deleted = 0 ORDER BY created_time DESC")
	List<LogicalRelation> selectByDatasourceId(@Param("datasourceId") Integer datasourceId);

	/**
	 * 插入逻辑外键
	 */
	@Insert("""
			INSERT INTO logical_relation
			    (datasource_id, source_table_name, source_column_name, target_table_name, target_column_name,
			     relation_type, description, is_deleted, created_time, updated_time)
			VALUES (#{datasourceId}, #{sourceTableName}, #{sourceColumnName}, #{targetTableName}, #{targetColumnName},
			        #{relationType}, #{description}, 0, NOW(), NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(LogicalRelation logicalRelation);

	/**
	 * 更新逻辑外键
	 */
	@Update("""
			<script>
			UPDATE logical_relation
			<set>
			    <if test="sourceTableName != null">source_table_name = #{sourceTableName},</if>
			    <if test="sourceColumnName != null">source_column_name = #{sourceColumnName},</if>
			    <if test="targetTableName != null">target_table_name = #{targetTableName},</if>
			    <if test="targetColumnName != null">target_column_name = #{targetColumnName},</if>
			    <if test="relationType != null">relation_type = #{relationType},</if>
			    <if test="description != null">description = #{description},</if>
			    updated_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(LogicalRelation logicalRelation);

	/**
	 * 逻辑删除外键
	 */
	@Update("UPDATE logical_relation SET is_deleted = 1, updated_time = NOW() WHERE id = #{id}")
	int deleteById(@Param("id") Integer id);

	/**
	 * 逻辑删除数据源下的所有逻辑外键
	 */
	@Update("UPDATE logical_relation SET is_deleted = 1, updated_time = NOW() WHERE datasource_id = #{datasourceId}")
	int deleteByDatasourceId(@Param("datasourceId") Integer datasourceId);

	/**
	 * 检查逻辑外键是否存在
	 */
	@Select("""
			SELECT COUNT(*) FROM logical_relation
			WHERE datasource_id = #{datasourceId}
			  AND source_table_name = #{sourceTableName}
			  AND source_column_name = #{sourceColumnName}
			  AND target_table_name = #{targetTableName}
			  AND target_column_name = #{targetColumnName}
			  AND is_deleted = 0
			""")
	int checkExists(@Param("datasourceId") Integer datasourceId, @Param("sourceTableName") String sourceTableName,
			@Param("sourceColumnName") String sourceColumnName, @Param("targetTableName") String targetTableName,
			@Param("targetColumnName") String targetColumnName);

}
