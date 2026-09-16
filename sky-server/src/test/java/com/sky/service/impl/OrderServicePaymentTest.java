package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
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

    @InjectMocks
    private OrderServiceImpl orderService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ShoppingCartMapper shoppingCartMapper;

    @Mock
    private WebSocketServer webSocketServer;

    @Test
    void shouldIgnoreARepeatedPaymentNotificationForAnAlreadyPaidOrder() {
        Orders paidOrder = Orders.builder()
                .id(1001L)
                .number("202609150001")
                .userId(9L)
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .build();
        when(orderMapper.getByNumber(paidOrder.getNumber())).thenReturn(paidOrder);

        assertDoesNotThrow(() -> orderService.paySuccess(paidOrder.getNumber()));

        verify(orderMapper, never()).updateIfStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT));
        verify(shoppingCartMapper, never()).cleanByUserId(any());
        verify(webSocketServer, never()).sendToAllClient(any());
    }

    @Test
    void shouldProcessTheFirstPaymentNotificationOnce() {
        Orders unpaidOrder = Orders.builder()
                .id(1002L)
                .number("202609150002")
                .userId(10L)
                .status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID)
                .build();
        when(orderMapper.getByNumber(unpaidOrder.getNumber())).thenReturn(unpaidOrder);
        when(orderMapper.updateIfStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(1);

        orderService.paySuccess(unpaidOrder.getNumber());

        verify(shoppingCartMapper, never()).cleanByUserId(any());
        verify(webSocketServer).sendToAllClient(any());
    }

    @Test
    void shouldTreatAConcurrentSuccessfulUpdateAsADuplicateNotification() {
        Orders unpaidOrder = Orders.builder()
                .id(1003L)
                .number("202609150003")
                .userId(11L)
                .status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID)
                .build();
        Orders paidOrder = Orders.builder()
                .id(unpaidOrder.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .build();
        when(orderMapper.getByNumber(unpaidOrder.getNumber())).thenReturn(unpaidOrder);
        when(orderMapper.updateIfStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(0);
        when(orderMapper.getById(unpaidOrder.getId())).thenReturn(paidOrder);

        assertDoesNotThrow(() -> orderService.paySuccess(unpaidOrder.getNumber()));

        verify(shoppingCartMapper, never()).cleanByUserId(any());
        verify(webSocketServer, never()).sendToAllClient(any());
    }
}
