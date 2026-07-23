package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.*;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import io.swagger.util.Json;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;
    /**
     * 提交订单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        Long addressId = ordersSubmitDTO.getAddressBookId();
        AddressBook addressBook = addressBookMapper.getById(addressId);
        Long userId= BaseContext.getCurrentId();
        List<ShoppingCart> list = shoppingCartMapper.list(ShoppingCart.builder().userId(userId).build());
        //1.处理异常（地址为空 购物车为空）
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        if (list == null||list.isEmpty()) {
            throw new AddressBookBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        //2.向订单表中插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        //填充其他字段
        orders.setUserId(userId);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setAddressBookId(addressId);
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(addressBook.getAddress());
        orderMapper.insert(orders); // Insert order into the database
        //3.向订单明细表中插入多条数据
        List<OrderDetail> orderDetails = new ArrayList<>();
        for (ShoppingCart shoppingCart : list){
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(shoppingCart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetails.add(orderDetail);
        }
        orderDetailMapper.batchInsert(orderDetails);
        //5.返回订单确认页面需要的VO
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
        Map<String, Object> map = new HashMap<>();
        return orderSubmitVO;
    }
    /**
     * 订单支付（模拟支付，跳过微信支付）
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 直接调用支付成功逻辑，跳过微信支付
        paySuccess(ordersPaymentDTO.getOrderNumber());

        return OrderPaymentVO.builder().build();
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        // 支付成功后清空购物车
        shoppingCartMapper.cleanByUserId(ordersDB.getUserId());
        //给管理端发来单提醒，使用WebSocket
        Map<String, Object> map = new HashMap<>();
        map.put("type",1);//1表示来单，2表示催单
        map.put("orderId", ordersDB.getId());
        map.put("content","订单号"+outTradeNo);
        String json = com.alibaba.fastjson.JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }
    public void reminder(Long orderId){
        Map<String, Object> map = new HashMap<>();
        map.put("type",2);//1表示来单，2表示催单
        map.put("orderId", orderId);
        map.put("content","订单号"+orderId);
        String json = com.alibaba.fastjson.JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }
    public OrderVO getOrderDetail(Long id){
        OrderVO orderVO = new OrderVO();
        List<OrderDetail> orderDetailList = orderDetailMapper.getOrderDetailByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);
        Orders orders = orderMapper.getById(id);
        BeanUtils.copyProperties(orders, orderVO);
        return orderVO;
    }
    public void cancel(Long id){
        orderMapper.cancel(id, LocalDateTime.now());
        webSocketServer.sendToAllClient("订单号"+id+"已取消");
    }
}
