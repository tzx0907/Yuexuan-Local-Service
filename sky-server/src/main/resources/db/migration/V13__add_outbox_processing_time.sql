-- V12 已执行的数据库补充发送抢占时间，避免按创建时间误判正在发送的旧事件。
USE `yuexuan_local_service`;

DROP PROCEDURE IF EXISTS add_outbox_processing_time;
DELIMITER //
CREATE PROCEDURE add_outbox_processing_time()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'outbox_event' AND column_name = 'processing_time'
    ) THEN
        ALTER TABLE outbox_event ADD COLUMN processing_time DATETIME DEFAULT NULL COMMENT '本次开始投递时间'
            AFTER created_time;
    END IF;
END //
DELIMITER ;
CALL add_outbox_processing_time();
DROP PROCEDURE add_outbox_processing_time;
