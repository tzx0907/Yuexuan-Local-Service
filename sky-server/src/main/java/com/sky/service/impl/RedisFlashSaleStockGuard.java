package com.sky.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;

/**
 * 限时购 Redis 预扣：把配额已耗尽或已超限的请求挡在数据库事务之外。
 *
 * <p>Redis 只是闸门，不是真相。MySQL 的 sold_stock 与 sku.stock 仍是唯一持久化依据，
 * 预扣成功的请求依然要在事务里条件扣减。两者不一致时以 MySQL 为准，由对账任务修正。</p>
 */
@Slf4j
@Component
public class RedisFlashSaleStockGuard {

    public static final int DEDUCTED = 0;
    public static final int SOLD_OUT = 1;
    public static final int OVER_QUOTA = 2;
    public static final int NOT_WARMED = -1;

    private static final String STOCK_KEY_PREFIX = "yuexuan:flash-sale:stock:";
    private static final String QUOTA_KEY_PREFIX = "yuexuan:flash-sale:quota:";

    /**
     * 预扣：查余量 → 查该用户已占用 → 扣减。三段必须在同一脚本内完成，
     * 否则两个请求会同时读到 stock=1 而双双通过，造成超卖。
     */
    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>(
            "local stock = redis.call('GET', KEYS[1]); "
                    + "if stock == false then return -1 end; "
                    + "stock = tonumber(stock); "
                    + "local qty = tonumber(ARGV[2]); "
                    + "if stock < qty then return 1 end; "
                    + "local used = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0'); "
                    + "if used + qty > tonumber(ARGV[3]) then return 2 end; "
                    + "redis.call('DECRBY', KEYS[1], qty); "
                    + "redis.call('HINCRBY', KEYS[2], ARGV[1], qty); "
                    + "return 0",
            Long.class);

    /**
     * 回补：先校验该用户当前占用是否够扣，防止重复回补把库存补多。
     * 返回 0 表示占用不足、拒绝回补；返回 1 表示回补成功。
     */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "local qty = tonumber(ARGV[2]); "
                    + "local used = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0'); "
                    + "if used < qty then return 0 end; "
                    + "redis.call('INCRBY', KEYS[1], qty); "
                    + "local remain = redis.call('HINCRBY', KEYS[2], ARGV[1], -qty); "
                    + "if remain <= 0 then redis.call('HDEL', KEYS[2], ARGV[1]) end; "
                    + "return 1",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;

    public RedisFlashSaleStockGuard(StringRedisTemplate redisTemplate,
                                    @Value("${sky.flash-sale.pre-deduct.enabled:false}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return {@link #DEDUCTED} 成功 / {@link #SOLD_OUT} 售罄 /
     *         {@link #OVER_QUOTA} 超限 / {@link #NOT_WARMED} 未启用或 Redis 异常（降级为纯 MySQL 判定）
     */
    public int tryDeduct(Long activityId, Long userId, int quantity, int perUserLimit) {
        if (!enabled) {
            return NOT_WARMED;
        }
        try {
            Long result = redisTemplate.execute(DEDUCT_SCRIPT, keys(activityId),
                    String.valueOf(userId), String.valueOf(quantity), String.valueOf(perUserLimit));
            return result == null ? NOT_WARMED : result.intValue();
        } catch (RuntimeException ex) {
            // 闸门失效不能阻断交易：降级为纯 MySQL 判定，正确性由条件更新保证。
            log.warn("限时购 Redis 预扣不可用，降级为 MySQL 校验 activityId={}, userId={}", activityId, userId, ex);
            return NOT_WARMED;
        }
    }

    /** 下单事务回滚时归还预扣额度。必须在 afterCompletion 里做。 */
    public void registerRollbackOnFailure(Long activityId, Long userId, int quantity) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    release(activityId, userId, quantity);
                }
            }
        });
    }

    /**
     * 关单成功后归还额度。必须放在 afterCommit：若放在事务内，后续 SQL 失败回滚
     * 会让 Redis 白补一次，余量虚高后超卖。放 afterCommit 的代价是回补失败会让
     * Redis 偏少（少卖），这比超卖安全，由对账任务兜住。
     */
    public void registerReleaseAfterCommit(Long activityId, Long userId, int quantity) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            release(activityId, userId, quantity);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                release(activityId, userId, quantity);
            }
        });
    }

    public void release(Long activityId, Long userId, int quantity) {
        if (!enabled) {
            return;
        }
        try {
            Long result = redisTemplate.execute(RELEASE_SCRIPT, keys(activityId),
                    String.valueOf(userId), String.valueOf(quantity));
            if (result == null || result != 1L) {
                log.warn("限时购 Redis 回补未生效（占用不足，疑似重复回补）activityId={}, userId={}, quantity={}",
                        activityId, userId, quantity);
            }
        } catch (RuntimeException ex) {
            log.error("限时购 Redis 回补失败，等待对账任务修正 activityId={}, userId={}, quantity={}",
                    activityId, userId, quantity, ex);
        }
    }

    public void warmUp(Long activityId, int remaining) {
        redisTemplate.opsForValue().set(STOCK_KEY_PREFIX + activityId, String.valueOf(Math.max(remaining, 0)));
    }

    /** 已存在则不覆盖：Redis 里的余量可能包含应用重启前的在途预扣，覆盖会造成超卖。 */
    public boolean warmUpIfAbsent(Long activityId, int remaining) {
        Boolean created = redisTemplate.opsForValue()
                .setIfAbsent(STOCK_KEY_PREFIX + activityId, String.valueOf(Math.max(remaining, 0)));
        return Boolean.TRUE.equals(created);
    }

    /** 活动总配额调整时按差额增减，不覆盖，避免抹掉在途预扣。 */
    public void adjustStock(Long activityId, int delta) {
        if (delta == 0) {
            return;
        }
        String key = STOCK_KEY_PREFIX + activityId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.opsForValue().increment(key, delta);
        }
    }

    public Integer getStock(Long activityId) {
        String value = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + activityId);
        return value == null ? null : Integer.valueOf(value);
    }

    private List<String> keys(Long activityId) {
        return Arrays.asList(STOCK_KEY_PREFIX + activityId, QUOTA_KEY_PREFIX + activityId);
    }
}