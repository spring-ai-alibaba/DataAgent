-- Versioned MySQL upgrade for schema-publication fencing and outbox worker leases.
-- Apply this file once after V20260729_01__create_durable_memory.sql.

ALTER TABLE datasource
  ADD COLUMN schema_revision CHAR(64) NULL COMMENT '最近一次完整Schema索引的稳定版本' AFTER description,
  ADD COLUMN schema_generation BIGINT NOT NULL DEFAULT 0 COMMENT 'Schema索引输入的单调代次'
    AFTER schema_revision;

ALTER TABLE memory_outbox
  MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    COMMENT 'PENDING/PROCESSING/DONE/FAILED/DEAD',
  ADD COLUMN lease_token VARCHAR(36) NULL COMMENT '当前投影工作者的唯一租约' AFTER attempt_count;
