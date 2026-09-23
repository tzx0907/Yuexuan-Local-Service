package com.sky.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisFlashSaleRequestGuardTest {
    @Mock private RedisTemplate<String, Object> redisTemplate;

    @Test
    void shouldAllowWhenLuaReturnsOneAndUseActivityUserKey() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(1L);
        assertTrue(new RedisFlashSaleRequestGuard(redisTemplate).tryAcquire(12L, 30L));
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), org.mockito.ArgumentMatchers.<Object[]>any());
        assertEquals("yuexuan:flash-sale:request:12:30", keys.getValue().get(0));
    }

    @Test
    void shouldRejectWhenLuaReturnsZero() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(0L);
        assertFalse(new RedisFlashSaleRequestGuard(redisTemplate).tryAcquire(12L, 30L));
    }

    @Test
    void shouldFailOpenWhenRedisThrowsBecauseMysqlIsFinalAuthority() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenThrow(new RuntimeException("Redis unavailable"));
        assertTrue(new RedisFlashSaleRequestGuard(redisTemplate).tryAcquire(12L, 30L));
    }
}
