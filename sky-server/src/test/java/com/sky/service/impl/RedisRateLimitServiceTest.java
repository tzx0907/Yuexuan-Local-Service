package com.sky.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void shouldAllowRequestWhenLuaReturnsOne() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(1L);

        boolean allowed = new RedisRateLimitService(redisTemplate)
                .tryAcquire("order:submit", 1001L, 5, 60);

        assertTrue(allowed);
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), org.mockito.ArgumentMatchers.<Object[]>any());
        org.junit.jupiter.api.Assertions.assertEquals("yuexuan:rate-limit:order:submit:1001", keys.getValue().get(0));
    }

    @Test
    void shouldRejectRequestWhenLuaReturnsZero() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(0L);

        boolean allowed = new RedisRateLimitService(redisTemplate)
                .tryAcquire("order:submit", 1001L, 5, 60);

        assertFalse(allowed);
    }
}
