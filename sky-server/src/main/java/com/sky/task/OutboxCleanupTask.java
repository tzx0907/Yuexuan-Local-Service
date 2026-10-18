package com.sky.task;

import com.sky.mapper.OutboxEventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Outbox 已投递成功的事件只用于排查审计，不应永久保留；
 * 保留 7 天后分批清理，防止表无限膨胀影响备份与索引维护。
 */
@Component
@Slf4j
public class OutboxCleanupTask {
    static final int RETENTION_DAYS = 7;
    static final int BATCH_SIZE = 1000;
    static final int MAX_BATCHES = 20;

    private final OutboxEventMapper outboxEventMapper;

    public OutboxCleanupTask(OutboxEventMapper outboxEventMapper) {
        this.outboxEventMapper = outboxEventMapper;
    }

    /** 每天凌晨 4:30 执行，与购物车清理任务错峰；分批删除避免长事务锁表。 */
    @Scheduled(cron = "0 30 4 * * ?")
    public void cleanSentEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            int deleted = outboxEventMapper.deleteSentBefore(cutoff, BATCH_SIZE);
            total += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("已清理 Outbox 历史事件 records={}, cutoff={}", total, cutoff);
        }
    }
}