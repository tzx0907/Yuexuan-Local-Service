package com.sky.service.impl;

import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ProductSkuMapper;
import com.sky.service.OrderTimeoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class OrderTimeoutServiceImpl implements OrderTimeoutService {
    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final DishMapper dishMapper;
    private final ProductSkuMapper productSkuMapper;

    public OrderTimeoutServiceImpl(OrderMapper orderMapper, OrderDetailMapper orderDetailMapper,
                                   DishMapper dishMapper, ProductSkuMapper productSkuMapper) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.dishMapper = dishMapper;
        this.productSkuMapper = productSkuMapper;
    }

    @Override
    @Transactional
    public boolean closeIfUnpaid(Long orderId, String triggerSource) {
        Orders current = orderMapper.getById(orderId);
        if (current == null || !Orders.PENDING_PAYMENT.equals(current.getStatus())
                || !Orders.UN_PAID.equals(current.getPayStatus())) {
            log.info("跳过超时关闭，订单已支付、已取消或不存在 orderId={}, source={}", orderId, triggerSource);
            return false;
        }

        Orders cancellation = Orders.builder()
                .id(orderId)
                .status(Orders.CANCELLED)
                .cancelTime(LocalDateTime.now())
                .cancelReason("支付超时，系统自动关闭")
                .build();
        // MQ、定时任务或重复消息并发时，只有一个线程能从待支付状态更新成功。
        if (orderMapper.updateIfStatus(cancellation, Orders.PENDING_PAYMENT) != 1) {
            log.info("订单超时关闭被其他流程抢先处理 orderId={}, source={}", orderId, triggerSource);
            return false;
        }

        List<OrderDetail> details = orderDetailMapper.getOrderDetailByOrderId(orderId);
        for (OrderDetail detail : details) {
            int updated;
            if (detail.getSkuId() != null) {
                updated = productSkuMapper.incrementStock(detail.getSkuId(), detail.getNumber());
            } else if (detail.getDishId() != null) {
                updated = dishMapper.incrementStock(detail.getDishId(), detail.getNumber());
            } else {
                // 组合商品当前下单时未扣减单品库存，待物料清单能力完成后统一处理。
                continue;
            }
            if (updated != 1) {
                throw new OrderBusinessException("订单超时库存回补失败");
            }
        }
        log.info("订单支付超时已关闭并回补库存 orderId={}, source={}", orderId, triggerSource);
        return true;
    }
}
