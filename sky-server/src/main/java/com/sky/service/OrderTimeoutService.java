package com.sky.service;

/** 订单超时关闭与库存回补。 */
public interface OrderTimeoutService {
    /** @return true 表示本次成功关闭并回补库存，false 表示订单已被其他流程处理。 */
    boolean closeIfUnpaid(Long orderId, String triggerSource);
}
