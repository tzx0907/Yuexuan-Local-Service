package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingCart implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //名称
    private String name;

    //用户id
    private Long userId;

    //菜品id
    private Long dishId;

    //商品 SKU id
    private Long skuId;

    // 限时购活动 id。订单提交时必须再次校验活动，不信任购物车中的活动价。
    private Long flashSaleActivityId;

    //套餐id
    private Long setmealId;

    //口味
    private String dishFlavor;

    //数量
    private Integer number;

    //金额
    private BigDecimal amount;

    //图片
    private String image;

    // 浏览/结算展示使用的商品分类信息；不落 shopping_cart 表，由查询关联返回。
    private Long categoryId;

    private String categoryName;

    private LocalDateTime createTime;
}
