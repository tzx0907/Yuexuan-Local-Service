package com.sky.messaging;

import com.rabbitmq.client.Channel;
import com.sky.config.RabbitMqConfig;
import com.sky.event.OrderPaidEvent;
import com.sky.mapper.ProcessedMessageMapper;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
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

    /** 仅用于本地 dev 环境演示“消费失败 -> 重试 -> DLQ”，默认关闭。 */
    @Value("${sky.messaging.fault-injection.order-paid-consumer-fail:false}")
    private boolean forceConsumerFailure;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    //依赖注入
    public OrderPaidNotificationConsumer(ProcessedMessageMapper processedMessageMapper,
                                         WebSocketServer webSocketServer) {
        this.processedMessageMapper = processedMessageMapper;
        this.webSocketServer = webSocketServer;
    }

    // 监听订单支付事件
    /**
     * 消费失败直接向上抛，由 {@code orderEventRabbitListenerContainerFactory} 统一完成
     * 3 次退避重试与 {@code basicNack(requeue=false)} 转 DLQ。
     *
     * <p>本方法刻意不吞异常：一旦吞掉，容器就感知不到失败，消息会被静默 ACK，
     * 容量与死信统计也会失真。</p>
     */
    @RabbitListener(queues = RabbitMqConfig.ORDER_PAID_QUEUE,
            containerFactory = "orderEventRabbitListenerContainerFactory")
    public void onOrderPaid(OrderPaidEvent event, Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        consumeOnce(event);
        channel.basicAck(deliveryTag, false);
        log.info("ORDER_PAID 事件消费完成并 ACK eventId={}, orderId={}",
                event.getEventId(), event.getOrderId());
    }

    /** 每次容器重试都会重新进入本方法；幂等标记只在副作用成功之后才写。 */
    private void consumeOnce(OrderPaidEvent event) {
        if (forceConsumerFailure && activeProfiles.contains("dev")) {
            throw new IllegalStateException("dev 故障注入：强制 ORDER_PAID 消费失败 eventId=" + event.getEventId());
        }
        // 前置查询挡住 ACK 丢失后的重投，避免重复提醒（此时推送还没执行过）
        if (processedMessageMapper.exists(event.getEventId(), CONSUMER_NAME) > 0) {
            log.info("重复 ORDER_PAID 事件已跳过 eventId={}, orderId={}", event.getEventId(), event.getOrderId());
            return;
        }
        //向运营端发送新订单提醒；先推送、后落幂等标记，崩溃窗口内由重投补偿
        Map<String, Object> message = new HashMap<>();
        message.put("type", 1);
        message.put("orderId", event.getOrderId());
        message.put("content", "新订单：" + event.getOrderNumber());
        int failed = webSocketServer.sendToAllAndCountFailures(com.alibaba.fastjson.JSON.toJSONString(message));
        if (failed > 0) {
            // 不落幂等标记；由容器重试，耗尽后进入 DLQ 供人工重放。
            log.warn("ORDER_PAID 广播失败待容器重试 eventId={}, orderId={}, failedSessions={}",
                    event.getEventId(), event.getOrderId(), failed);
            throw new IllegalStateException("WebSocket 广播失败 " + failed + " 个会话，等待重试 eventId="
                    + event.getEventId());
        }
        // 推送成功后才写完成标记，避免"标记已写但提醒没发出去"
        processedMessageMapper.insertIgnore(event.getEventId(), CONSUMER_NAME, LocalDateTime.now());
    }
}
