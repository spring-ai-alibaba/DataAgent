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

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentDatasourceTablesMapper {

	// 选择当前智能体数据源所有表
	@Select("select table_name from agent_datasource_tables where agent_datasource_id = #{agentDatasourceId}")
	List<String> getAgentDatasourceTables(@Param("agentDatasourceId") int agentDatasourceId);

	// 删除当前列表中不存在的表
	@Delete("<script>" + "DELETE FROM agent_datasource_tables WHERE agent_datasource_id = #{agentDatasourceId}"
			+ "<if test='tables != null and tables.size() > 0'>" + " AND table_name NOT IN ("
			+ "<foreach collection='tables' item='table' separator=','>#{table}</foreach>" + ")" + "</if>"
			+ "</script>")
	int removeExpireTables(@Param("agentDatasourceId") int agentDatasourceId, @Param("tables") List<String> tables);

	// 删除当前智能体数据源所有表
	@Delete("DELETE FROM agent_datasource_tables WHERE agent_datasource_id = #{agentDatasourceId}")
	int removeAllTables(@Param("agentDatasourceId") int agentDatasourceId);

	// 插入用户选择的列表
	@Insert("<script>" + "INSERT IGNORE INTO agent_datasource_tables (agent_datasource_id, table_name) VALUES "
			+ "<if test='tables != null and tables.size() > 0'>"
			+ "<foreach collection='tables' item='table' separator=','>" + "(#{agentDatasourceId}, #{table})"
			+ "</foreach>" + "</if>" + "</script>")
	int insertNewTables(@Param("agentDatasourceId") int agentDatasourceId, @Param("tables") List<String> tables);

	// 更新用户的选择（tables不能为空）
	default int updateAgentDatasourceTables(int agentDatasourceId, List<String> tables) {
		if (tables.isEmpty()) {
			throw new IllegalArgumentException("tables cannot be empty");
		}
		int deleteCount = removeExpireTables(agentDatasourceId, tables);
		int insertCount = insertNewTables(agentDatasourceId, tables);
		return deleteCount + insertCount;
	}

}
