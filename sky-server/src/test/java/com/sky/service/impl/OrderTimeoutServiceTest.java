package com.sky.service.impl;

import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ProductSkuMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTimeoutServiceTest {
    @InjectMocks
    private OrderTimeoutServiceImpl orderTimeoutService;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderDetailMapper orderDetailMapper;
    @Mock
    private DishMapper dishMapper;
    @Mock
    private ProductSkuMapper productSkuMapper;

    @Test
    void shouldCloseUnpaidOrderAndRestoreProductAndSkuStockOnce() {
        Orders pending = Orders.builder().id(2001L).status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID).build();
        when(orderMapper.getById(2001L)).thenReturn(pending);
        when(orderMapper.updateIfStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(1);
        when(orderDetailMapper.getOrderDetailByOrderId(2001L)).thenReturn(List.of(
                OrderDetail.builder().dishId(46L).number(2).build(),
                OrderDetail.builder().dishId(58L).skuId(5802L).number(1).build()));
        when(dishMapper.incrementStock(46L, 2)).thenReturn(1);
        when(productSkuMapper.incrementStock(5802L, 1)).thenReturn(1);

        assertTrue(orderTimeoutService.closeIfUnpaid(2001L, "test"));

        ArgumentCaptor<Orders> update = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).updateIfStatus(update.capture(), eq(Orders.PENDING_PAYMENT));
        assertTrue(update.getValue().getCancelReason().contains("支付超时"));
        verify(dishMapper).incrementStock(46L, 2);
        verify(productSkuMapper).incrementStock(5802L, 1);
    }

    @Test
    void shouldIgnoreDelayedMessageWhenOrderWasAlreadyPaid() {
        Orders paid = Orders.builder().id(2002L).status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID).build();
        when(orderMapper.getById(2002L)).thenReturn(paid);

        assertFalse(orderTimeoutService.closeIfUnpaid(2002L, "rabbitmq"));

        verify(orderMapper, never()).updateIfStatus(any(), any());
        verify(orderDetailMapper, never()).getOrderDetailByOrderId(any());
        verify(dishMapper, never()).incrementStock(any(), any());
        verify(productSkuMapper, never()).incrementStock(any(), any());
    }

    @Test
    void shouldNotRestoreStockWhenAnotherWorkerAlreadyClosedTheOrder() {
        Orders pending = Orders.builder().id(2003L).status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID).build();
        when(orderMapper.getById(2003L)).thenReturn(pending);
        when(orderMapper.updateIfStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(0);

        assertFalse(orderTimeoutService.closeIfUnpaid(2003L, "scheduled-fallback"));

        verify(orderDetailMapper, never()).getOrderDetailByOrderId(any());
        verify(dishMapper, never()).incrementStock(any(), any());
        verify(productSkuMapper, never()).incrementStock(any(), any());
    }
}
