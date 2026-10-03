package com.sky.messaging;

import com.rabbitmq.client.Channel;
import com.sky.event.OrderPaidEvent;
import com.sky.mapper.ProcessedMessageMapper;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderPaidNotificationConsumerTest {

    @Test
    void shouldNotifyRecordAndAckWhenEventIsFirstConsumed() throws Exception {
        ProcessedMessageMapper mapper = mock(ProcessedMessageMapper.class);
        WebSocketServer webSocket = mock(WebSocketServer.class);
        Channel channel = mock(Channel.class);
        when(mapper.exists(eq("evt-1"), any())).thenReturn(0);
        when(webSocket.sendToAllAndCountFailures(any())).thenReturn(0);

        new OrderPaidNotificationConsumer(mapper, webSocket)
                .onOrderPaid(event("evt-1"), channel, 7L);

        verify(webSocket).sendToAllAndCountFailures(any());
        verify(mapper).insertIgnore(eq("evt-1"), any(), any());
        verify(channel).basicAck(7L, false);
    }

    @Test
    void shouldOnlyAckWhenRabbitMqRedeliversAnAlreadyConsumedEvent() throws Exception {
        ProcessedMessageMapper mapper = mock(ProcessedMessageMapper.class);
        WebSocketServer webSocket = mock(WebSocketServer.class);
        Channel channel = mock(Channel.class);
        when(mapper.exists(eq("evt-2"), any())).thenReturn(1);

        new OrderPaidNotificationConsumer(mapper, webSocket)
                .onOrderPaid(event("evt-2"), channel, 8L);

        verify(webSocket, never()).sendToAllAndCountFailures(any());
        verify(mapper, never()).insertIgnore(any(), any(), any());
        verify(channel).basicAck(8L, false);
    }

    @Test
    void shouldNotRecordOrAckWhenWebSocketBroadcastFails() throws Exception {
        ProcessedMessageMapper mapper = mock(ProcessedMessageMapper.class);
        WebSocketServer webSocket = mock(WebSocketServer.class);
        Channel channel = mock(Channel.class);
        when(mapper.exists(eq("evt-3"), any())).thenReturn(0);
        when(webSocket.sendToAllAndCountFailures(any())).thenReturn(1);

        OrderPaidNotificationConsumer consumer = new OrderPaidNotificationConsumer(mapper, webSocket);

        assertThrows(IllegalStateException.class, () -> consumer.onOrderPaid(event("evt-3"), channel, 9L));

        verify(mapper, never()).insertIgnore(any(), any(), any());
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    private OrderPaidEvent event(String eventId) {
        return OrderPaidEvent.builder().eventId(eventId).orderId(1L).orderNumber("20260919001")
                .userId(9L).amount(BigDecimal.TEN).paidAt(LocalDateTime.now()).build();
    }
}
