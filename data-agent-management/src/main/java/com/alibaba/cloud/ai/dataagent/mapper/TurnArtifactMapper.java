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

import com.alibaba.cloud.ai.dataagent.entity.TurnArtifact;
import com.alibaba.cloud.ai.dataagent.enums.TurnArtifactType;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TurnArtifactMapper {

	@Insert("""
			INSERT INTO turn_artifact
			    (turn_id, run_id, artifact_type, content, content_hash, create_time)
			VALUES
			    (#{turnId}, #{runId}, #{artifactType}, #{content}, #{contentHash}, NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(TurnArtifact artifact);

	@Delete("""
			DELETE FROM turn_artifact
			WHERE turn_id = #{turnId} AND run_id = #{runId} AND artifact_type = #{artifactType}
			""")
	int deleteByType(@Param("turnId") String turnId, @Param("runId") String runId,
			@Param("artifactType") TurnArtifactType artifactType);

	@Select("""
			SELECT * FROM turn_artifact
			WHERE turn_id = #{turnId}
			ORDER BY create_time ASC, id ASC
			""")
	List<TurnArtifact> selectByTurnId(@Param("turnId") String turnId);

}
