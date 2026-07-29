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

import com.alibaba.cloud.ai.dataagent.entity.MemoryItem;
import com.alibaba.cloud.ai.dataagent.enums.MemoryStatus;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MemoryItemMapper {

	@Insert("""
			INSERT INTO memory_item
			    (scope_type, owner_id, agent_id, datasource_id, memory_kind, memory_key, value_json,
			     identity_hash, active_identity_hash, source_turn_id, status, confidence,
			     schema_fingerprint, valid_until, supersedes_id,
			     create_time, update_time)
			VALUES
			    (#{scopeType}, #{ownerId}, #{agentId}, #{datasourceId}, #{memoryKind}, #{memoryKey},
			     #{valueJson}, #{identityHash}, #{activeIdentityHash}, #{sourceTurnId}, #{status},
			     #{confidence}, #{schemaFingerprint}, #{validUntil}, #{supersedesId}, NOW(), NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(MemoryItem item);

	@Select("SELECT * FROM memory_item WHERE id = #{id}")
	MemoryItem selectById(@Param("id") Long id);

	@Select("SELECT * FROM memory_item WHERE id = #{id} FOR UPDATE")
	MemoryItem selectByIdForUpdate(@Param("id") Long id);

	@Select("""
			SELECT * FROM memory_item
			WHERE active_identity_hash = #{identityHash}
			LIMIT 1
			FOR UPDATE
			""")
	MemoryItem selectConfirmedByIdentityHashForUpdate(@Param("identityHash") String identityHash);

	@Select("""
			<script>
			SELECT * FROM memory_item
			WHERE agent_id = #{agentId}
			<if test="status != null">
			  AND status = #{status}
			</if>
			ORDER BY update_time DESC, id DESC
			</script>
			""")
	List<MemoryItem> selectByAgentId(@Param("agentId") Integer agentId, @Param("status") MemoryStatus status);

	@Select("SELECT * FROM memory_item WHERE source_turn_id IN (SELECT id FROM conversation_turn WHERE conversation_id = #{conversationId})")
	List<MemoryItem> selectByConversationId(@Param("conversationId") String conversationId);

	@Select("""
			<script>
			SELECT * FROM memory_item
			WHERE id IN
			<foreach collection="ids" item="id" open="(" separator="," close=")">
			  #{id}
			</foreach>
			  AND status = 'CONFIRMED'
			  AND (valid_until IS NULL OR valid_until &gt; NOW())
			</script>
			""")
	List<MemoryItem> selectConfirmedByIds(@Param("ids") List<Long> ids);

	@Select("""
			<script>
			SELECT * FROM memory_item
			WHERE agent_id = #{agentId}
			  AND status = 'CONFIRMED'
			  AND (valid_until IS NULL OR valid_until &gt; NOW())
			  AND (
			        scope_type = 'AGENT'
			        <if test="datasourceId != null">
			          OR (scope_type = 'DATASOURCE' AND datasource_id = #{datasourceId})
			        </if>
			        <if test="ownerId != null">
			          OR (scope_type = 'USER_AGENT' AND owner_id = #{ownerId})
			        </if>
			      )
			ORDER BY confidence DESC, update_time DESC
			LIMIT #{limit}
			</script>
			""")
	List<MemoryItem> selectConfirmedForContext(@Param("ownerId") Long ownerId, @Param("agentId") Integer agentId,
			@Param("datasourceId") Integer datasourceId, @Param("limit") int limit);

	@Update("""
			UPDATE memory_item
			SET status = 'CONFIRMED', active_identity_hash = identity_hash, update_time = NOW()
			WHERE id = #{id} AND status = 'CANDIDATE'
			""")
	int confirmCandidate(@Param("id") Long id);

	@Update("""
			UPDATE memory_item
			SET status = 'SUPERSEDED', active_identity_hash = NULL, update_time = NOW()
			WHERE id = #{id} AND status = 'CONFIRMED'
			""")
	int markSuperseded(@Param("id") Long id);

	@Update("""
			UPDATE memory_item
			SET status = 'INVALIDATED', active_identity_hash = NULL, update_time = NOW()
			WHERE id = #{id} AND status IN ('CANDIDATE', 'CONFIRMED')
			""")
	int invalidate(@Param("id") Long id);

	@Delete("DELETE FROM memory_item WHERE source_turn_id IN (SELECT id FROM conversation_turn WHERE conversation_id = #{conversationId})")
	int deleteByConversationId(@Param("conversationId") String conversationId);

}
