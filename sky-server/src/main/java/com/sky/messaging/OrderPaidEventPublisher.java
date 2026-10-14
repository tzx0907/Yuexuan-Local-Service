package com.sky.messaging;

import com.sky.config.RabbitMqConfig;
import com.sky.event.OrderPaidEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/** 发布订单支付事件，并等待 RabbitMQ 的生产者确认。 */
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
        RabbitPublishConfirmAwaiter.sendAndAwait(rabbitTemplate, RabbitMqConfig.ORDER_EVENT_EXCHANGE,
                RabbitMqConfig.ORDER_PAID_ROUTING_KEY, event, event.getEventId());
        log.info("ORDER_PAID 已获 RabbitMQ 确认并路由 eventId={}, orderId={}",
                event.getEventId(), event.getOrderId());
    }
}
