-- Upgrade existing MySQL installations so chat messages can store 4-byte Unicode
-- characters such as emoji. Run this script against the DataAgent management
-- database. It is safe to run again: the current session foreign key is discovered,
-- replaced, and given a stable name.

SET NAMES utf8mb4;

SET @chat_message_session_fk = NULL;
SELECT CONSTRAINT_NAME
INTO @chat_message_session_fk
FROM information_schema.KEY_COLUMN_USAGE
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND TABLE_NAME = 'chat_message'
  AND COLUMN_NAME = 'session_id'
  AND REFERENCED_TABLE_NAME = 'chat_session'
  AND REFERENCED_COLUMN_NAME = 'id'
LIMIT 1;

SET @drop_chat_message_session_fk = IF(
  @chat_message_session_fk IS NULL,
  'SELECT 1',
  CONCAT(
    'ALTER TABLE `chat_message` DROP FOREIGN KEY `',
    REPLACE(@chat_message_session_fk, '`', '``'),
    '`'
  )
);

PREPARE drop_chat_message_session_fk_stmt FROM @drop_chat_message_session_fk;
EXECUTE drop_chat_message_session_fk_stmt;
DEALLOCATE PREPARE drop_chat_message_session_fk_stmt;

ALTER TABLE `chat_session`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `chat_message`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `chat_message`
  ADD CONSTRAINT `fk_chat_message_session`
  FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`)
  ON DELETE CASCADE;
