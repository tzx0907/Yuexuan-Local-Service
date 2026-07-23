package com.sky.controller.user;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController("UserOrderController")
@RequestMapping("/user/order")
@Api(tags= "用户端订单相关接口")
public class OrderController {
    @Autowired
    private OrderService orderService;
    @PostMapping("/submit")
    @ApiOperation("用户提交订单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO){
        log.info("用户提交订单：{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.submit(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }
    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }
    /**
     * 催单提醒
     *
     * @param id
     * @return
     */
    @GetMapping("/reminder/{id}")
    @ApiOperation("催单提醒")
    public Result<?> reminder(@PathVariable Long id){
        log.info("催单提醒：{}", id);
        orderService.reminder(id);
        return Result.success();
    }
    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @GetMapping("orderDetail/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> detail(@PathVariable Long id){
        log.info("查询订单详情：{}", id);
        OrderVO orderVO = orderService.getOrderDetail(id);
        return Result.success(orderVO);
    }
    /**
     * 取消订单
     *
     * @param id
     * @return
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result<?> cancel(@PathVariable Long id){
        log.info("取消订单：{}", id);
        orderService.cancel(id);
        return Result.success();
    }
    /**
     * 用户再来一单
     *
     * @param id
     * @return
     */
    @PostMapping("/repetition/{id}")
    @ApiOperation("用户再来一单")
    public Result<?> repetition(@PathVariable Long id){
        log.info("用户再来一下单：{}", id);
        orderService.repetition(id);
        return Result.success();
    }
    @ApiOperation("分页查询历史订单")
    @GetMapping("/historyOrders")
    public Result<PageResult> page(OrdersPageQueryDTO ordersPageQueryDTO){
        log.info("分页查询历史订单：{}", ordersPageQueryDTO);
        PageResult pageResult = orderService.pageQuery(ordersPageQueryDTO);
        return Result.success(pageResult);
    }
}
