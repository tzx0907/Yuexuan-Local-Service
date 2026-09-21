package com.sky.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.entity.OutboxEvent;
import com.sky.event.OrderCloseEvent;
import com.sky.event.OrderPaidEvent;
import com.sky.mapper.OutboxEventMapper;
import com.sky.messaging.OrderCloseEventPublisher;
import com.sky.messaging.OrderPaidEventPublisher;
import com.sky.service.impl.OutboxServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 将数据库 Outbox 中已提交的事件投递到 RabbitMQ。 */
@Component
@Slf4j
public class OutboxDispatchTask {
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ERROR_LENGTH = 500;
    private final OutboxEventMapper outboxEventMapper;
    private final OrderPaidEventPublisher orderPaidEventPublisher;
    private final OrderCloseEventPublisher orderCloseEventPublisher;
    private final ObjectMapper objectMapper;

    public OutboxDispatchTask(OutboxEventMapper outboxEventMapper,
                              OrderPaidEventPublisher orderPaidEventPublisher,
                              OrderCloseEventPublisher orderCloseEventPublisher,
                              ObjectMapper objectMapper) {
        this.outboxEventMapper = outboxEventMapper;
        this.orderPaidEventPublisher = orderPaidEventPublisher;
        this.orderCloseEventPublisher = orderCloseEventPublisher;
        this.objectMapper = objectMapper;
    }

    /** 每 10 秒扫描一次；每条先抢占，多个应用实例同时运行也只会由一个实例发送。 */
    @Scheduled(cron = "*/10 * * * * ?")
    public void dispatchPendingEvents() {
        outboxEventMapper.recoverStaleSending(LocalDateTime.now().minusMinutes(5));
        List<OutboxEvent> events = outboxEventMapper.findSendable(BATCH_SIZE);
        for (OutboxEvent event : events) {
            if (outboxEventMapper.claim(event.getId()) != 1) {
                continue;
            }
            try {
                dispatch(event);
                outboxEventMapper.markSent(event.getId());//如果数据库没有更新成功，则说明有其他实例已经投递成功或者后续会重试
                log.info("Outbox 事件已投递 eventId={}, type={}, id={}",
                        event.getEventId(), event.getEventType(), event.getId());
            } catch (Exception e) {
                int nextRetry = event.getRetryCount() + 1;
                long delaySeconds = Math.min(300L, 1L << Math.min(nextRetry, 8));
                String message = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
                outboxEventMapper.markRetry(event.getId(), LocalDateTime.now().plusSeconds(delaySeconds),
                        message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message);
                log.warn("Outbox 投递失败，将重试 eventId={}, type={}, retry={}, delaySeconds={}",
                        event.getEventId(), event.getEventType(), nextRetry, delaySeconds, e);
            }
        }
    }

    private void dispatch(OutboxEvent event) throws Exception {
        if (OutboxServiceImpl.ORDER_PAID.equals(event.getEventType())) {
            orderPaidEventPublisher.publish(objectMapper.readValue(event.getPayload(), OrderPaidEvent.class));
            return;
        }
        if (OutboxServiceImpl.ORDER_CLOSE.equals(event.getEventType())) {
            orderCloseEventPublisher.publish(objectMapper.readValue(event.getPayload(), OrderCloseEvent.class));
            return;
        }
        throw new IllegalArgumentException("不支持的 Outbox 事件类型: " + event.getEventType());
    }
}
