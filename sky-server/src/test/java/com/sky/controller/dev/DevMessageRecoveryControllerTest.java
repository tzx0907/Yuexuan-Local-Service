package com.sky.controller.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.entity.OutboxEvent;
import com.sky.event.OrderPaidEvent;
import com.sky.mapper.OutboxEventMapper;
import com.sky.messaging.OrderPaidEventPublisher;
import com.sky.result.Result;
import com.sky.service.impl.OutboxServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevMessageRecoveryControllerTest {

    @Test
    void shouldRepublishOriginalOrderPaidEventByEventId() throws Exception {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        OrderPaidEventPublisher publisher = mock(OrderPaidEventPublisher.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OrderPaidEvent expected = event("evt-paid-1");
        when(mapper.getByEventId("evt-paid-1")).thenReturn(OutboxEvent.builder()
                .eventId("evt-paid-1").eventType(OutboxServiceImpl.ORDER_PAID)
                .payload(objectMapper.writeValueAsString(expected)).build());

        DevMessageRecoveryController controller = new DevMessageRecoveryController(mapper, publisher, objectMapper);
        Result<Map<String, Object>> result = controller.replayOrderPaid("evt-paid-1");

        assertEquals(1, result.getCode());
        assertEquals(7L, result.getData().get("orderId"));
        verify(publisher).publish(eq(expected));
    }

    @Test
    void shouldRejectMissingOrWrongTypeEventWithoutPublishing() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        OrderPaidEventPublisher publisher = mock(OrderPaidEventPublisher.class);
        DevMessageRecoveryController controller = new DevMessageRecoveryController(mapper, publisher,
                new ObjectMapper().findAndRegisterModules());

        assertThrows(IllegalArgumentException.class, () -> controller.replayOrderPaid("missing"));
        when(mapper.getByEventId("evt-close")).thenReturn(OutboxEvent.builder()
                .eventId("evt-close").eventType(OutboxServiceImpl.ORDER_CLOSE).build());
        assertThrows(IllegalArgumentException.class, () -> controller.replayOrderPaid("evt-close"));
        verify(publisher, never()).publish(any());
    }

    private OrderPaidEvent event(String eventId) {
        return OrderPaidEvent.builder().eventId(eventId).orderId(7L).orderNumber("20261018001")
                .userId(9L).amount(BigDecimal.TEN).paidAt(LocalDateTime.now()).build();
    }
}
