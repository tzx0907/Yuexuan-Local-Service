package com.sky.messaging;

import com.sky.config.RabbitMqConfig;
import com.sky.event.OrderCloseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** 将订单关闭检查事件送入固定 15 分钟 TTL 队列。 */
@Component
@Slf4j
public class OrderCloseEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public OrderCloseEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(OrderCloseEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.ORDER_EVENT_EXCHANGE,
                RabbitMqConfig.ORDER_CLOSE_DELAY_ROUTING_KEY, event,
                new CorrelationData(event.getEventId()));
        log.info("已投递订单超时关闭事件 eventId={}, orderId={}, closeAt={}",
                event.getEventId(), event.getOrderId(), event.getCloseAt());
    }
}
