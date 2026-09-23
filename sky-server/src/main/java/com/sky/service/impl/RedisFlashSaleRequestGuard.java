package com.sky.service.impl;

import com.sky.service.FlashSaleRequestGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@Slf4j
public class RedisFlashSaleRequestGuard implements FlashSaleRequestGuard {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local c=redis.call('INCR',KEYS[1]); if c==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; if c>tonumber(ARGV[2]) then return 0; end; return 1",
            Long.class);
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisFlashSaleRequestGuard(RedisTemplate<String, Object> redisTemplate) { this.redisTemplate = redisTemplate; }

    @Override
    public boolean tryAcquire(Long activityId, Long userId) {
        String key = "yuexuan:flash-sale:request:" + activityId + ":" + userId;
        try {
            Long result = redisTemplate.execute(SCRIPT, Collections.singletonList(key), "3", "3");
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) {
            // Redis 是削峰层，不是库存真相；不可用时让 MySQL 的最终校验继续兜底。
            log.warn("限时购 Redis 削峰不可用，转由 MySQL 校验 activityId={}, userId={}", activityId, userId, ex);
            return true;
        }
    }
}
