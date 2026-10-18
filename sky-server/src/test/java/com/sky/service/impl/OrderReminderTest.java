package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReminderTest {
    @InjectMocks private OrderServiceImpl service;
    @Mock private OrderMapper orderMapper;
    @Mock private RedisTemplate redisTemplate;
    @Mock private ValueOperations values;
    @Mock private WebSocketServer webSocketServer;

    @BeforeEach void setUp() { BaseContext.setCurrentId(9L); }
    @AfterEach void tearDown() { BaseContext.removeCurrentId(); }

    @Test
    void repeatWithinWindowBroadcastsOnlyOnce() {
        when(orderMapper.getById(12L)).thenReturn(order(9L, Orders.TO_BE_CONFIRMED));
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("order:reminder:12", "SENT", 60, TimeUnit.SECONDS))
                .thenReturn(true, false);
        service.reminder(12L);
        OrderBusinessException ex = assertThrows(OrderBusinessException.class, () -> service.reminder(12L));
        assertEquals("已催单，请60秒后再试", ex.getMessage());
        verify(webSocketServer).sendToAllClient(anyString());
    }

    @Test
    void permitsAnotherReminderAfterWindowExpires() {
        when(orderMapper.getById(12L)).thenReturn(order(9L, Orders.TO_BE_CONFIRMED));
        when(redisTemplate.opsForValue()).thenReturn(values);
        // Redis 的 TTL 到期后重新获得 key，模拟下一个时间窗口。
        when(values.setIfAbsent("order:reminder:12", "SENT", 60, TimeUnit.SECONDS))
                .thenReturn(true, true);
        service.reminder(12L);
        service.reminder(12L);
        verify(webSocketServer, org.mockito.Mockito.times(2)).sendToAllClient(anyString());
    }

    @Test
    void rejectsAnotherUsersOrderBeforeAcquiringKey() {
        when(orderMapper.getById(12L)).thenReturn(order(10L, Orders.TO_BE_CONFIRMED));
        assertThrows(OrderBusinessException.class, () -> service.reminder(12L));
        verify(redisTemplate, never()).opsForValue();
        verify(webSocketServer, never()).sendToAllClient(anyString());
    }

    @Test
    void rejectsCancelledOrderBeforeAcquiringKey() {
        when(orderMapper.getById(12L)).thenReturn(order(9L, Orders.CANCELLED));
        assertThrows(OrderBusinessException.class, () -> service.reminder(12L));
        verify(redisTemplate, never()).opsForValue();
        verify(webSocketServer, never()).sendToAllClient(anyString());
    }

    @Test
    void failsClosedWhenRedisUnavailable() {
        when(orderMapper.getById(12L)).thenReturn(order(9L, Orders.TO_BE_CONFIRMED));
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("order:reminder:12", "SENT", 60, TimeUnit.SECONDS))
                .thenThrow(new IllegalStateException("Redis unavailable"));
        assertThrows(OrderBusinessException.class, () -> service.reminder(12L));
        verify(webSocketServer, never()).sendToAllClient(anyString());
    }

    private Orders order(Long userId, Integer status) {
        return Orders.builder().id(12L).userId(userId).status(status).build();
    }
}
