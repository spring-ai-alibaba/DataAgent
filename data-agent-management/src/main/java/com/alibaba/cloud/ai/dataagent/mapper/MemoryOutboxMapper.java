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
			  AND attempt_count < #{maxAttempts}
			ORDER BY id ASC
			LIMIT #{limit}
			""")
	List<MemoryOutboxEvent> selectReady(@Param("limit") int limit, @Param("maxAttempts") int maxAttempts);

	@Update("""
			UPDATE memory_outbox
			SET status = 'PROCESSING', attempt_count = attempt_count + 1, update_time = NOW()
			WHERE id = #{id} AND status IN ('PENDING', 'FAILED')
			""")
	int claim(@Param("id") Long id);

	@Update("""
			UPDATE memory_outbox
			SET status = 'FAILED',
			    last_error = 'Recovered stale PROCESSING memory projection',
			    available_at = NOW(),
			    update_time = NOW()
			WHERE status = 'PROCESSING' AND update_time < #{staleBefore}
			""")
	int recoverStale(@Param("staleBefore") LocalDateTime staleBefore);

	@Update("UPDATE memory_outbox SET status = 'DONE', last_error = NULL, update_time = NOW() WHERE id = #{id}")
	int markDone(@Param("id") Long id);

	@Update("""
			UPDATE memory_outbox
			SET status = 'FAILED', last_error = #{error}, available_at = #{availableAt}, update_time = NOW()
			WHERE id = #{id}
			""")
	int markFailed(@Param("id") Long id, @Param("error") String error, @Param("availableAt") LocalDateTime availableAt);

}
