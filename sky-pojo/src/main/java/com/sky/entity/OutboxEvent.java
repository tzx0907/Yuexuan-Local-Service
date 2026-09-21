package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 与业务数据在同一事务中保存的待投递事件。
 * RabbitMQ 暂不可用或进程在提交后退出时，事件仍可由后台任务继续投递。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    private Long id;
    private String eventId;
    private String eventType;
    private String exchangeName;
    private String routingKey;
    private String payload;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextAttemptTime;
    private LocalDateTime createdTime;
    private LocalDateTime processingTime;
    private LocalDateTime sentTime;
    private String lastError;
}
