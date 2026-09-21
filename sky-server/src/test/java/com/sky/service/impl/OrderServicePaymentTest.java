package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.OutboxService;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServicePaymentTest {
    @InjectMocks private OrderServiceImpl orderService;
    @Mock private OrderMapper orderMapper;
    @Mock private ShoppingCartMapper shoppingCartMapper;
    @Mock private WebSocketServer webSocketServer;
    @Mock private OutboxService outboxService;

    @Test
    void shouldIgnoreARepeatedPaymentNotificationForAnAlreadyPaidOrder() {
        Orders paid = Orders.builder().id(1001L).number("202609150001").userId(9L)
                .status(Orders.TO_BE_CONFIRMED).payStatus(Orders.PAID).build();
        when(orderMapper.getByNumber(paid.getNumber())).thenReturn(paid);

        assertDoesNotThrow(() -> orderService.paySuccess(paid.getNumber()));

        verify(orderMapper, never()).updateIfStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT));
        verify(outboxService, never()).saveOrderPaidEvent(any());
    }

    @Test
    void shouldPersistOrderPaidEventWithThePaymentTransaction() {
        Orders unpaid = Orders.builder().id(1002L).number("202609150002").userId(10L)
                .amount(new java.math.BigDecimal("18.80"))
                .status(Orders.PENDING_PAYMENT).payStatus(Orders.UN_PAID).build();
        when(orderMapper.getByNumber(unpaid.getNumber())).thenReturn(unpaid);
        when(orderMapper.updateIfStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(1);

        orderService.paySuccess(unpaid.getNumber());

        verify(outboxService).saveOrderPaidEvent(any());
        verify(shoppingCartMapper, never()).cleanByUserId(any());
        verify(webSocketServer, never()).sendToAllClient(any());
    }

    @Test
    void shouldTreatAConcurrentSuccessfulUpdateAsADuplicateNotification() {
        Orders unpaid = Orders.builder().id(1003L).number("202609150003").userId(11L)
                .status(Orders.PENDING_PAYMENT).payStatus(Orders.UN_PAID).build();
        Orders paid = Orders.builder().id(1003L).status(Orders.TO_BE_CONFIRMED).payStatus(Orders.PAID).build();
        when(orderMapper.getByNumber(unpaid.getNumber())).thenReturn(unpaid);
        when(orderMapper.updateIfStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(0);
        when(orderMapper.getById(unpaid.getId())).thenReturn(paid);

        assertDoesNotThrow(() -> orderService.paySuccess(unpaid.getNumber()));

        verify(outboxService, never()).saveOrderPaidEvent(any());
    }
}
