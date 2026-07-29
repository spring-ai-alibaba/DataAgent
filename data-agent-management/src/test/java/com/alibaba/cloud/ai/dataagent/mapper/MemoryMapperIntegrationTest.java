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

import com.alibaba.cloud.ai.dataagent.entity.ConversationSummary;
import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.entity.MemoryItem;
import com.alibaba.cloud.ai.dataagent.entity.MemoryOutboxEvent;
import com.alibaba.cloud.ai.dataagent.entity.TurnRun;
import com.alibaba.cloud.ai.dataagent.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
class MemoryMapperIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ConversationTurnMapper turnMapper;

	@Autowired
	private ConversationSummaryMapper summaryMapper;

	@Autowired
	private MemoryItemMapper memoryItemMapper;

	@Autowired
	private TurnRunMapper turnRunMapper;

	@Autowired
	private MemoryOutboxMapper outboxMapper;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("DROP TABLE IF EXISTS memory_outbox");
		jdbcTemplate.execute("DROP TABLE IF EXISTS memory_item");
		jdbcTemplate.execute("DROP TABLE IF EXISTS turn_run");
		jdbcTemplate.execute("DROP TABLE IF EXISTS conversation_summary");
		jdbcTemplate.execute("DROP TABLE IF EXISTS conversation_turn");
		jdbcTemplate.execute("""
				CREATE TABLE conversation_turn (
				  id VARCHAR(36) PRIMARY KEY,
				  conversation_id VARCHAR(36) NOT NULL,
				  agent_id INT NOT NULL,
				  owner_id BIGINT,
				  accepted_run_id VARCHAR(36),
				  datasource_id INT,
				  raw_query TEXT NOT NULL,
				  canonical_query TEXT,
				  query_frame TEXT,
				  result_summary TEXT,
				  final_answer TEXT,
				  schema_fingerprint VARCHAR(128),
				  status VARCHAR(32) NOT NULL,
				  memory_eligible TINYINT NOT NULL,
				  observed_at TIMESTAMP,
				  completed_at TIMESTAMP,
				  create_time TIMESTAMP,
				  update_time TIMESTAMP
					)
					""");
		jdbcTemplate.execute("""
				CREATE TABLE conversation_summary (
				  conversation_id VARCHAR(36) PRIMARY KEY,
				  summary_text TEXT NOT NULL,
				  covered_through_turn_id VARCHAR(36),
				  version BIGINT NOT NULL,
				  create_time TIMESTAMP,
				  update_time TIMESTAMP
				)
				""");
		jdbcTemplate.execute("""
				CREATE TABLE turn_run (
				  run_id VARCHAR(36) PRIMARY KEY,
				  turn_id VARCHAR(36) NOT NULL,
				  attempt INT NOT NULL,
				  status VARCHAR(32) NOT NULL,
				  error_message TEXT,
				  create_time TIMESTAMP,
				  update_time TIMESTAMP,
				  FOREIGN KEY (turn_id) REFERENCES conversation_turn(id) ON DELETE CASCADE
				)
				""");
		jdbcTemplate.execute("""
				CREATE TABLE memory_item (
				  id BIGINT AUTO_INCREMENT PRIMARY KEY,
				  scope_type VARCHAR(32) NOT NULL,
				  owner_id BIGINT,
				  agent_id INT NOT NULL,
				  datasource_id INT,
					  memory_kind VARCHAR(32) NOT NULL,
					  memory_key VARCHAR(255) NOT NULL,
					  value_json TEXT NOT NULL,
					  identity_hash CHAR(64) NOT NULL,
					  active_identity_hash CHAR(64),
					  source_turn_id VARCHAR(36),
				  status VARCHAR(32) NOT NULL,
				  confidence DECIMAL(5,4) NOT NULL,
				  schema_fingerprint VARCHAR(128),
				  valid_until TIMESTAMP,
					  supersedes_id BIGINT,
					  create_time TIMESTAMP,
					  update_time TIMESTAMP,
					  UNIQUE (active_identity_hash),
					  CONSTRAINT chk_test_active_identity CHECK (
					    (status = 'CONFIRMED' AND active_identity_hash IS NOT NULL
					      AND active_identity_hash = identity_hash)
					    OR (status <> 'CONFIRMED' AND active_identity_hash IS NULL)
					  )
					)
				""");
		jdbcTemplate.execute("""
				CREATE TABLE memory_outbox (
				  id BIGINT AUTO_INCREMENT PRIMARY KEY,
				  aggregate_type VARCHAR(32) NOT NULL,
				  aggregate_id VARCHAR(64) NOT NULL,
				  event_type VARCHAR(64) NOT NULL,
				  payload TEXT,
				  status VARCHAR(16) NOT NULL,
				  attempt_count INT NOT NULL,
				  available_at TIMESTAMP,
				  last_error TEXT,
				  create_time TIMESTAMP,
				  update_time TIMESTAMP
				)
				""");
	}

	@Test
	void successfulTurnAndScopedConfirmedMemoryRoundTripThroughMybatis() {
		ConversationTurn turn = ConversationTurn.builder()
			.id("turn-1")
			.conversationId("conversation-1")
			.agentId(7)
			.acceptedRunId("run-1")
			.rawQuery("sales")
			.status(TurnStatus.RUNNING)
			.build();
		turnMapper.insert(turn);
		LocalDateTime now = LocalDateTime.now();
		turnMapper.complete(ConversationTurn.builder()
			.id("turn-1")
			.acceptedRunId("run-1")
			.datasourceId(3)
			.canonicalQuery("monthly sales")
			.queryFrame("{}")
			.resultSummary("100")
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(true)
			.observedAt(now)
			.completedAt(now)
			.build());

		MemoryItem item = MemoryItem.builder()
			.scopeType(MemoryScopeType.DATASOURCE)
			.agentId(7)
			.datasourceId(3)
				.memoryKind(MemoryKind.QUERY_PATTERN)
				.memoryKey("sales-period")
				.valueJson("\"month\"")
				.identityHash("a".repeat(64))
				.activeIdentityHash("a".repeat(64))
				.sourceTurnId("turn-1")
			.status(MemoryStatus.CONFIRMED)
			.confidence(BigDecimal.ONE)
			.build();
		memoryItemMapper.insert(item);

		assertThat(turnMapper.selectRecentSuccessful("conversation-1", 3)).singleElement()
			.extracting(ConversationTurn::getStatus)
			.isEqualTo(TurnStatus.SUCCEEDED);
		assertThat(memoryItemMapper.selectConfirmedForContext(null, 7, 3, 5)).singleElement()
			.extracting(MemoryItem::getMemoryKind)
			.isEqualTo(MemoryKind.QUERY_PATTERN);
		assertThat(memoryItemMapper.selectConfirmedForContext(null, 7, 4, 5)).isEmpty();
	}

	@Test
	void terminalTurnCannotBeCompletedByALateCallback() {
		ConversationTurn turn = ConversationTurn.builder()
			.id("turn-1")
			.conversationId("conversation-1")
			.agentId(7)
			.acceptedRunId("run-1")
			.rawQuery("sales")
			.status(TurnStatus.RUNNING)
			.build();
		turnMapper.insert(turn);
		turnRunMapper.insert(TurnRun.builder()
			.runId("run-1")
			.turnId("turn-1")
			.status(TurnStatus.RUNNING)
			.build());

		assertThat(turnMapper.markTerminal("turn-1", "run-1", TurnStatus.CANCELLED)).isEqualTo(1);
		assertThat(turnRunMapper.markTerminal("run-1", TurnStatus.CANCELLED, "cancelled")).isEqualTo(1);
		assertThat(turnMapper.complete(ConversationTurn.builder()
			.id("turn-1")
			.acceptedRunId("run-1")
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(true)
			.observedAt(LocalDateTime.now())
			.completedAt(LocalDateTime.now())
			.build())).isZero();
		assertThat(turnRunMapper.markSucceeded("run-1")).isZero();
		assertThat(turnMapper.selectById("turn-1").getStatus()).isEqualTo(TurnStatus.CANCELLED);
	}

	@Test
	void summaryInsertAndUpdateAreVersioned() {
		ConversationSummary first = ConversationSummary.builder()
			.conversationId("conversation-1")
			.summaryText("first")
			.coveredThroughTurnId("turn-1")
			.build();
		ConversationSummary second = ConversationSummary.builder()
			.conversationId("conversation-1")
			.summaryText("second")
			.coveredThroughTurnId("turn-2")
			.build();

		assertThat(summaryMapper.insert(first)).isEqualTo(1);
		assertThat(summaryMapper.update(second)).isEqualTo(1);
		assertThat(summaryMapper.selectByConversationId("conversation-1"))
			.extracting(ConversationSummary::getSummaryText, ConversationSummary::getCoveredThroughTurnId,
					ConversationSummary::getVersion)
			.containsExactly("second", "turn-2", 2L);
	}

	@Test
	void databaseRejectsTwoConfirmedValuesForTheSameMemoryIdentity() {
		MemoryItem first = confirmedMemory("a".repeat(64), "\"CNY\"");
		MemoryItem second = confirmedMemory("a".repeat(64), "\"USD\"");

		memoryItemMapper.insert(first);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> memoryItemMapper.insert(second))
			.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	@Test
	void outboxClaimIsAtomicAndRetryable() {
		MemoryOutboxEvent event = MemoryOutboxEvent.builder()
			.aggregateType("CONVERSATION_TURN")
			.aggregateId("turn-1")
			.eventType("TURN_SUCCEEDED")
			.build();
		outboxMapper.insert(event);

		assertThat(outboxMapper.selectReady(10, 5)).extracting(MemoryOutboxEvent::getId).containsExactly(event.getId());
		assertThat(outboxMapper.claim(event.getId())).isEqualTo(1);
		assertThat(outboxMapper.claim(event.getId())).isZero();
		jdbcTemplate.update("UPDATE memory_outbox SET update_time = ?", LocalDateTime.now().minusMinutes(10));
		assertThat(outboxMapper.recoverStale(LocalDateTime.now().minusMinutes(5))).isEqualTo(1);
		outboxMapper.markFailed(event.getId(), "retry", LocalDateTime.now().minusSeconds(1));
		assertThat(outboxMapper.selectReady(10, 5)).hasSize(1);
	}

	private MemoryItem confirmedMemory(String identityHash, String value) {
		return MemoryItem.builder()
			.scopeType(MemoryScopeType.AGENT)
			.agentId(7)
			.memoryKind(MemoryKind.PREFERENCE)
			.memoryKey("currency")
			.valueJson(value)
			.identityHash(identityHash)
			.activeIdentityHash(identityHash)
			.status(MemoryStatus.CONFIRMED)
			.confidence(BigDecimal.ONE)
			.build();
	}

}
