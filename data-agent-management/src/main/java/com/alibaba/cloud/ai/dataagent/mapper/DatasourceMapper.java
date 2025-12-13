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

import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;
import java.util.Map;

/**
 * Data Source Mapper Interface
 *
 * @author Alibaba Cloud AI
 */
@Mapper
public interface DatasourceMapper {

	@Select("SELECT * FROM datasource WHERE id = #{id}")
	Datasource selectById(@Param("id") Integer id);

	@Select("SELECT * FROM datasource ORDER BY create_time DESC")
	List<Datasource> selectAll();

	@Insert("""
			INSERT INTO datasource
			    (name, type, host, port, database_name, username, password, connection_url, status, test_status, description, creator_id, create_time, update_time)
			VALUES (#{name}, #{type}, #{host}, #{port}, #{databaseName}, #{username}, #{password}, #{connectionUrl}, #{status}, #{testStatus}, #{description}, #{creatorId}, NOW(), NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Datasource datasource);

	/**
	 * Update data source by id, only update non-null fields
	 */
	@Update("""
			<script>
			UPDATE datasource
			<set>
			    <if test="name != null">name = #{name},</if>
			    <if test="type != null">type = #{type},</if>
			    <if test="host != null">host = #{host},</if>
			    <if test="port != null">port = #{port},</if>
			    <if test="databaseName != null">database_name = #{databaseName},</if>
			    <if test="username != null">username = #{username},</if>
			    <if test="password != null">password = #{password},</if>
			    <if test="connectionUrl != null">connection_url = #{connectionUrl},</if>
			    <if test="status != null">status = #{status},</if>
			    <if test="testStatus != null">test_status = #{testStatus},</if>
			    <if test="description != null">description = #{description},</if>
			    <if test="creatorId != null">creator_id = #{creatorId},</if>
			    update_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(Datasource datasource);

	@Update("UPDATE datasource SET test_status = #{testStatus} WHERE id = #{id}")
	int updateTestStatusById(@Param("id") Integer id, @Param("testStatus") String testStatus);

	/**
	 * Query data source list by status
	 */
	@Select("SELECT * FROM datasource WHERE status = #{status} ORDER BY create_time DESC")
	List<Datasource> selectByStatus(@Param("status") String status);

	/**
	 * Query data source list by type
	 */
	@Select("SELECT * FROM datasource WHERE type = #{type} ORDER BY create_time DESC")
	List<Datasource> selectByType(@Param("type") String type);

	/**
	 * Get data source statistics - by status
	 */
	@Select("SELECT status, COUNT(*) as count FROM datasource GROUP BY status")
	List<Map<String, Object>> selectStatusStats();

	/**
	 * Get data source statistics - by type
	 */
	@Select("SELECT type, COUNT(*) as count FROM datasource GROUP BY type")
	List<Map<String, Object>> selectTypeStats();

	/**
	 * Get data source statistics - by test status
	 */
	@Select("SELECT test_status, COUNT(*) as count FROM datasource GROUP BY test_status")
	List<Map<String, Object>> selectTestStatusStats();

	@Select("SELECT COUNT(*) FROM datasource")
	Long selectCount();

	@Delete("DELETE FROM datasource WHERE id = #{id}")
	int deleteById(Integer id);

}
