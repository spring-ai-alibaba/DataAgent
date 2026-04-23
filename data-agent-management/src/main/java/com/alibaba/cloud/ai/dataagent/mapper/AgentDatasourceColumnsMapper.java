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

import com.alibaba.cloud.ai.dataagent.entity.AgentDatasourceColumn;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentDatasourceColumnsMapper {

	@Select("SELECT * FROM agent_datasource_columns WHERE agent_datasource_id = #{agentDatasourceId} ORDER BY table_name, column_name")
	List<AgentDatasourceColumn> getAgentDatasourceColumns(@Param("agentDatasourceId") int agentDatasourceId);

	@Delete("DELETE FROM agent_datasource_columns WHERE agent_datasource_id = #{agentDatasourceId}")
	int removeAllColumns(@Param("agentDatasourceId") int agentDatasourceId);

	@Delete("<script>"
			+ "DELETE FROM agent_datasource_columns WHERE agent_datasource_id = #{agentDatasourceId}"
			+ "<if test='tables != null and tables.size() > 0'>"
			+ " AND table_name NOT IN ("
			+ "<foreach collection='tables' item='table' separator=','>#{table}</foreach>"
			+ ")"
			+ "</if>"
			+ "</script>")
	int removeColumnsOutsideTables(@Param("agentDatasourceId") int agentDatasourceId,
			@Param("tables") List<String> tables);

	@Insert("<script>"
			+ "INSERT IGNORE INTO agent_datasource_columns (agent_datasource_id, table_name, column_name) VALUES "
			+ "<foreach collection='rows' item='row' separator=','>"
			+ "(#{row.agentDatasourceId}, #{row.tableName}, #{row.columnName})"
			+ "</foreach>"
			+ "</script>")
	int insertColumns(@Param("rows") List<AgentDatasourceColumn> rows);

}
