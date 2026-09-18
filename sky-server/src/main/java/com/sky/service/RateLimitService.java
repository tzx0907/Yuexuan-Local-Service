package com.sky.service;

/**
 * 基于 Redis 的接口访问频率控制。
 */
public interface RateLimitService {

    /**
     * 记录当前用户的一次请求，并判断是否仍在允许次数内。
     */
    boolean tryAcquire(String businessKey, Long userId, int limit, int windowSeconds);
}
