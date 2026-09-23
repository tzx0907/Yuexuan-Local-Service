package com.sky.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class ShoppingCartDTO implements Serializable {

    private Long dishId;
    private Long skuId;
    /** 限时购活动 id；为空表示普通售价商品。 */
    private Long flashSaleActivityId;
    private Long setmealId;
    private String dishFlavor;

}
