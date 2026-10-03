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
        // 前置查询挡住 ACK 丢失后的重投，避免重复提醒（此时推送还没执行过）
        if (processedMessageMapper.exists(event.getEventId(), CONSUMER_NAME) > 0) {
            log.info("重复 ORDER_PAID 事件已跳过 eventId={}, orderId={}", event.getEventId(), event.getOrderId());
            channel.basicAck(deliveryTag, false);
            return;
        }
        //向运营端发送新订单提醒；先推送、后落幂等标记，崩溃窗口内由重投补偿
        Map<String, Object> message = new HashMap<>();
        message.put("type", 1);
        message.put("orderId", event.getOrderId());
        message.put("content", "新订单：" + event.getOrderNumber());
        int failed = webSocketServer.sendToAllAndCountFailures(com.alibaba.fastjson.JSON.toJSONString(message));
        if (failed > 0) {
            // 不落标记、不 ACK：让 RabbitMQ 重投，重试耗尽后进 DLQ 供人工重放
            throw new IllegalStateException("WebSocket 广播失败 " + failed + " 个会话，等待重投 eventId="
                    + event.getEventId());
        }
        // 推送成功后才写完成标记，避免“标记已写但提醒没发出去”
        processedMessageMapper.insertIgnore(event.getEventId(), CONSUMER_NAME, LocalDateTime.now());
        channel.basicAck(deliveryTag, false);
        log.info("ORDER_PAID 事件消费完成并 ACK eventId={}, orderId={}", event.getEventId(), event.getOrderId());
    }
}
