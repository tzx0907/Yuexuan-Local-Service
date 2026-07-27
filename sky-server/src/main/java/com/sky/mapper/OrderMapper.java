package com.sky.mapper;

import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import com.sky.vo.OrderStatisticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    void insert(Orders orders);
    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);
    /**
     * 根据状态和时间取消订单
     * @param pendingPayment
     * @param time
     */
    List<Orders> cancelOrderByStatusAndTime(Integer pendingPayment, LocalDateTime time);

    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    @Update("update orders set status = 6, cancel_time = #{time} where id = #{id}")
    void cancel(Long id, LocalDateTime time);

    @Select("select * from orders where user_id = #{userId} order by order_time desc")
    List<Orders> getByUserId(Long userId);

    List<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderStatisticsVO getStatistics();
}
