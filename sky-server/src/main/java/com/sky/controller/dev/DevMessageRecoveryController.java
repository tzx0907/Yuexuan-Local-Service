package com.sky.controller.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.entity.OutboxEvent;
import com.sky.event.OrderPaidEvent;
import com.sky.mapper.OutboxEventMapper;
import com.sky.messaging.OrderPaidEventPublisher;
import com.sky.result.Result;
import com.sky.service.impl.OutboxServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地开发环境中用于演示和处理 ORDER_PAID 死信的受控重放入口。
 *
 * <p>死信队列保存失败现场，不会自动回到原队列。该入口根据 eventId 从 Outbox
 * 读取事务提交时保存的原始 payload，再投递到正常的 ORDER_PAID 路由。它不改变
 * Outbox 的 SENT 状态，也不会删除 DLQ 中的原始失败消息；验证成功后可由开发者在
 * RabbitMQ 管理台确认并删除该条死信。</p>
 */
@Slf4j
@RestController
@Profile("dev")
@ConditionalOnProperty(prefix = "sky.messaging.replay", name = "enabled", havingValue = "true")
@RequestMapping("/_dev/messaging")
public class DevMessageRecoveryController {
    private final OutboxEventMapper outboxEventMapper;
    private final OrderPaidEventPublisher orderPaidEventPublisher;
    private final ObjectMapper objectMapper;

    public DevMessageRecoveryController(OutboxEventMapper outboxEventMapper,
                                        OrderPaidEventPublisher orderPaidEventPublisher,
                                        ObjectMapper objectMapper) {
        this.outboxEventMapper = outboxEventMapper;
        this.orderPaidEventPublisher = orderPaidEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 将指定支付事件重新发布到正常订单支付队列。
     *
     * <p>仅在 dev profile 且 {@code sky.messaging.replay.enabled=true} 时注册。</p>
     */
    @PostMapping("/replay/order-paid/{eventId}")
    public Result<Map<String, Object>> replayOrderPaid(@PathVariable String eventId) {
        OutboxEvent event = outboxEventMapper.getByEventId(eventId);
        if (event == null) {
            throw new IllegalArgumentException("未找到 eventId 对应的 Outbox 事件: " + eventId);
        }
        if (!OutboxServiceImpl.ORDER_PAID.equals(event.getEventType())) {
            throw new IllegalArgumentException("该事件不是 ORDER_PAID，不能使用支付消息重放入口");
        }
        try {
            OrderPaidEvent orderPaidEvent = objectMapper.readValue(event.getPayload(), OrderPaidEvent.class);
            if (!eventId.equals(orderPaidEvent.getEventId())) {
                throw new IllegalArgumentException("Outbox payload 的 eventId 与查询参数不一致，已拒绝重放");
            }
            orderPaidEventPublisher.publish(orderPaidEvent);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("eventId", eventId);
            data.put("orderId", orderPaidEvent.getOrderId());
            data.put("message", "已重新投递到 order.paid；等待消费者处理。原 DLQ 消息保留用于核验，请在确认成功后手动删除。");
            log.info("开发环境已重放 ORDER_PAID 事件 eventId={}, orderId={}", eventId, orderPaidEvent.getOrderId());
            return Result.success(data);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("ORDER_PAID 事件反序列化或重放失败 eventId=" + eventId, ex);
        }
    }
}
