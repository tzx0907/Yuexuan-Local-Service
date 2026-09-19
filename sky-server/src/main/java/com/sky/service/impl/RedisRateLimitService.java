package com.sky.service.impl;

import com.sky.service.RateLimitService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

/**
 * 固定窗口限流：Lua 脚本把 INCR 与首次设置过期时间放在 Redis 内原子执行，
 * 避免并发请求分别执行 INCR/EXPIRE 时出现永不过期的计数 Key。
 */
@Service
@Slf4j
public class RedisRateLimitService implements RateLimitService {

    private static final String KEY_PREFIX = "yuexuan:rate-limit:";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            // A previous development build used the same Redis instance while
            // storing non-counter values.  INCR would then throw and turn an
            // ordinary order request into HTTP 500.  Treat a non-numeric
            // legacy value as an expired counter and start a fresh window.
            "local raw = redis.call('GET', KEYS[1]) "
                    + "if raw and tonumber(raw) == nil then redis.call('DEL', KEYS[1]) end "
                    + "local current = redis.call('INCR', KEYS[1]) "
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
        try {
            Long result = redisTemplate.execute(RATE_LIMIT_SCRIPT, Collections.singletonList(redisKey),
                    String.valueOf(windowSeconds), String.valueOf(limit));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) {
            // The submit flow already has request idempotency and stock CAS.
            // A malformed/stale Redis rate-limit entry must not turn a valid
            // order into a 500 response. Remove only this user's counter and
            // allow this one request; the next call recreates its window.
            log.warn("限流计数异常，已跳过本次限流并清理键：{}", redisKey, ex);
            try {
                redisTemplate.delete(redisKey);
            } catch (RuntimeException deleteEx) {
                log.warn("限流异常键清理失败：{}", redisKey, deleteEx);
            }
            return true;
        }
    }
}
