package com.sky.vo;

import lombok.Data;
import java.math.BigDecimal;

/** 管理端选择限时购商品规格时使用的搜索结果。 */
@Data
public class ProductSkuSearchVO {
    private Long skuId;
    private Long dishId;
    private String productName;
    private String image;
    private String specName;
    private String specValue;
    private BigDecimal price;
    private Integer stock;
}
