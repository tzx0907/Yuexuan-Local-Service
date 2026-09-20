package com.sky.messaging;

import com.rabbitmq.client.Channel;
import com.sky.config.RabbitMqConfig;
import com.sky.event.OrderCloseEvent;
import com.sky.service.OrderTimeoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 延迟消息到期后再次检查数据库状态，绝不无条件取消订单。 */
@Component
@Slf4j
public class OrderCloseConsumer {
    private final OrderTimeoutService orderTimeoutService;

    public OrderCloseConsumer(OrderTimeoutService orderTimeoutService) {
        this.orderTimeoutService = orderTimeoutService;
    }

    @RabbitListener(queues = RabbitMqConfig.ORDER_CLOSE_QUEUE,
            containerFactory = "orderEventRabbitListenerContainerFactory")
    public void onOrderClose(OrderCloseEvent event, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        boolean closed = orderTimeoutService.closeIfUnpaid(event.getOrderId(), "rabbitmq");
        channel.basicAck(deliveryTag, false);
        log.info("订单超时消息处理完成 eventId={}, orderId={}, closed={}",
                event.getEventId(), event.getOrderId(), closed);
    }
}
