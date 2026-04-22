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

import com.alibaba.cloud.ai.dataagent.entity.AgentSkillBinding;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentSkillBindingMapper {

	@Select("""
			SELECT * FROM agent_skill_binding WHERE agent_id = #{agentId} ORDER BY skill_id ASC
			""")
	List<AgentSkillBinding> findByAgentId(Long agentId);

	@Delete("""
			DELETE FROM agent_skill_binding WHERE agent_id = #{agentId}
			""")
	int deleteByAgentId(Long agentId);

	@Delete("""
			DELETE FROM agent_skill_binding WHERE skill_id = #{skillId}
			""")
	int deleteBySkillId(String skillId);

	@Insert("""
			<script>
			INSERT INTO agent_skill_binding (agent_id, skill_id, create_time, update_time)
			VALUES
			<foreach collection='skillIds' item='skillId' separator=','>
				(#{agentId}, #{skillId}, NOW(), NOW())
			</foreach>
			</script>
			""")
	@Options(useGeneratedKeys = false)
	int batchInsert(@Param("agentId") Long agentId, @Param("skillIds") List<String> skillIds);

}
