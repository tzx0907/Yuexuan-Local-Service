package com.sky.task;

import com.sky.mapper.FlashSaleActivityMapper;
import com.sky.service.impl.RedisFlashSaleStockGuard;
import com.sky.vo.FlashSaleActivityVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** 启动时把进行中活动的剩余配额写入 Redis，作为预扣闸门的初始值。 */
@Slf4j
@Component
public class FlashSaleStockWarmUpRunner implements ApplicationRunner {

    private final FlashSaleActivityMapper activityMapper;
    private final RedisFlashSaleStockGuard stockGuard;

    public FlashSaleStockWarmUpRunner(FlashSaleActivityMapper activityMapper,
                                      RedisFlashSaleStockGuard stockGuard) {
        this.activityMapper = activityMapper;
        this.stockGuard = stockGuard;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!stockGuard.isEnabled()) {
            log.info("限时购 Redis 预扣未启用，跳过库存预热");
            return;
        }
        List<FlashSaleActivityVO> activities = activityMapper.listActive();
        int warmed = 0;
        for (FlashSaleActivityVO activity : activities) {
            if (stockGuard.warmUpIfAbsent(activity.getId(), activity.getRemainingStock())) {
                warmed++;
            }
        }
        log.info("限时购库存预热完成 active={}, newlyWarmed={}", activities.size(), warmed);
    }
}