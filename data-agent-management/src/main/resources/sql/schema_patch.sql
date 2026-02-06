-- ------------------------------
-- 补充模型规模字段
-- @see https://github.com/spring-ai-alibaba/DataAgent/issues/287
-- ------------------------------
START TRANSACTION;

ALTER TABLE `model_config`
    ADD COLUMN `model_tier` varchar(20) DEFAULT NULL COMMENT '模型规模，仅对话模型有效 (FLASH/STANDARD/THINKING)' AFTER `model_type`;

UPDATE `model_config` SET `model_tier` = 'STANDARD' WHERE `model_type` = 'CHAT';

COMMIT;