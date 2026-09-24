package com.sky.vo;

import com.sky.entity.FlashSaleActivity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 限时购活动及其商品、规格展示信息。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlashSaleActivityVO extends FlashSaleActivity {
    private Long dishId;
    private String productName;
    private String image;
    private String specName;
    private String specValue;
    private java.math.BigDecimal originalPrice;
    private Integer skuStock;
    /** 活动总配额减去已售配额，不等同于商品 SKU 库存。 */
    private Integer remainingStock;
}
