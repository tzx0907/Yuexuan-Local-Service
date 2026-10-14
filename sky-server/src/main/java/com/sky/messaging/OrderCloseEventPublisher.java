package com.sky.messaging;

import com.sky.config.RabbitMqConfig;
import com.sky.event.OrderCloseEvent;
import lombok.extern.slf4j.Slf4j;
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
        RabbitPublishConfirmAwaiter.sendAndAwait(rabbitTemplate, RabbitMqConfig.ORDER_EVENT_EXCHANGE,
                RabbitMqConfig.ORDER_CLOSE_DELAY_ROUTING_KEY, event, event.getEventId());
        log.info("订单超时关闭事件已获 RabbitMQ 确认并路由 eventId={}, orderId={}, closeAt={}",
                event.getEventId(), event.getOrderId(), event.getCloseAt());
    }
}
