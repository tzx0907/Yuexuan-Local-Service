package com.sky.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class ShoppingCartDTO implements Serializable {

    private Long dishId;
    private Long skuId;
    /** 限时购活动 id；为空表示普通售价商品。 */
    private Long flashSaleActivityId;
    /** 普通商品入口的显式标记；优先级高于页面可能残留的活动 id。 */
    private Boolean normalPurchase;
    private Long setmealId;
    private String dishFlavor;

}
