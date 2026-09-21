package com.sky.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.entity.OutboxEvent;
import com.sky.event.OrderPaidEvent;
import com.sky.mapper.OutboxEventMapper;
import com.sky.messaging.OrderCloseEventPublisher;
import com.sky.messaging.OrderPaidEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxDispatchTaskTest {
    @Mock private OutboxEventMapper outboxEventMapper;
    @Mock private OrderPaidEventPublisher orderPaidEventPublisher;
    @Mock private OrderCloseEventPublisher orderCloseEventPublisher;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private OutboxDispatchTask outboxDispatchTask;

    @Test
    void shouldMarkEventSentAfterRabbitMqAcceptsIt() throws Exception {
        OutboxEvent outbox = OutboxEvent.builder().id(1L).eventId("paid-1")
                .eventType("ORDER_PAID").payload("{}").retryCount(0).build();
        OrderPaidEvent event = OrderPaidEvent.builder().eventId("paid-1").orderId(101L)
                .amount(BigDecimal.TEN).paidAt(LocalDateTime.now()).build();
        when(outboxEventMapper.findSendable(50)).thenReturn(List.of(outbox));
        when(outboxEventMapper.claim(1L)).thenReturn(1);
        when(objectMapper.readValue("{}", OrderPaidEvent.class)).thenReturn(event);

        outboxDispatchTask.dispatchPendingEvents();

        verify(orderPaidEventPublisher).publish(event);
        verify(outboxEventMapper).markSent(1L);
        verify(outboxEventMapper, never()).markRetry(eq(1L), any(), any());
    }

    @Test
    void shouldScheduleRetryWhenRabbitMqPublishFails() throws Exception {
        OutboxEvent outbox = OutboxEvent.builder().id(2L).eventId("paid-2")
                .eventType("ORDER_PAID").payload("{}").retryCount(1).build();
        OrderPaidEvent event = OrderPaidEvent.builder().eventId("paid-2").orderId(102L).build();
        when(outboxEventMapper.findSendable(50)).thenReturn(List.of(outbox));
        when(outboxEventMapper.claim(2L)).thenReturn(1);
        when(objectMapper.readValue("{}", OrderPaidEvent.class)).thenReturn(event);
        doThrow(new RuntimeException("RabbitMQ unavailable")).when(orderPaidEventPublisher).publish(event);

        outboxDispatchTask.dispatchPendingEvents();

        verify(outboxEventMapper, never()).markSent(anyLong());
        verify(outboxEventMapper).markRetry(eq(2L), any(LocalDateTime.class), eq("RuntimeException: RabbitMQ unavailable"));
    }
}
