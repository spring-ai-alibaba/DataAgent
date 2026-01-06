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

import com.alibaba.cloud.ai.dataagent.entity.ChatMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

	/**
	 * Query message list by session ID
	 */
	@Select("""
			SELECT * FROM chat_message
			WHERE session_id = #{sessionId}
			ORDER BY create_time ASC
			""")
	List<ChatMessage> selectBySessionId(@Param("sessionId") String sessionId);

	/**
	 * Query by id
	 */
	@Select("""
			SELECT * FROM chat_message
			WHERE id = #{id}
			""")
	ChatMessage selectById(@Param("id") Long id);

	/**
	 * Query message count by session ID
	 */
	@Select("""
			SELECT COUNT(*) FROM chat_message
			WHERE session_id = #{sessionId}
			""")
	int countBySessionId(@Param("sessionId") String sessionId);

	/**
	 * Query message list by session ID and role
	 */
	@Select("""
			SELECT * FROM chat_message
			WHERE session_id = #{sessionId}
			AND role = #{role}
			ORDER BY create_time ASC
			""")
	List<ChatMessage> selectBySessionIdAndRole(@Param("sessionId") String sessionId, @Param("role") String role);

	@Insert("""
			INSERT INTO chat_message (session_id, role, content, message_type, metadata, create_time)
			VALUES (#{sessionId}, #{role}, #{content}, #{messageType}, #{metadata}, NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(ChatMessage message);

	@Delete("""
			DELETE FROM chat_message
			WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id);

}
