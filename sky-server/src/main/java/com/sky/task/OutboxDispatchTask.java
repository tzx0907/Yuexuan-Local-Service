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
    static final int BATCH_SIZE = 50;
    /** 单轮最多连续处理 10 批（500 条），积压时在同一个 10 秒窗口内快速排空。 */
    static final int MAX_BATCHES_PER_RUN = 10;
    /** 最多重试 20 次后进入 FAILED 终态，避免坏消息无限重试。 */
    static final int MAX_RETRY = 20;
    static final long MAX_BACKOFF_SECONDS = 300L;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final long STALE_SENDING_MINUTES = 5L;
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
        outboxEventMapper.recoverStaleSending(LocalDateTime.now().minusMinutes(STALE_SENDING_MINUTES));
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<OutboxEvent> events = outboxEventMapper.findSendable(BATCH_SIZE);
            if (events.isEmpty()) {
                return;
            }
            events.forEach(this::dispatchOne);
            if (events.size() < BATCH_SIZE) {
                return;
            }
        }
        log.warn("Outbox 单轮投递达到批次上限，事件可能积压 maxEventsPerRun={}", MAX_BATCHES_PER_RUN * BATCH_SIZE);
    }

    private void dispatchOne(OutboxEvent event) {
        if (outboxEventMapper.claim(event.getId()) != 1) {
            return;
        }
        try {
            dispatch(event);
            outboxEventMapper.markSent(event.getId());//如果数据库没有更新成功，则说明有其他实例已经投递成功或者后续会重试
            log.info("Outbox 事件已投递 eventId={}, type={}, id={}",
                    event.getEventId(), event.getEventType(), event.getId());
        } catch (Exception e) {
            int nextRetry = event.getRetryCount() + 1;
            String message = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            String lastError = message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
            if (nextRetry > MAX_RETRY) {
                outboxEventMapper.markFailed(event.getId(), lastError);
                log.error("Outbox 投递重试次数耗尽，标记 FAILED 等待人工处理 eventId={}, type={}, retry={}",
                        event.getEventId(), event.getEventType(), nextRetry, e);
                return;
            }
            long delaySeconds = backoffSeconds(nextRetry);
            outboxEventMapper.markRetry(event.getId(), LocalDateTime.now().plusSeconds(delaySeconds), lastError);
            log.warn("Outbox 投递失败，将重试 eventId={}, type={}, retry={}, delaySeconds={}",
                    event.getEventId(), event.getEventType(), nextRetry, delaySeconds, e);
        }
    }

    /** 指数退避：2、4、8、16、32、64、128、256、300 秒封顶。 */
    static long backoffSeconds(int nextRetry) {
        return Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(nextRetry, 9));
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
