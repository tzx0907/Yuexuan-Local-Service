package com.sky.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.config.RabbitMqConfig;
import com.sky.entity.OutboxEvent;
import com.sky.event.OrderCloseEvent;
import com.sky.event.OrderPaidEvent;
import com.sky.mapper.OutboxEventMapper;
import com.sky.service.OutboxService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OutboxServiceImpl implements OutboxService {
    public static final String ORDER_PAID = "ORDER_PAID";
    public static final String ORDER_CLOSE = "ORDER_CLOSE";

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    public OutboxServiceImpl(OutboxEventMapper outboxEventMapper, ObjectMapper objectMapper) {
        this.outboxEventMapper = outboxEventMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveOrderPaidEvent(Object event) {
        OrderPaidEvent orderPaidEvent = (OrderPaidEvent) event;
        save(orderPaidEvent.getEventId(), ORDER_PAID, RabbitMqConfig.ORDER_PAID_ROUTING_KEY, orderPaidEvent);
    }

    @Override
    public void saveOrderCloseEvent(Object event) {
        OrderCloseEvent orderCloseEvent = (OrderCloseEvent) event;
        save(orderCloseEvent.getEventId(), ORDER_CLOSE, RabbitMqConfig.ORDER_CLOSE_DELAY_ROUTING_KEY, orderCloseEvent);
    }

    private void save(String eventId, String eventType, String routingKey, Object event) {
        try {
            outboxEventMapper.insert(OutboxEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .exchangeName(RabbitMqConfig.ORDER_EVENT_EXCHANGE)
                    .routingKey(routingKey)
                    .payload(objectMapper.writeValueAsString(event))
                    .nextAttemptTime(LocalDateTime.now())
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox 事件序列化失败", e);
        }
    }
}
