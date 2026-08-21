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

import com.alibaba.cloud.ai.dataagent.entity.MemoryOutboxEvent;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MemoryOutboxMapper {

	@Insert("""
			INSERT INTO memory_outbox
			    (aggregate_type, aggregate_id, event_type, payload, status, attempt_count,
			     available_at, create_time, update_time)
			VALUES
			    (#{aggregateType}, #{aggregateId}, #{eventType}, #{payload}, 'PENDING', 0,
			     NOW(), NOW(), NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(MemoryOutboxEvent event);

	@Select("""
			SELECT * FROM memory_outbox
			WHERE status IN ('PENDING', 'FAILED')
			  AND available_at <= NOW()
			  AND (
			        attempt_count < #{maxAttempts}
			        OR event_type IN ('TURN_INVALIDATED', 'CONVERSATION_FORGOTTEN',
			                          'GRAPH_CHECKPOINT_RELEASE', 'MEMORY_INVALIDATED')
			      )
			ORDER BY id ASC
			LIMIT #{limit}
			""")
	List<MemoryOutboxEvent> selectReady(@Param("limit") int limit, @Param("maxAttempts") int maxAttempts);

	@Update("""
			UPDATE memory_outbox
			SET status = 'PROCESSING', attempt_count = attempt_count + 1,
			    lease_token = #{leaseToken}, update_time = NOW()
			WHERE id = #{id}
			  AND status IN ('PENDING', 'FAILED')
			  AND available_at <= NOW()
			  AND (
			        attempt_count < #{maxAttempts}
			        OR event_type IN ('TURN_INVALIDATED', 'CONVERSATION_FORGOTTEN',
			                          'GRAPH_CHECKPOINT_RELEASE', 'MEMORY_INVALIDATED')
			      )
			""")
	int claim(@Param("id") Long id, @Param("leaseToken") String leaseToken, @Param("maxAttempts") int maxAttempts);

	@Update("""
			UPDATE memory_outbox
				SET status = 'FAILED',
				    lease_token = NULL,
				    last_error = 'Recovered stale PROCESSING memory projection',
			    available_at = NOW(),
			    update_time = NOW()
			WHERE status = 'PROCESSING' AND update_time < #{staleBefore}
			""")
	int recoverStale(@Param("staleBefore") LocalDateTime staleBefore);

	/**
	 * Upgrades destructive obligations that an older worker may already have moved to
	 * DEAD before guaranteed retries were introduced.
	 */
	@Update("""
			UPDATE memory_outbox
			SET status = 'FAILED', lease_token = NULL, available_at = NOW(), update_time = NOW()
			WHERE status = 'DEAD'
			  AND event_type IN ('TURN_INVALIDATED', 'CONVERSATION_FORGOTTEN',
			                     'GRAPH_CHECKPOINT_RELEASE', 'MEMORY_INVALIDATED')
			""")
	int reviveGuaranteedRetryDeadLetters();

	@Update("""
			UPDATE memory_outbox
			SET status = 'DEAD', lease_token = NULL, update_time = NOW()
			WHERE status = 'FAILED' AND attempt_count >= #{maxAttempts}
			  AND event_type NOT IN ('TURN_INVALIDATED', 'CONVERSATION_FORGOTTEN',
			                         'GRAPH_CHECKPOINT_RELEASE', 'MEMORY_INVALIDATED')
			""")
	int markExhaustedAsDead(@Param("maxAttempts") int maxAttempts);

	@Update("""
			UPDATE memory_outbox
			SET status = 'DONE', lease_token = NULL, last_error = NULL, update_time = NOW()
			WHERE id = #{id} AND status = 'PROCESSING' AND lease_token = #{leaseToken}
			""")
	int markDone(@Param("id") Long id, @Param("leaseToken") String leaseToken);

	@Update("""
			UPDATE memory_outbox
			SET status = 'FAILED', lease_token = NULL, last_error = #{error},
			    available_at = #{availableAt}, update_time = NOW()
			WHERE id = #{id} AND status = 'PROCESSING' AND lease_token = #{leaseToken}
			""")
	int markFailed(@Param("id") Long id, @Param("leaseToken") String leaseToken, @Param("error") String error,
			@Param("availableAt") LocalDateTime availableAt);

	@Update("""
			UPDATE memory_outbox
			SET status = 'DEAD', lease_token = NULL, last_error = #{error}, update_time = NOW()
			WHERE id = #{id} AND status = 'PROCESSING' AND lease_token = #{leaseToken}
			""")
	int markDead(@Param("id") Long id, @Param("leaseToken") String leaseToken, @Param("error") String error);

	@Select("""
			SELECT * FROM memory_outbox
			WHERE status = 'DONE' AND update_time < #{completedBefore}
			ORDER BY id ASC
			LIMIT #{limit}
			""")
	List<MemoryOutboxEvent> selectCompletedBefore(@Param("completedBefore") LocalDateTime completedBefore,
			@Param("limit") int limit);

	@Delete("""
			<script>
			DELETE FROM memory_outbox
			WHERE status = 'DONE'
			  AND id IN
			<foreach collection="ids" item="id" open="(" separator="," close=")">
			  #{id}
			</foreach>
			</script>
			""")
	int deleteCompletedByIds(@Param("ids") List<Long> ids);

}
