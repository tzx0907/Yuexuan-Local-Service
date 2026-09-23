package com.sky.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 限时购活动。库存与商品 SKU 库存分开记录：前者限制活动配额，后者代表真实可售库存。
 */
@Data
public class FlashSaleActivity {
    private Long id;
    private Long skuId;
    private BigDecimal salePrice;
    private Integer activityStock;
    private Integer soldStock;
    private Integer perUserLimit;
    /** 0=停用，1=启用 */
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
