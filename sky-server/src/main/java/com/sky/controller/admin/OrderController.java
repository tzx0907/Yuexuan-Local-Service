package com.sky.controller.admin;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController("AdminOrderController")
@RequestMapping("/admin/order")
@Slf4j
public class OrderController {
    @Autowired
    private OrderService orderService;
    @GetMapping("/details/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> detail(@PathVariable Long id){
        log.info("查询订单详情：{}", id);
        OrderVO orderVO = orderService.getOrderDetail(id);
        return Result.success(orderVO);
    }

    @PutMapping("/complete/{id}")
    @ApiOperation("完成订单")
    public Result<?> complete(@PathVariable Long id){
        log.info("完成订单：{}", id);
        orderService.complete(id);
        return Result.success();
    }

    @PutMapping("/confirm")
    @ApiOperation("确认订单")
    public Result<?> confirm(@RequestBody OrdersConfirmDTO dto){
        log.info("确认订单：{}", dto);
        orderService.confirm(dto.getId());
        return Result.success();
    }

    @PutMapping("/rejection")
    @ApiOperation("拒绝订单")
    public Result<?> rejection(@RequestBody OrdersRejectionDTO ordersRejectionDTO){
        log.info("拒绝订单：{}", ordersRejectionDTO.getId());
        orderService.reject(ordersRejectionDTO.getId(), ordersRejectionDTO.getRejectionReason());
        return Result.success();
    }

    @PutMapping("/cancel")
    @ApiOperation("取消订单")
    public Result<?> cancel(@RequestBody OrdersCancelDTO dto){
        log.info("取消订单：{}", dto);
        orderService.cancelByAdmin(dto.getId(), dto.getCancelReason());
        return Result.success();
    }

    @PutMapping("/delivery/{id}")
    @ApiOperation("派送订单")
    public Result<?> delivery(@PathVariable Long id){
        log.info("派送订单：{}", id);
        orderService.delivery(id);
        return Result.success();
    }
    @GetMapping("/conditionSearch")
    public Result<PageResult> conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO){
        log.info("条件查询订单：{}", ordersPageQueryDTO);
        PageResult pageResult = orderService.pageQuery(ordersPageQueryDTO);
        return Result.success(pageResult);
    }
    @GetMapping("/statistics")
    public Result<OrderStatisticsVO> statistics(){
        log.info("查询订单统计信息");
        OrderStatisticsVO orderStatisticsVO = orderService.getStatistics();
        return Result.success(orderStatisticsVO);
    }
}
