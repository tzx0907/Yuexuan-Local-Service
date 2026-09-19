package com.sky.messaging;

import com.rabbitmq.client.Channel;
import com.sky.event.OrderPaidEvent;
import com.sky.mapper.ProcessedMessageMapper;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderPaidNotificationConsumerTest {

    @Test
    void shouldNotifyAndAckWhenEventIsFirstConsumed() throws Exception {
        ProcessedMessageMapper mapper = mock(ProcessedMessageMapper.class);
        WebSocketServer webSocket = mock(WebSocketServer.class);
        Channel channel = mock(Channel.class);
        when(mapper.insertIgnore(eq("evt-1"), any(), any())).thenReturn(1);

        new OrderPaidNotificationConsumer(mapper, webSocket)
                .onOrderPaid(event("evt-1"), channel, 7L);

        verify(webSocket).sendToAllClient(any());
        verify(channel).basicAck(7L, false);
    }

    @Test
    void shouldOnlyAckWhenRabbitMqRedeliversAnAlreadyConsumedEvent() throws Exception {
        ProcessedMessageMapper mapper = mock(ProcessedMessageMapper.class);
        WebSocketServer webSocket = mock(WebSocketServer.class);
        Channel channel = mock(Channel.class);
        when(mapper.insertIgnore(eq("evt-2"), any(), any())).thenReturn(0);

        new OrderPaidNotificationConsumer(mapper, webSocket)
                .onOrderPaid(event("evt-2"), channel, 8L);

        verify(webSocket, never()).sendToAllClient(any());
        verify(channel).basicAck(8L, false);
    }

    private OrderPaidEvent event(String eventId) {
        return OrderPaidEvent.builder().eventId(eventId).orderId(1L).orderNumber("20260919001")
                .userId(9L).amount(BigDecimal.TEN).paidAt(LocalDateTime.now()).build();
    }
}
