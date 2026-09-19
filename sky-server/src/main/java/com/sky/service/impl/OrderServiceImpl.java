package com.sky.service.impl;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderStateMachine;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    private DishMapper dishMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private ProductSkuMapper productSkuMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 提交订单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO,String idempotencyKey) {
        Long userId = BaseContext.getCurrentId();
        validateIdempotencyKey(idempotencyKey);
        String redisKey = "order:submit:" + userId + ":" + idempotencyKey;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                redisKey,
                "PROCESSING",
                5,
                TimeUnit.MINUTES
        );
        if (!Boolean.TRUE.equals(acquired)) {
            return getRepeatedSubmitResult(userId, idempotencyKey, redisKey);
        }

        try {
            Orders existingOrder = orderMapper.getByUserIdAndSubmitRequestId(userId, idempotencyKey);
            if (existingOrder != null) {
                markSubmitSuccessAfterCommit(redisKey, existingOrder.getId());
                return buildSubmitVO(existingOrder);
            }

            Long addressId = ordersSubmitDTO.getAddressBookId();
            AddressBook addressBook = addressId == null ? null : addressBookMapper.getById(addressId);
            List<ShoppingCart> list = shoppingCartMapper.list(ShoppingCart.builder().userId(userId).build());
            //1.处理异常（地址为空 购物车为空）
            boolean selfPickup = Integer.valueOf(2).equals(ordersSubmitDTO.getDeliveryStatus());
            if (!selfPickup && addressBook == null) {
                throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
            }
            if (list == null||list.isEmpty()) {
                throw new AddressBookBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
            }
            boolean containsOnsiteService = list.stream().anyMatch(cart -> {
                if (cart.getDishId() == null) return false;
                Dish cartDish = dishMapper.getById(cart.getDishId());
                Category category = cartDish == null ? null : categoryMapper.getById(cartDish.getCategoryId());
                return category != null && "上门服务".equals(category.getName());
            });
            // 上门服务需要服务人员到用户地址履约，不允许切换为到店自提。
            if (containsOnsiteService && selfPickup) {
                throw new OrderBusinessException("上门服务不支持到店自提，请选择预约上门时间");
            }
            // 普通商品采用条件更新原子扣减库存；返回 0 表示商品已下架或库存不足。
            // 商品组合库存将在 SKU/组合物料清单改造后统一处理。
            for (ShoppingCart shoppingCart : list) {
                if (shoppingCart.getSkuId() != null
                        && productSkuMapper.decrementStock(shoppingCart.getSkuId(), shoppingCart.getNumber()) != 1) {
                    throw new OrderBusinessException("商品规格库存不足或已下架");
                }
                if (shoppingCart.getSkuId() == null && shoppingCart.getDishId() != null
                        && dishMapper.decrementStock(shoppingCart.getDishId(), shoppingCart.getNumber()) != 1) {
                    throw new OrderBusinessException("商品库存不足或已下架");
                }
            }
            //2.向订单表中插入一条数据
            Orders orders = new Orders();
            BeanUtils.copyProperties(ordersSubmitDTO, orders);
            //填充其他字段
            orders.setSubmitRequestId(idempotencyKey);
            orders.setUserId(userId);
            orders.setNumber(String.valueOf(System.currentTimeMillis()));
            orders.setOrderTime(LocalDateTime.now());
            orders.setPayStatus(Orders.UN_PAID);
            orders.setStatus(Orders.PENDING_PAYMENT);
            if (selfPickup) {
                orders.setAddressBookId(null);
                orders.setConsignee("悦选服务点自提");
                orders.setAddress("悦选服务点");
                orders.setPhone(null);
            } else {
                orders.setAddressBookId(addressId);
                orders.setPhone(addressBook.getPhone());
                orders.setConsignee(addressBook.getConsignee());
                orders.setAddress(addressBook.getAddress());
            }
            // 订单金额只能以服务端购物车中已经确认的商品/SKU 单价计算，
            // 不采信小程序传来的 amount，避免规格价或客户端金额被篡改。
            BigDecimal goodsAmount = list.stream()
                    .map(cart -> cart.getAmount().multiply(BigDecimal.valueOf(cart.getNumber())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean containsPhysicalGoods = list.stream().anyMatch(cart -> {
                if (cart.getDishId() == null) return true;
                Dish cartDish = dishMapper.getById(cart.getDishId());
                Category category = cartDish == null ? null : categoryMapper.getById(cartDish.getCategoryId());
                return category == null || !"上门服务".equals(category.getName());
            });
            // 上门服务是预约履约，不属于商品打包或配送；任何服务订单均不收这两项费用。
            BigDecimal packingFee = !containsOnsiteService && containsPhysicalGoods ? BigDecimal.ONE : BigDecimal.ZERO;
            BigDecimal deliveryFee = !containsOnsiteService && !selfPickup ? BigDecimal.valueOf(4) : BigDecimal.ZERO;
            orders.setPackAmount(packingFee.intValue());
            orders.setAmount(goodsAmount.add(packingFee).add(deliveryFee));
            orderMapper.insert(orders); // Insert order into the database
            //3.向订单明细表中插入多条数据
            List<OrderDetail> orderDetails = new ArrayList<>();
            for (ShoppingCart shoppingCart : list){
                OrderDetail orderDetail = new OrderDetail();
                BeanUtils.copyProperties(shoppingCart, orderDetail);
                orderDetail.setOrderId(orders.getId());
                if (shoppingCart.getSkuId() != null) {
                    orderDetail.setSkuSnapshot(shoppingCart.getDishFlavor());
                }
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
            //提交订单后清空购物车
            shoppingCartMapper.cleanByUserId(userId);
            markSubmitSuccessAfterCommit(redisKey, orders.getId());
            return orderSubmitVO;
        } catch (DuplicateKeyException e) {
            Orders existingOrder = orderMapper.getByUserIdAndSubmitRequestId(userId, idempotencyKey);
            if (existingOrder != null) {
                markSubmitSuccessAfterCommit(redisKey, existingOrder.getId());
                return buildSubmitVO(existingOrder);
            }
            redisTemplate.delete(redisKey);
            throw e;
        } catch (RuntimeException e) {
            redisTemplate.delete(redisKey);
            throw e;
        }
    }
    /**
     * 订单支付（模拟支付，跳过微信支付）
     *
     * @param ordersPaymentDTO
     * @return
     */
    @Override
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
    @Override
    @Transactional
    public void paySuccess(String outTradeNo) {
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            return;
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();
        if (!markPaid(orders)) {
            Orders currentOrder = getOrder(ordersDB.getId());
            if (Orders.PAID.equals(currentOrder.getPayStatus())) {
                return;
            }
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

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
        Orders order = getOrder(id);
        Long userId = BaseContext.getCurrentId();
        if (!order.getUserId().equals(userId)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (order.getStatus().equals(Orders.PENDING_PAYMENT)) {
            transition(Orders.builder().id(id).status(Orders.CANCELLED).cancelTime(LocalDateTime.now())
                    .cancelReason("用户取消订单").build(), Orders.PENDING_PAYMENT);
        } else if (order.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            transition(Orders.builder().id(id).status(Orders.CANCELLED).cancelTime(LocalDateTime.now())
                    .cancelReason("用户取消订单").build(), Orders.TO_BE_CONFIRMED);
        } else {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        webSocketServer.sendToAllClient("订单号"+id+"已取消");
    }

    @Override
    public void confirm(Long id) {
        transition(Orders.builder().id(id).status(Orders.CONFIRMED).build(), Orders.TO_BE_CONFIRMED);
    }

    @Override
    public void reject(Long id, String rejectionReason) {
        transition(Orders.builder().id(id).status(Orders.CANCELLED).cancelTime(LocalDateTime.now())
                .rejectionReason(rejectionReason).build(), Orders.TO_BE_CONFIRMED);
    }

    @Override
    public void cancelByAdmin(Long id, String cancelReason) {
        Orders order = getOrder(id);
        if (order.getStatus().equals(Orders.TO_BE_CONFIRMED) || order.getStatus().equals(Orders.CONFIRMED)) {
            transition(Orders.builder().id(id).status(Orders.CANCELLED).cancelTime(LocalDateTime.now())
                    .cancelReason(cancelReason).build(), order.getStatus());
            return;
        }
        throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
    }

    @Override
    public void delivery(Long id) {
        transition(Orders.builder().id(id).status(Orders.DELIVERY_IN_PROGRESS).build(), Orders.CONFIRMED);
    }

    @Override
    public void complete(Long id) {
        transition(Orders.builder().id(id).status(Orders.COMPLETED).deliveryTime(LocalDateTime.now()).build(),
                Orders.DELIVERY_IN_PROGRESS);
    }
    @Override
    @Transactional
    public void repetition(Long id) {
        Long userId = BaseContext.getCurrentId();
        List<OrderDetail> orderDetailList = orderDetailMapper.getOrderDetailByOrderId(id);
        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = ShoppingCart.builder()
                    .userId(userId)
                    .dishId(orderDetail.getDishId())
                    .setmealId(orderDetail.getSetmealId())
                    .name(orderDetail.getName())
                    .amount(orderDetail.getAmount())
                    .image(orderDetail.getImage())
                    .number(orderDetail.getNumber())
                    .createTime(LocalDateTime.now())
                    .build();
            shoppingCartMapper.insert(shoppingCart);
        }
    }
    @Override
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO){
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        List<Orders> ordersList = orderMapper.pageQuery(ordersPageQueryDTO);
        Page<Orders> ordersPage = (Page<Orders>) ordersList;
        Page<OrderVO> page = new Page<>();
        for (Orders orders : ordersList){
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);
            Long orderId = orders.getId();
            List<OrderDetail> orderDetailList = orderDetailMapper.getOrderDetailByOrderId(orderId);
            orderVO.setOrderDetailList(orderDetailList);
            page.add(orderVO);
        }
        return new PageResult(ordersPage.getTotal(), page.getResult());
    }
    private Orders getOrder(Long id) {
        Orders order = orderMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void transition(Orders orders, Integer expectedStatus) {
        if (!OrderStateMachine.canTransition(expectedStatus, orders.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (orderMapper.updateIfStatus(orders, expectedStatus) == 0) {
            Orders currentOrder = orderMapper.getById(orders.getId());
            if (currentOrder == null) {
                throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
            }
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    private boolean markPaid(Orders orders) {
        if (!OrderStateMachine.canTransition(Orders.PENDING_PAYMENT, orders.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        return orderMapper.updateIfStatus(orders, Orders.PENDING_PAYMENT) == 1;
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty() || idempotencyKey.length() > 64) {
            throw new OrderBusinessException("幂等请求号不能为空且长度不能超过64位");
        }
    }

    private OrderSubmitVO getRepeatedSubmitResult(Long userId, String idempotencyKey, String redisKey) {
        Object cachedValue = redisTemplate.opsForValue().get(redisKey);
        if (cachedValue instanceof String && ((String) cachedValue).startsWith("SUCCESS:")) {
            Long orderId = Long.valueOf(((String) cachedValue).substring("SUCCESS:".length()));
            Orders order = getOrder(orderId);
            if (userId.equals(order.getUserId())) {
                return buildSubmitVO(order);
            }
        }

        Orders existingOrder = orderMapper.getByUserIdAndSubmitRequestId(userId, idempotencyKey);
        if (existingOrder != null) {
            markSubmitSuccessAfterCommit(redisKey, existingOrder.getId());
            return buildSubmitVO(existingOrder);
        }
        throw new OrderBusinessException("订单正在提交，请勿重复操作");
    }

    private OrderSubmitVO buildSubmitVO(Orders order) {
        return OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();
    }

    private void markSubmitSuccessAfterCommit(String redisKey, Long orderId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                redisTemplate.opsForValue().set(redisKey, "SUCCESS:" + orderId, 5, TimeUnit.MINUTES);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    redisTemplate.delete(redisKey);
                }
            }
        });
    }
    @Override
    public OrderStatisticsVO getStatistics() {
        return orderMapper.getStatistics();
    }
}
