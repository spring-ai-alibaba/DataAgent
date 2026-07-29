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

import com.alibaba.cloud.ai.dataagent.entity.TurnRun;
import com.alibaba.cloud.ai.dataagent.enums.TurnStatus;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TurnRunMapper {

	@Insert("""
			INSERT INTO turn_run
			    (run_id, turn_id, attempt, status, create_time, update_time)
			VALUES
			    (#{runId}, #{turnId}, #{attempt}, #{status}, NOW(), NOW())
			""")
	int insert(TurnRun run);

	@Select("SELECT * FROM turn_run WHERE run_id = #{runId}")
	TurnRun selectById(@Param("runId") String runId);

	@Update("""
			UPDATE turn_run
			SET status = #{status}, error_message = #{errorMessage}, update_time = NOW()
			WHERE run_id = #{runId}
			""")
	int updateStatus(@Param("runId") String runId, @Param("status") TurnStatus status,
			@Param("errorMessage") String errorMessage);

	@Update("""
			UPDATE turn_run
			SET attempt = attempt + 1, status = 'RUNNING', error_message = NULL, update_time = NOW()
			WHERE run_id = #{runId}
			""")
	int incrementAttempt(@Param("runId") String runId);

}
