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

import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.enums.TurnStatus;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ConversationTurnMapper {

	@Insert("""
			INSERT INTO conversation_turn
			    (id, conversation_id, agent_id, owner_id, accepted_run_id, raw_query, status,
			     memory_eligible, create_time, update_time)
			VALUES
			    (#{id}, #{conversationId}, #{agentId}, #{ownerId}, #{acceptedRunId}, #{rawQuery},
			     #{status}, #{memoryEligible}, NOW(), NOW())
			""")
	int insert(ConversationTurn turn);

	@Select("SELECT * FROM conversation_turn WHERE id = #{id}")
	ConversationTurn selectById(@Param("id") String id);

	@Select("SELECT * FROM conversation_turn WHERE conversation_id = #{conversationId}")
	List<ConversationTurn> selectByConversationId(@Param("conversationId") String conversationId);

	@Select("""
			SELECT * FROM conversation_turn
			WHERE conversation_id = #{conversationId}
			  AND status = 'SUCCEEDED'
			  AND memory_eligible = 1
			ORDER BY observed_at DESC, create_time DESC, id DESC
			LIMIT #{limit}
			""")
	List<ConversationTurn> selectRecentSuccessful(@Param("conversationId") String conversationId,
			@Param("limit") int limit);

	@Select("""
			SELECT * FROM conversation_turn
			WHERE conversation_id = #{conversationId}
			  AND status = 'SUCCEEDED'
			  AND memory_eligible = 1
			ORDER BY observed_at ASC, create_time ASC, id ASC
			""")
	List<ConversationTurn> selectAllSuccessful(@Param("conversationId") String conversationId);

	@Select("""
			SELECT * FROM conversation_turn
			WHERE owner_id = #{ownerId}
			  AND agent_id = #{agentId}
			  AND status = 'SUCCEEDED'
			  AND memory_eligible = 1
			  AND (#{datasourceId} IS NULL OR datasource_id = #{datasourceId})
			ORDER BY observed_at DESC, create_time DESC, id DESC
			LIMIT #{limit}
			""")
	List<ConversationTurn> selectRecentSuccessfulByOwner(@Param("ownerId") Long ownerId,
			@Param("agentId") Integer agentId, @Param("datasourceId") Integer datasourceId, @Param("limit") int limit);

	@Select("""
			<script>
			SELECT * FROM conversation_turn
			WHERE id IN
			<foreach collection="ids" item="id" open="(" separator="," close=")">
			  #{id}
			</foreach>
			  AND status = 'SUCCEEDED'
			  AND memory_eligible = 1
			</script>
			""")
	List<ConversationTurn> selectSuccessfulByIds(@Param("ids") List<String> ids);

	@Update("""
			UPDATE conversation_turn
			SET status = 'RUNNING', update_time = NOW()
			WHERE id = #{turnId}
			  AND accepted_run_id = #{runId}
			  AND status = 'WAITING_REVIEW'
			""")
	int markRunning(@Param("turnId") String turnId, @Param("runId") String runId);

	@Update("""
			UPDATE conversation_turn
			SET status = 'WAITING_REVIEW', update_time = NOW()
			WHERE id = #{turnId}
			  AND accepted_run_id = #{runId}
			  AND status = 'RUNNING'
			""")
	int markWaitingReview(@Param("turnId") String turnId, @Param("runId") String runId);

	@Update("""
			UPDATE conversation_turn
			SET accepted_run_id = #{acceptedRunId},
			    datasource_id = #{datasourceId},
			    canonical_query = #{canonicalQuery},
			    query_frame = #{queryFrame},
			    result_summary = #{resultSummary},
			    final_answer = #{finalAnswer},
			    schema_fingerprint = #{schemaFingerprint},
			    status = #{status},
			    memory_eligible = #{memoryEligible},
			    observed_at = #{observedAt},
			    completed_at = #{completedAt},
			    update_time = NOW()
			WHERE id = #{id}
			  AND accepted_run_id = #{acceptedRunId}
			  AND status = 'RUNNING'
			""")
	int complete(ConversationTurn turn);

	@Update("""
			UPDATE conversation_turn
			SET status = #{status}, memory_eligible = 0, completed_at = NOW(), update_time = NOW()
			WHERE id = #{turnId}
			  AND accepted_run_id = #{runId}
			  AND status IN ('RUNNING', 'WAITING_REVIEW')
			""")
	int markTerminal(@Param("turnId") String turnId, @Param("runId") String runId, @Param("status") TurnStatus status);

	@Delete("DELETE FROM conversation_turn WHERE conversation_id = #{conversationId}")
	int deleteByConversationId(@Param("conversationId") String conversationId);

}
