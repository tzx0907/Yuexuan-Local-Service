package com.sky.task;

import com.sky.mapper.FlashSaleActivityMapper;
import com.sky.service.impl.RedisFlashSaleStockGuard;
import com.sky.vo.FlashSaleActivityVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时把 Redis 余量校准到 MySQL。以 DB 为准，但只收紧不放宽。 */
@Slf4j
@Component
public class FlashSaleStockReconcileTask {

    private static final int DRIFT_ALERT_THRESHOLD = 5;

    private final FlashSaleActivityMapper activityMapper;
    private final RedisFlashSaleStockGuard stockGuard;

    public FlashSaleStockReconcileTask(FlashSaleActivityMapper activityMapper,
                                       RedisFlashSaleStockGuard stockGuard) {
        this.activityMapper = activityMapper;
        this.stockGuard = stockGuard;
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void reconcile() {
        if (!stockGuard.isEnabled()) {
            return;
        }
        for (FlashSaleActivityVO activity : activityMapper.listActive()) {
            int dbRemaining = activity.getRemainingStock();
            Integer redisRemaining = stockGuard.getStock(activity.getId());
            if (redisRemaining == null) {
                stockGuard.warmUp(activity.getId(), dbRemaining);
                log.warn("对账补齐缺失的预热数据 activityId={}, remaining={}", activity.getId(), dbRemaining);
                continue;
            }
            if (redisRemaining > dbRemaining) {
                // Redis 偏多：回补重复或补偿脚本被绕过。收紧到 DB 值，防止超卖。
                stockGuard.warmUp(activity.getId(), dbRemaining);
                log.warn("对账收紧 Redis 库存 activityId={}, redis={}, db={}",
                        activity.getId(), redisRemaining, dbRemaining);
            } else if (dbRemaining - redisRemaining > DRIFT_ALERT_THRESHOLD) {
                // Redis 偏少：可能只是事务在途，也可能是回补失败。无法区分，只告警不覆盖，
                // 否则会把在途预扣抹掉，导致同一份额度被卖两次。
                log.warn("对账发现 Redis 库存偏少，疑似回补失败 activityId={}, redis={}, db={}",
                        activity.getId(), redisRemaining, dbRemaining);
            }
        }
    }
}