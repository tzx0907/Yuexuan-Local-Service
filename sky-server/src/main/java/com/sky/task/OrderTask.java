package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {
    @Autowired
    private OrderMapper orderMapper;
    @Scheduled(cron="0 * * * * ?")
    public void orderCancelTask() {
        log.info("user订单取消任务开始执行");
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);
        List<Orders> list=orderMapper.cancelOrderByStatusAndTime(Orders.PENDING_PAYMENT,time);
        for(Orders orders:list){
            orders.setStatus(Orders.CANCELLED);
            orders.setCancelTime(LocalDateTime.now());
            orders.setCancelReason("订单超时未支付，系统自动取消");
            orderMapper.updateIfStatus(orders, Orders.PENDING_PAYMENT);
        }
    }
    @Scheduled(cron="0 0 1 * * ?")
    public void deliveryCancelTask() {
        log.info("派送中订单自动完成任务开始执行");
        LocalDateTime time = LocalDateTime.now().plusHours(-1);
        List<Orders> list=orderMapper.cancelOrderByStatusAndTime(Orders.DELIVERY_IN_PROGRESS,time);
        for(Orders orders:list){
            orders.setStatus(Orders.COMPLETED);
            orders.setDeliveryTime(LocalDateTime.now());
            orderMapper.updateIfStatus(orders, Orders.DELIVERY_IN_PROGRESS);
        }
    }
}
