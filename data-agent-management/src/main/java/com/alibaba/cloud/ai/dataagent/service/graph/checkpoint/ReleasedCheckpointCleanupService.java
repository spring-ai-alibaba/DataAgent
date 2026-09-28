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
package com.alibaba.cloud.ai.dataagent.service.graph.checkpoint;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Applies bounded physical retention to rows that the framework {@code MysqlSaver} has
 * already released logically.
 *
 * <p>
 * Checkpoint serialization, recovery and release remain framework-owned. This component
 * performs only two retention mechanics around them: it unblocks the published 1.1.2.3
 * unique-index conflict after framework release fails, and it removes every database
 * generation of a logical thread after its durable Outbox release event has completed and
 * passed the configured retention period. Final removal also covers a remote writer that
 * raced logical release and recreated an active row with the same thread name. Deleting a
 * {@code GRAPH_THREAD} cascades to its {@code GRAPH_CHECKPOINT} rows through the
 * framework-created foreign key.
 */
@Component
@RequiredArgsConstructor
public class ReleasedCheckpointCleanupService {

	private static final String DELETE_RETAINED_THREAD = """
			DELETE FROM GRAPH_THREAD
			WHERE thread_name = ?
			""";

	private static final String LOCK_THREAD_GENERATIONS = """
			SELECT is_released
			FROM GRAPH_THREAD
			WHERE thread_name = ?
			FOR UPDATE
			""";

	private static final String DELETE_RELEASED_GENERATIONS = """
			DELETE FROM GRAPH_THREAD
			WHERE thread_name = ? AND is_released = TRUE
			""";

	private final JdbcTemplate jdbcTemplate;

	private final DataAgentProperties properties;

	/**
	 * Repairs the published graph-core {@code 1.1.2.3} release conflict without replacing
	 * framework checkpoint semantics. That release uses a unique
	 * {@code (thread_name, is_released)} index: if a remote writer recreates an active
	 * generation after an earlier release, changing the new row to released conflicts
	 * with the retained row. Only after framework release has failed, lock both
	 * generations and discard the older released generation; the caller must then retry
	 * framework {@code release()}.
	 * @return {@code true} only when a blocking released generation was removed
	 */
	@Transactional
	public boolean reconcileLegacyReleaseConflict(String threadId) {
		if (!"mysql".equalsIgnoreCase(properties.getCheckpoint().getType()) || !StringUtils.hasText(threadId)) {
			return false;
		}
		List<Boolean> generations = jdbcTemplate.query(LOCK_THREAD_GENERATIONS,
				(resultSet, rowNumber) -> resultSet.getBoolean("is_released"), threadId);
		boolean hasActive = generations.stream().anyMatch(released -> !released);
		boolean hasReleased = generations.stream().anyMatch(Boolean::booleanValue);
		if (!hasActive || !hasReleased) {
			return false;
		}
		return jdbcTemplate.update(DELETE_RELEASED_GENERATIONS, threadId) > 0;
	}

	public void purgeReleased(String threadId) {
		if (!"mysql".equalsIgnoreCase(properties.getCheckpoint().getType()) || !StringUtils.hasText(threadId)) {
			return;
		}
		jdbcTemplate.update(DELETE_RETAINED_THREAD, threadId);
	}

}
