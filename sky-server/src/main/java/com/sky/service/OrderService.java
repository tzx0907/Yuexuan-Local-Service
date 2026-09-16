package com.sky.service;

import com.sky.dto.DishPageQueryDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.Orders;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

public interface OrderService {
    OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO,String idempotencyKey);
    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    void reminder(Long id);

    OrderVO getOrderDetail(Long id);

    void cancel(Long id);

    void confirm(Long id);

    void reject(Long id, String rejectionReason);

    void cancelByAdmin(Long id, String cancelReason);

    void delivery(Long id);

    void complete(Long id);

    void repetition(Long id);

    PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderStatisticsVO getStatistics();
}
