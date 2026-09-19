package com.sky.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付成功后发布的领域事件。
 *
 * <p>事件只携带消费者完成非核心动作所需的订单快照，避免消费者再次依赖
 * 支付请求上下文。eventId 是消息消费幂等键，不使用 RabbitMQ 的 deliveryTag。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventId;
    private Long orderId;
    private String orderNumber;
    private Long userId;
    private BigDecimal amount;
    private LocalDateTime paidAt;
}
