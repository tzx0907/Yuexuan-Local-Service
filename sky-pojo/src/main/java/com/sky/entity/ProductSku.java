package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商品可售库存单位（SKU）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSku implements Serializable {
    private Long id;
    private Long dishId;
    private String specName;
    private String specValue;
    private BigDecimal price;
    private Integer stock;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
