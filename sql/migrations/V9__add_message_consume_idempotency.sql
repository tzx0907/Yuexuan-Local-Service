-- RabbitMQ 消费幂等记录：同一 eventId 被同一业务消费者最多处理一次。
CREATE TABLE IF NOT EXISTS processed_message (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '领域事件唯一标识',
    consumer_name VARCHAR(64) NOT NULL COMMENT '消费者名称',
    processed_at DATETIME NOT NULL COMMENT '首次处理时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_processed_message_event_consumer (event_id, consumer_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息消费幂等记录';
