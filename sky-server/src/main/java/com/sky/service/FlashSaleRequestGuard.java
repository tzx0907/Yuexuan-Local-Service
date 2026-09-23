package com.sky.service;

/** 限时购请求削峰：只限制短时间重复点击，不承担最终库存扣减。 */
public interface FlashSaleRequestGuard {
    boolean tryAcquire(Long activityId, Long userId);
}
