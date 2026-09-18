package com.sky.service.impl;

import com.sky.service.RateLimitService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 固定窗口限流：Lua 脚本把 INCR 与首次设置过期时间放在 Redis 内原子执行，
 * 避免并发请求分别执行 INCR/EXPIRE 时出现永不过期的计数 Key。
 */
@Service
public class RedisRateLimitService implements RateLimitService {

    private static final String KEY_PREFIX = "yuexuan:rate-limit:";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "if current > tonumber(ARGV[2]) then return 0 end "
                    + "return 1",
            Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisRateLimitService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String businessKey, Long userId, int limit, int windowSeconds) {
        String redisKey = KEY_PREFIX + businessKey + ":" + userId;
        Long result = redisTemplate.execute(RATE_LIMIT_SCRIPT, Collections.singletonList(redisKey),
                String.valueOf(windowSeconds), String.valueOf(limit));
        return Long.valueOf(1L).equals(result);
    }
}
