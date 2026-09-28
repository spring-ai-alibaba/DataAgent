-- Versioned MySQL upgrade from DataAgent main before durable memory was introduced.
-- Apply this file once before deploying the corresponding application version.

CREATE TABLE IF NOT EXISTS conversation_turn (
  id VARCHAR(36) NOT NULL COMMENT '逻辑轮次ID（UUID）',
  conversation_id VARCHAR(36) NOT NULL COMMENT '稳定会话ID',
  agent_id INT NOT NULL COMMENT '智能体ID',
  owner_id BIGINT COMMENT '可信用户ID；为空时不启用个人长期记忆',
  accepted_run_id VARCHAR(36) COMMENT '最终被接受的Graph运行ID',
  datasource_id INT COMMENT '执行时使用的数据源ID',
  raw_query TEXT NOT NULL COMMENT '用户原始问题',
  canonical_query TEXT COMMENT '规范化后的问题',
  query_frame MEDIUMTEXT COMMENT '指标、维度、过滤条件等结构化查询上下文JSON',
  result_summary TEXT COMMENT '用于短期记忆的有界结果摘要',
  final_answer MEDIUMTEXT COMMENT '最终回答或报告',
  schema_fingerprint VARCHAR(128) COMMENT '执行时Schema指纹',
  status VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/RUNNING/WAITING_REVIEW/SUCCEEDED/FAILED/CANCELLED',
  memory_eligible TINYINT NOT NULL DEFAULT 0 COMMENT '是否可进入模型记忆',
  observed_at TIMESTAMP NULL COMMENT '结果观测时间',
  completed_at TIMESTAMP NULL COMMENT '轮次完成时间',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_conversation_turn_conversation (conversation_id, create_time),
  INDEX idx_conversation_turn_owner_agent (owner_id, agent_id, create_time),
  INDEX idx_conversation_turn_status (status, memory_eligible),
  INDEX idx_conversation_turn_datasource (datasource_id),
  FOREIGN KEY (conversation_id) REFERENCES chat_session(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '对话轮次执行审计与可验证记忆事实源';

CREATE TABLE IF NOT EXISTS turn_run (
  run_id VARCHAR(36) NOT NULL COMMENT 'Graph运行ID',
  turn_id VARCHAR(36) NOT NULL COMMENT '逻辑轮次ID',
  attempt INT NOT NULL DEFAULT 1 COMMENT '当前尝试次数',
  status VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/RUNNING/WAITING_REVIEW/SUCCEEDED/FAILED/CANCELLED',
  error_message TEXT COMMENT '失败原因',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (run_id),
  INDEX idx_turn_run_turn (turn_id, attempt),
  INDEX idx_turn_run_status (status),
  FOREIGN KEY (turn_id) REFERENCES conversation_turn(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '对话轮次Graph运行表';

CREATE TABLE IF NOT EXISTS turn_artifact (
  id BIGINT NOT NULL AUTO_INCREMENT,
  turn_id VARCHAR(36) NOT NULL,
  run_id VARCHAR(36) NOT NULL,
  artifact_type VARCHAR(32) NOT NULL COMMENT 'PLAN/SQL/RESULT/REPORT/TIMELINE',
  content MEDIUMTEXT NOT NULL,
  content_hash VARCHAR(128) COMMENT '内容指纹',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_turn_artifact_type (turn_id, run_id, artifact_type),
  INDEX idx_turn_artifact_turn (turn_id),
  FOREIGN KEY (turn_id) REFERENCES conversation_turn(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '对话轮次执行产物表';

CREATE TABLE IF NOT EXISTS memory_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  scope_type VARCHAR(32) NOT NULL COMMENT 'USER_AGENT/AGENT/DATASOURCE',
  owner_id BIGINT COMMENT 'USER_AGENT作用域的可信用户ID',
  agent_id INT NOT NULL,
  datasource_id INT COMMENT 'DATASOURCE作用域的数据源ID',
  memory_kind VARCHAR(32) NOT NULL COMMENT 'PREFERENCE/CORRECTION/QUERY_PATTERN',
  memory_key VARCHAR(255) NOT NULL,
  value_json MEDIUMTEXT NOT NULL,
  identity_hash CHAR(64) NOT NULL COMMENT '作用域、类型和键组成的稳定身份哈希',
  active_identity_hash CHAR(64) COMMENT '仅CONFIRMED状态持有，用于保证单一有效值',
  source_turn_id VARCHAR(36) COMMENT '来源轮次',
  status VARCHAR(32) NOT NULL DEFAULT 'CANDIDATE' COMMENT 'CANDIDATE/CONFIRMED/SUPERSEDED/INVALIDATED',
  confidence DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
  schema_fingerprint VARCHAR(128),
  valid_until TIMESTAMP NULL,
  supersedes_id BIGINT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_memory_item_active_identity (active_identity_hash),
  INDEX idx_memory_item_scope (scope_type, owner_id, agent_id, datasource_id, status),
  INDEX idx_memory_item_key (memory_key),
  INDEX idx_memory_item_source_turn (source_turn_id),
  CONSTRAINT chk_memory_item_active_identity CHECK (
    (status = 'CONFIRMED' AND active_identity_hash IS NOT NULL AND active_identity_hash = identity_hash)
    OR (status <> 'CONFIRMED' AND active_identity_hash IS NULL)
  ),
  FOREIGN KEY (source_turn_id) REFERENCES conversation_turn(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '长期语义记忆表';

CREATE TABLE IF NOT EXISTS memory_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT,
  aggregate_type VARCHAR(32) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload MEDIUMTEXT,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DONE/FAILED',
  attempt_count INT NOT NULL DEFAULT 0,
  available_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  last_error TEXT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_memory_outbox_pending (status, available_at),
  INDEX idx_memory_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE = InnoDB COMMENT = '记忆投影Outbox表';
