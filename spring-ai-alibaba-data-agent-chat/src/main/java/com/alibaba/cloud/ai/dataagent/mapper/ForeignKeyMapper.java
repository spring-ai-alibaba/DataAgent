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

import com.alibaba.cloud.ai.dataagent.entity.ForeignKey;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 逻辑外键 Mapper 接口
 */
@Mapper
public interface ForeignKeyMapper {

	/**
	 * 根据ID查询逻辑外键
	 */
	@Select("SELECT * FROM foreign_key WHERE id = #{id}")
	ForeignKey selectById(@Param("id") Integer id);

	/**
	 * 根据数据源ID查询逻辑外键列表
	 */
	@Select("SELECT * FROM foreign_key WHERE datasource_id = #{datasourceId} ORDER BY create_time DESC")
	List<ForeignKey> selectByDatasourceId(@Param("datasourceId") Integer datasourceId);

	/**
	 * 插入逻辑外键
	 */
	@Insert("""
			INSERT INTO foreign_key
			    (datasource_id, source_table, source_column, target_table, target_column, description, create_time, update_time)
			VALUES (#{datasourceId}, #{sourceTable}, #{sourceColumn}, #{targetTable}, #{targetColumn}, #{description}, NOW(), NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(ForeignKey foreignKey);

	/**
	 * 更新逻辑外键
	 */
	@Update("""
			<script>
			UPDATE foreign_key
			<set>
			    <if test="sourceTable != null">source_table = #{sourceTable},</if>
			    <if test="sourceColumn != null">source_column = #{sourceColumn},</if>
			    <if test="targetTable != null">target_table = #{targetTable},</if>
			    <if test="targetColumn != null">target_column = #{targetColumn},</if>
			    <if test="description != null">description = #{description},</if>
			    update_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(ForeignKey foreignKey);

	/**
	 * 删除逻辑外键
	 */
	@Delete("DELETE FROM foreign_key WHERE id = #{id}")
	int deleteById(@Param("id") Integer id);

	/**
	 * 删除数据源下的所有逻辑外键
	 */
	@Delete("DELETE FROM foreign_key WHERE datasource_id = #{datasourceId}")
	int deleteByDatasourceId(@Param("datasourceId") Integer datasourceId);

	/**
	 * 检查逻辑外键是否存在（用于去重）
	 */
	@Select("""
			SELECT COUNT(*) FROM foreign_key
			WHERE datasource_id = #{datasourceId}
			  AND source_table = #{sourceTable}
			  AND source_column = #{sourceColumn}
			  AND target_table = #{targetTable}
			  AND target_column = #{targetColumn}
			""")
	int checkExists(@Param("datasourceId") Integer datasourceId, @Param("sourceTable") String sourceTable,
			@Param("sourceColumn") String sourceColumn, @Param("targetTable") String targetTable,
			@Param("targetColumn") String targetColumn);

}
