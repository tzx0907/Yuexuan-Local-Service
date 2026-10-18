-- Outbox 重试策略：重试耗尽的事件进入 FAILED 终态；历史 SENT 事件由清理任务分批删除。
USE `yuexuan_local_service`;

ALTER TABLE outbox_event
    MODIFY COLUMN status VARCHAR(16) NOT NULL COMMENT 'PENDING / SENDING / RETRY / SENT / FAILED';