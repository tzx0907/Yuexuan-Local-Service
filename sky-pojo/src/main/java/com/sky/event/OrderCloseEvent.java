package com.sky.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 订单创建后用于触发超时检查的延迟事件。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCloseEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventId;
    private Long orderId;
    private String orderNumber;
    private LocalDateTime createdAt;
    private LocalDateTime closeAt;
}
