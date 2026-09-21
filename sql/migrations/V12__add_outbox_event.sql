-- Outbox：与订单状态更新同一事务写入，后台任务再可靠投递至 RabbitMQ。
USE `Yuexuan-Local-Service`;

CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '领域事件唯一标识',
    event_type VARCHAR(32) NOT NULL COMMENT 'ORDER_PAID / ORDER_CLOSE',
    exchange_name VARCHAR(128) NOT NULL COMMENT 'RabbitMQ 交换机',
    routing_key VARCHAR(128) NOT NULL COMMENT 'RabbitMQ 路由键',
    payload LONGTEXT NOT NULL COMMENT 'JSON 事件正文',
    status VARCHAR(16) NOT NULL COMMENT 'PENDING / SENDING / RETRY / SENT',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '投递失败次数',
    next_attempt_time DATETIME NOT NULL COMMENT '下一次允许投递的时间',
    created_time DATETIME NOT NULL COMMENT '创建时间',
    processing_time DATETIME DEFAULT NULL COMMENT '本次开始投递时间',
    sent_time DATETIME DEFAULT NULL COMMENT '确认投递时间',
    last_error VARCHAR(500) DEFAULT NULL COMMENT '最近一次失败原因',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_dispatch (status, next_attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RabbitMQ 可靠投递事件表';
