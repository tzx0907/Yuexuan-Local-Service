package com.sky.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FlashSaleActivityDTO {
    private Long id;
    private Long skuId;
    private BigDecimal salePrice;
    private Integer activityStock;
    private Integer perUserLimit;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
