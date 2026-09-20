package com.sky.task;

import com.sky.mapper.ShoppingCartMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 购物车是用户的暂存数据，不应永久保留；1天后自动清理长期未结算记录。
 */
@Component
@Slf4j
public class ShoppingCartCleanupTask {
    static final int RETENTION_DAYS = 1;

    private final ShoppingCartMapper shoppingCartMapper;

    public ShoppingCartCleanupTask(ShoppingCartMapper shoppingCartMapper) {
        this.shoppingCartMapper = shoppingCartMapper;
    }

    /** 每天凌晨 3 点执行，避免影响用户高峰期的购物车操作。 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredShoppingCarts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = shoppingCartMapper.deleteExpired(cutoff);
        if (deleted > 0) {
            log.info("已清理长期未结算购物车 records={}, cutoff={}", deleted, cutoff);
        }
    }
}
