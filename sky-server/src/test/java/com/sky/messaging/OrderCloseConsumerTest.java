package com.sky.messaging;

import com.rabbitmq.client.Channel;
import com.sky.event.OrderCloseEvent;
import com.sky.service.OrderTimeoutService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCloseConsumerTest {
    @Test
    void shouldAckAfterTheTimeoutServiceFinishes() throws Exception {
        OrderTimeoutService timeoutService = mock(OrderTimeoutService.class);
        Channel channel = mock(Channel.class);
        OrderCloseEvent event = OrderCloseEvent.builder().eventId("close-event-1").orderId(3001L).build();
        when(timeoutService.closeIfUnpaid(3001L, "rabbitmq")).thenReturn(true);

        new OrderCloseConsumer(timeoutService).onOrderClose(event, channel, 12L);

        verify(timeoutService).closeIfUnpaid(3001L, "rabbitmq");
        verify(channel).basicAck(12L, false);
    }
}
