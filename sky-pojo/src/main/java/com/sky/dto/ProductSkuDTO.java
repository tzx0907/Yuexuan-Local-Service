package com.sky.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductSkuDTO {
    private Long id;
    private Long dishId;
    private String specName;
    private String specValue;
    private BigDecimal price;
    private Integer stock;
    private Integer status;
}
