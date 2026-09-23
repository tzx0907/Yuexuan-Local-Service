package com.sky.service;

/** 订单超时关闭与库存回补。 */
public interface OrderTimeoutService {
    /** @return true 表示本次成功关闭并回补库存，false 表示订单已被其他流程处理。 */
    boolean closeIfUnpaid(Long orderId, String triggerSource);

    /**
     * 关闭尚未支付的订单并释放其占用资源。用户主动取消、MQ 超时和定时兜底都应复用此入口。
     */
    boolean closeUnpaid(Long orderId, String triggerSource, String cancelReason);
}
