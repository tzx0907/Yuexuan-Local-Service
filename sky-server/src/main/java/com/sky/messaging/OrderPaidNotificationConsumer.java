package com.sky.messaging;

import com.rabbitmq.client.Channel;
import com.sky.config.RabbitMqConfig;
import com.sky.event.OrderPaidEvent;
import com.sky.mapper.ProcessedMessageMapper;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** 消费支付事件，向运营端发送新订单提醒。 */
@Component
@Slf4j
public class OrderPaidNotificationConsumer {
    private static final String CONSUMER_NAME = "admin-order-notifier";

    private final ProcessedMessageMapper processedMessageMapper;
    private final WebSocketServer webSocketServer;

    //依赖注入
    public OrderPaidNotificationConsumer(ProcessedMessageMapper processedMessageMapper,
                                         WebSocketServer webSocketServer) {
        this.processedMessageMapper = processedMessageMapper;
        this.webSocketServer = webSocketServer;
    }

    // 监听订单支付事件
    /**
     * 监听订单支付事件
     *
     * @param event          订单支付事件
     * @param channel        RabbitMQ 通道
     * @param deliveryTag    消息投递标签
     * @throws IOException   IO 异常
     */
    //指定队列和监听器容器
    @RabbitListener(queues = RabbitMqConfig.ORDER_PAID_QUEUE,
            containerFactory = "orderEventRabbitListenerContainerFactory")
    public void onOrderPaid(OrderPaidEvent event, Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        //查幂等表
        int inserted = processedMessageMapper.insertIgnore(event.getEventId(), CONSUMER_NAME, LocalDateTime.now());
        if (inserted == 0) {
            log.info("重复 ORDER_PAID 事件已跳过 eventId={}, orderId={}", event.getEventId(), event.getOrderId());
            channel.basicAck(deliveryTag, false);
            return;
        }
        //向运营端发送新订单提醒
        Map<String, Object> message = new HashMap<>();
        message.put("type", 1);
        message.put("orderId", event.getOrderId());
        message.put("content", "新订单：" + event.getOrderNumber());
        webSocketServer.sendToAllClient(com.alibaba.fastjson.JSON.toJSONString(message));
        channel.basicAck(deliveryTag, false);
        log.info("ORDER_PAID 事件消费完成并 ACK eventId={}, orderId={}", event.getEventId(), event.getOrderId());
    }
}
