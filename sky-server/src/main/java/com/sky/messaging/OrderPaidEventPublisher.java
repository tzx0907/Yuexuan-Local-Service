package com.sky.messaging;

import com.sky.config.RabbitMqConfig;
import com.sky.event.OrderPaidEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/** 仅负责发布；阶段 C 将以 Outbox 投递器替换此处的直接发布。 */
@Component
@Slf4j
public class OrderPaidEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    //RabbitTemplate ：Spring AMQP 提供的 MQ 发送模板，生产者核心 API
    public OrderPaidEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostConstruct
    void registerCallbacks() {
        // 无匹配队列时将消息退回发布端，交由 ReturnsCallback 记录，而不是静默丢弃。
        rabbitTemplate.setMandatory(true);//强制消息路由
        //MQ 有没有收到消息（交换机层面确认）
        rabbitTemplate.setConfirmCallback((correlation, ack, cause) -> {
            String eventId = correlation == null ? "unknown" : correlation.getId();
            if (ack) {
                log.info("RabbitMQ 确认收到 ORDER_PAID 事件 eventId={}", eventId);
            } else {
                log.error("RabbitMQ 未确认 ORDER_PAID 事件 eventId={}, cause={}", eventId, cause);
            }
        });
        //消息退回处理（无匹配队列时）
        rabbitTemplate.setReturnsCallback(returned -> log.error(
                "ORDER_PAID 路由失败 eventId={}, exchange={}, routingKey={}, reply={}",
                returned.getMessage().getMessageProperties().getCorrelationId(), returned.getExchange(),
                returned.getRoutingKey(), returned.getReplyText()));
    }

    public void publish(OrderPaidEvent event) {
        CorrelationData correlationData = new CorrelationData(event.getEventId());
        rabbitTemplate.convertAndSend(RabbitMqConfig.ORDER_EVENT_EXCHANGE,
                RabbitMqConfig.ORDER_PAID_ROUTING_KEY, event, correlationData);
        log.info("已投递 ORDER_PAID 事件，等待 RabbitMQ Confirm eventId={}, orderId={}",
                event.getEventId(), event.getOrderId());
    }
}
