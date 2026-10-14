package com.sky.messaging;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 将一次 RabbitMQ 发布的“调用成功”提升为“Broker 已确认且消息已路由”。
 *
 * <p>{@link RabbitTemplate#convertAndSend} 返回只代表客户端已将发布请求交给 AMQP 客户端，
 * 不能据此将 Outbox 标记为 SENT。只有 correlated publisher confirm ACK 且没有 returned
 * message 时，调用方才可以认为这一次投递成功。</p>
 */
final class RabbitPublishConfirmAwaiter {
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private RabbitPublishConfirmAwaiter() {
    }

    static void sendAndAwait(RabbitTemplate rabbitTemplate, String exchange, String routingKey,
                             Object event, String eventId) {
        CorrelationData correlationData = new CorrelationData(eventId);
        rabbitTemplate.convertAndSend(exchange, routingKey, event, correlationData);
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                String reason = confirm == null ? "未返回确认结果" : confirm.getReason();
                throw new IllegalStateException("RabbitMQ 未确认消息 eventId=" + eventId + ", reason=" + reason);
            }
            if (correlationData.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ 无匹配队列，消息被退回 eventId=" + eventId
                        + ", exchange=" + exchange + ", routingKey=" + routingKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 RabbitMQ 确认被中断 eventId=" + eventId, e);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("等待 RabbitMQ 确认超时 eventId=" + eventId, e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("获取 RabbitMQ 确认失败 eventId=" + eventId, e);
        }
    }
}
