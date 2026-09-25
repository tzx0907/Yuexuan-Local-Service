package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 订单明细
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //名称
    private String name;

    //订单id
    private Long orderId;

    //菜品id
    private Long dishId;

    //商品 SKU id
    private Long skuId;

    // 限时购活动 id，用于历史订单展示及未支付关闭后的活动配额回补。
    private Long flashSaleActivityId;

    //下单时的 SKU 规格快照
    private String skuSnapshot;

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

    /**
     * 组合商品的组成明细，仅用于订单接口返回，不映射 order_detail 表字段。
     * 普通商品保持为空；组合商品返回下单时所属组合的商品、每组合份数，
     * 供用户端和管理端在订单中展开查看。
     */
    private List<SetmealDish> setmealDishes;
}
