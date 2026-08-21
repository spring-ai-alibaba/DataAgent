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
import com.alibaba.cloud.ai.dataagent.entity.MemoryItem;
import com.alibaba.cloud.ai.dataagent.entity.MemoryOutboxEvent;
import com.alibaba.cloud.ai.dataagent.entity.TurnRun;
import com.alibaba.cloud.ai.dataagent.enums.*;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryEventType;
import com.alibaba.cloud.ai.dataagent.service.memory.shortterm.ConversationMemoryGateway;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
class MemoryMapperIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ConversationTurnMapper turnMapper;

	@Autowired
	private MemoryItemMapper memoryItemMapper;

	@Autowired
	private TurnRunMapper turnRunMapper;

	@Autowired
	private MemoryOutboxMapper outboxMapper;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("DROP TABLE IF EXISTS memory_outbox");
		jdbcTemplate.execute("DROP TABLE IF EXISTS memory_item");
		jdbcTemplate.execute("DROP TABLE IF EXISTS turn_run");
		jdbcTemplate.execute("DROP TABLE IF EXISTS conversation_turn");
		jdbcTemplate.execute("DROP TABLE IF EXISTS SPRING_AI_CHAT_MEMORY");
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
					  lease_token VARCHAR(36),
					  available_at TIMESTAMP,
				  last_error TEXT,
				  create_time TIMESTAMP,
				  update_time TIMESTAMP
				)
				""");
		jdbcTemplate.execute("""
				CREATE TABLE SPRING_AI_CHAT_MEMORY (
				  conversation_id VARCHAR(36) NOT NULL,
				  content LONGVARCHAR NOT NULL,
				  type VARCHAR(10) NOT NULL,
				  timestamp TIMESTAMP NOT NULL,
				  CONSTRAINT chk_test_chat_memory_type CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
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
			.finalAnswer("monthly sales: 100")
			.schemaFingerprint("schema-v1")
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
			.schemaFingerprint("schema-v1")
			.build();
		memoryItemMapper.insert(item);

		assertThat(turnMapper.selectContextTimeline("conversation-1")).singleElement()
			.extracting(ConversationTurn::getStatus)
			.isEqualTo(TurnStatus.SUCCEEDED);
		assertThat(memoryItemMapper.selectConfirmedForContext(null, 7, 3, "schema-v1", 5)).singleElement()
			.extracting(MemoryItem::getMemoryKind)
			.isEqualTo(MemoryKind.QUERY_PATTERN);
		assertThat(memoryItemMapper.selectConfirmedForContext(null, 7, 3, "schema-v2", 5)).isEmpty();
		assertThat(memoryItemMapper.selectConfirmedForContext(null, 7, 3, null, 5)).isEmpty();
		assertThat(memoryItemMapper.selectConfirmedForContext(null, 7, 4, "schema-v1", 5)).isEmpty();
	}

	@Test
	void recentContextTurnsAreBoundedToNewestSuccessfulCompletedTurns() {
		LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 10, 0);
		insertSuccessfulTurn("turn-1", "question-1", "answer-1", baseTime);
		insertSuccessfulTurn("turn-2", "question-2", "answer-2", baseTime.plusMinutes(1));
		insertSuccessfulTurn("turn-3", "question-3", "answer-3", baseTime.plusMinutes(2));

		ConversationTurn failedTurn = ConversationTurn.builder()
			.id("turn-failed")
			.conversationId("conversation-1")
			.agentId(7)
			.acceptedRunId("run-failed")
			.rawQuery("failed-question")
			.status(TurnStatus.RUNNING)
			.build();
		turnMapper.insert(failedTurn);
		turnMapper.markTerminal("turn-failed", "run-failed", TurnStatus.FAILED);

		assertThat(turnMapper.selectRecentContextTurns("conversation-1", 2)).extracting(ConversationTurn::getId)
			.containsExactly("turn-3", "turn-2");
		assertThat(turnMapper.selectSummaryBoundaryTurnId("conversation-1", 2)).isEqualTo("turn-1");
		assertThat(turnMapper.selectSummaryBoundaryTurnId("conversation-1", 3)).isNull();
	}

	@Test
	void episodicFallbackExcludesTheCurrentConversationBeforeApplyingItsLimit() {
		LocalDateTime baseTime = LocalDateTime.of(2026, 8, 20, 10, 0);
		insertSuccessfulOwnedTurn("other-old", "conversation-2", baseTime, 99L, 7, 3);
		insertSuccessfulOwnedTurn("other-new", "conversation-3", baseTime.plusMinutes(1), 99L, 7, 3);
		for (int index = 0; index < 5; index++) {
			insertSuccessfulOwnedTurn("current-" + index, "conversation-1", baseTime.plusHours(1).plusMinutes(index),
					99L, 7, 3);
		}

		assertThat(turnMapper.selectRecentSuccessfulByOwner(99L, 7, 3, "conversation-1", 2))
			.extracting(ConversationTurn::getId)
			.containsExactly("other-new", "other-old");
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
		turnRunMapper.insert(TurnRun.builder().runId("run-1").turnId("turn-1").status(TurnStatus.RUNNING).build());

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
			.eventType("TURN_COMPLETED")
			.build();
		outboxMapper.insert(event);

		assertThat(outboxMapper.selectReady(10, 5)).extracting(MemoryOutboxEvent::getId).containsExactly(event.getId());
		assertThat(outboxMapper.claim(event.getId(), "lease-one", 5)).isEqualTo(1);
		assertThat(outboxMapper.claim(event.getId(), "other-lease", 5)).isZero();
		jdbcTemplate.update("UPDATE memory_outbox SET update_time = ?", LocalDateTime.now().minusMinutes(10));
		assertThat(outboxMapper.recoverStale(LocalDateTime.now().minusMinutes(5))).isEqualTo(1);
		assertThat(outboxMapper.claim(event.getId(), "lease-two", 5)).isEqualTo(1);
		assertThat(outboxMapper.markDone(event.getId(), "lease-one")).isZero();
		assertThat(outboxMapper.markFailed(event.getId(), "lease-two", "retry", LocalDateTime.now().plusMinutes(1)))
			.isEqualTo(1);
		assertThat(outboxMapper.claim(event.getId(), "stale-selected-worker", 5)).isZero();
		jdbcTemplate.update("UPDATE memory_outbox SET available_at = ? WHERE id = ?",
				LocalDateTime.now().minusSeconds(1), event.getId());
		assertThat(outboxMapper.selectReady(10, 5)).hasSize(1);
		jdbcTemplate.update("UPDATE memory_outbox SET status = 'FAILED', attempt_count = 5 WHERE id = ?",
				event.getId());
		assertThat(outboxMapper.markExhaustedAsDead(5)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM memory_outbox WHERE id = ?", String.class,
				event.getId()))
			.isEqualTo("DEAD");
	}

	@Test
	void exhaustedDestructiveOutboxEventsRemainClaimableInsteadOfBecomingDead() {
		for (String eventType : List.of(MemoryEventType.TURN_INVALIDATED, MemoryEventType.CONVERSATION_FORGOTTEN,
				MemoryEventType.GRAPH_CHECKPOINT_RELEASE, MemoryEventType.MEMORY_INVALIDATED)) {
			MemoryOutboxEvent event = MemoryOutboxEvent.builder()
				.aggregateType("CLEANUP")
				.aggregateId(eventType)
				.eventType(eventType)
				.build();
			outboxMapper.insert(event);
			jdbcTemplate.update(
					"UPDATE memory_outbox SET status = 'FAILED', attempt_count = 5, available_at = ? WHERE id = ?",
					LocalDateTime.now().minusSeconds(1), event.getId());
		}

		assertThat(outboxMapper.markExhaustedAsDead(5)).isZero();
		List<MemoryOutboxEvent> ready = outboxMapper.selectReady(10, 5);
		assertThat(ready).extracting(MemoryOutboxEvent::getEventType)
			.containsExactly(MemoryEventType.TURN_INVALIDATED, MemoryEventType.CONVERSATION_FORGOTTEN,
					MemoryEventType.GRAPH_CHECKPOINT_RELEASE, MemoryEventType.MEMORY_INVALIDATED);
		for (MemoryOutboxEvent event : ready) {
			assertThat(outboxMapper.claim(event.getId(), "lease-" + event.getId(), 5)).isEqualTo(1);
		}
	}

	@Test
	void destructiveDeadLettersFromOlderWorkersAreRevivedForGuaranteedRetry() {
		for (String eventType : List.of(MemoryEventType.TURN_INVALIDATED, MemoryEventType.CONVERSATION_FORGOTTEN,
				MemoryEventType.GRAPH_CHECKPOINT_RELEASE, MemoryEventType.MEMORY_INVALIDATED)) {
			MemoryOutboxEvent event = MemoryOutboxEvent.builder()
				.aggregateType("LEGACY_CLEANUP")
				.aggregateId(eventType)
				.eventType(eventType)
				.build();
			outboxMapper.insert(event);
			jdbcTemplate.update(
					"UPDATE memory_outbox SET status = 'DEAD', attempt_count = 5, available_at = ? WHERE id = ?",
					LocalDateTime.now().plusDays(1), event.getId());
		}
		MemoryOutboxEvent rebuildable = MemoryOutboxEvent.builder()
			.aggregateType("REBUILD")
			.aggregateId("turn-1")
			.eventType(MemoryEventType.TURN_COMPLETED)
			.build();
		outboxMapper.insert(rebuildable);
		jdbcTemplate.update("UPDATE memory_outbox SET status = 'DEAD', attempt_count = 5 WHERE id = ?",
				rebuildable.getId());

		assertThat(outboxMapper.reviveGuaranteedRetryDeadLetters()).isEqualTo(4);

		assertThat(outboxMapper.selectReady(10, 5)).extracting(MemoryOutboxEvent::getEventType)
			.containsExactly(MemoryEventType.TURN_INVALIDATED, MemoryEventType.CONVERSATION_FORGOTTEN,
					MemoryEventType.GRAPH_CHECKPOINT_RELEASE, MemoryEventType.MEMORY_INVALIDATED);
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM memory_outbox WHERE id = ?", String.class,
				rebuildable.getId()))
			.isEqualTo("DEAD");
	}

	@Test
	void completedOutboxRetentionQueryNeverDeletesFailedOrRecentEvents() {
		MemoryOutboxEvent oldCompleted = outboxEvent("old-completed");
		MemoryOutboxEvent recentCompleted = outboxEvent("recent-completed");
		MemoryOutboxEvent oldFailed = outboxEvent("old-failed");
		outboxMapper.insert(oldCompleted);
		outboxMapper.insert(recentCompleted);
		outboxMapper.insert(oldFailed);
		LocalDateTime now = LocalDateTime.now();
		jdbcTemplate.update("UPDATE memory_outbox SET status = 'DONE', update_time = ? WHERE id = ?", now.minusDays(8),
				oldCompleted.getId());
		jdbcTemplate.update("UPDATE memory_outbox SET status = 'DONE', update_time = ? WHERE id = ?", now,
				recentCompleted.getId());
		jdbcTemplate.update("UPDATE memory_outbox SET status = 'FAILED', update_time = ? WHERE id = ?",
				now.minusDays(8), oldFailed.getId());

		List<MemoryOutboxEvent> expired = outboxMapper.selectCompletedBefore(now.minusDays(7), 10);
		List<Long> expiredIds = expired.stream().map(MemoryOutboxEvent::getId).toList();

		assertThat(expired).singleElement().satisfies(event -> {
			assertThat(event.getId()).isEqualTo(oldCompleted.getId());
			assertThat(event.getAggregateId()).isEqualTo("old-completed");
			assertThat(event.getEventType()).isEqualTo("TEST_EVENT");
		});
		assertThat(outboxMapper.deleteCompletedByIds(expiredIds)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memory_outbox", Integer.class)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM memory_outbox WHERE id = ?", String.class,
				oldFailed.getId()))
			.isEqualTo("FAILED");
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void frameworkChatMemoryJoinsTheOuterBusinessTransaction() {
		var repository = JdbcChatMemoryRepository.builder()
			.jdbcTemplate(jdbcTemplate)
			.transactionManager(transactionManager)
			.build();
		var chatMemory = MessageWindowChatMemory.builder().chatMemoryRepository(repository).maxMessages(6).build();
		var gateway = new ConversationMemoryGateway(chatMemory, turnMapper, new DataAgentProperties());
		var transaction = new TransactionTemplate(transactionManager);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			gateway.commitSuccessfulTurn("conversation-1", "question", "answer");
			throw new IllegalStateException("force rollback after framework memory write");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(gateway.loadRecent("conversation-1")).isEmpty();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_AI_CHAT_MEMORY", Integer.class)).isZero();
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

	private MemoryOutboxEvent outboxEvent(String aggregateId) {
		return MemoryOutboxEvent.builder()
			.aggregateType("TEST")
			.aggregateId(aggregateId)
			.eventType("TEST_EVENT")
			.build();
	}

	private void insertSuccessfulTurn(String turnId, String question, String answer, LocalDateTime observedAt) {
		String runId = "run-" + turnId;
		turnMapper.insert(ConversationTurn.builder()
			.id(turnId)
			.conversationId("conversation-1")
			.agentId(7)
			.acceptedRunId(runId)
			.rawQuery(question)
			.status(TurnStatus.RUNNING)
			.build());
		assertThat(turnMapper.complete(ConversationTurn.builder()
			.id(turnId)
			.acceptedRunId(runId)
			.finalAnswer(answer)
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(true)
			.observedAt(observedAt)
			.completedAt(observedAt)
			.build())).isEqualTo(1);
	}

	private void insertSuccessfulOwnedTurn(String turnId, String conversationId, LocalDateTime observedAt, Long ownerId,
			Integer agentId, Integer datasourceId) {
		String runId = "run-" + turnId;
		turnMapper.insert(ConversationTurn.builder()
			.id(turnId)
			.conversationId(conversationId)
			.agentId(agentId)
			.ownerId(ownerId)
			.acceptedRunId(runId)
			.datasourceId(datasourceId)
			.rawQuery("question-" + turnId)
			.status(TurnStatus.RUNNING)
			.build());
		assertThat(turnMapper.complete(ConversationTurn.builder()
			.id(turnId)
			.acceptedRunId(runId)
			.datasourceId(datasourceId)
			.finalAnswer("answer-" + turnId)
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(true)
			.observedAt(observedAt)
			.completedAt(observedAt)
			.build())).isEqualTo(1);
	}

}
