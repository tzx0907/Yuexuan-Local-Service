package com.sky.service.impl;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.event.OrderPaidEvent;
import com.sky.event.OrderCloseEvent;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderStateMachine;
import com.sky.service.OrderService;
import com.sky.service.OutboxService;
import com.sky.service.OrderTimeoutService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
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
    private FlashSaleActivityMapper flashSaleActivityMapper;
    @Autowired
    private FlashSaleUserQuotaMapper flashSaleUserQuotaMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private OutboxService outboxService;
    @Autowired
    private OrderTimeoutService orderTimeoutService;

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
            boolean selfPickup = Integer.valueOf(2).equals(ordersSubmitDTO.getDeliveryStatus());
            // 自提不依赖收货地址内容；保留 addressId 仅用于兼容旧库 NOT NULL 约束。
            AddressBook addressBook = selfPickup || addressId == null
                    ? null : addressBookMapper.getById(addressId);
            List<ShoppingCart> list = shoppingCartMapper.list(ShoppingCart.builder().userId(userId).build());
            //1.处理异常（地址为空 购物车为空）
            if (!selfPickup && addressBook == null) {
                throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
            }
            if (list == null||list.isEmpty()) {
                throw new AddressBookBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
            }
            boolean containsOnsiteService = list.stream().anyMatch(this::isOnsiteServiceCart);
            boolean containsNonServiceItem = list.stream().anyMatch(cart -> !isOnsiteServiceCart(cart));
            // 上门服务是预约履约，实物商品是配送/自提履约，二者不能共用同一张订单。
            // 必须在库存扣减前拒绝，避免非法混单占用库存或产生费用规则冲突。
            if (containsOnsiteService && containsNonServiceItem) {
                throw new OrderBusinessException("上门服务和其他商品请分开下单");
            }
            // 上门服务需要服务人员到用户地址履约，不允许切换为到店自提。
            if (containsOnsiteService && selfPickup) {
                throw new OrderBusinessException("上门服务不支持到店自提，请选择预约上门时间");
            }
            // 限时购价格只能由活动服务端重新确认。购物车只是用户选择的暂存，
            // 不能作为活动仍有效或金额正确的依据。
            Map<Long, Integer> flashSaleQuantities = new HashMap<>();
            for (ShoppingCart cart : list) {
                if (cart.getFlashSaleActivityId() != null) {
                    FlashSaleActivity activity = flashSaleActivityMapper.getById(cart.getFlashSaleActivityId());
                    if (activity == null || !cart.getSkuId().equals(activity.getSkuId())) {
                        throw new OrderBusinessException("限时购活动与商品规格不匹配");
                    }
                    int quantity = flashSaleQuantities.getOrDefault(activity.getId(), 0) + cart.getNumber();
                    if (quantity > activity.getPerUserLimit()) {
                        throw new OrderBusinessException("超过该限时购活动的单次限购数量");
                    }
                    flashSaleQuantities.put(activity.getId(), quantity);
                    cart.setAmount(activity.getSalePrice());
                }
            }
            for (Map.Entry<Long, Integer> entry : flashSaleQuantities.entrySet()) {
                FlashSaleActivity activity = flashSaleActivityMapper.getById(entry.getKey());
                int quotaResult = flashSaleUserQuotaMapper.tryReserve(entry.getKey(), userId, entry.getValue(), activity.getPerUserLimit());
                if (quotaResult == 0) {
                    throw new OrderBusinessException("超过该限时购活动的每人累计限购数量");
                }
                if (flashSaleActivityMapper.decrementStock(entry.getKey(), entry.getValue()) != 1) {
                    throw new OrderBusinessException("限时购活动已结束或库存不足");
                }
            }
            // 普通商品采用条件更新原子扣减库存；返回 0 表示商品已下架或库存不足。
            // 组合商品保留独立领域模型，后续按组合物料清单统一扣减 SKU 库存。
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
            // 不整体复制 DTO：小程序的餐具等遗留字段允许为空，而 Orders 中对应字段是
            // int。BeanUtils 把 null 复制给基本类型会直接抛异常，前端只能看到“未知错误”。
            // 订单价格和服务费始终由下面的服务端逻辑计算，绝不采信客户端传入的 amount/packAmount。
            orders.setPayMethod(ordersSubmitDTO.getPayMethod());
            orders.setRemark(ordersSubmitDTO.getRemark());
            orders.setEstimatedDeliveryTime(ordersSubmitDTO.getEstimatedDeliveryTime());
            orders.setDeliveryStatus(ordersSubmitDTO.getDeliveryStatus());
            orders.setTablewareNumber(ordersSubmitDTO.getTablewareNumber() == null
                    ? 0 : ordersSubmitDTO.getTablewareNumber());
            orders.setTablewareStatus(ordersSubmitDTO.getTablewareStatus() == null
                    ? 1 : ordersSubmitDTO.getTablewareStatus());
            //填充其他字段
            orders.setSubmitRequestId(idempotencyKey);
            orders.setUserId(userId);
            orders.setNumber(String.valueOf(System.currentTimeMillis()));
            orders.setOrderTime(LocalDateTime.now());
            orders.setPayStatus(Orders.UN_PAID);
            orders.setStatus(Orders.PENDING_PAYMENT);
            if (selfPickup) {
                // 自提履约不使用配送地址，但保留客户端已有的地址簿 ID，兼容尚未执行
                // V10（orders.address_book_id 仍为 NOT NULL）的开发数据库。
                // 对外展示始终使用下面的服务点文案，不会泄露或误用配送地址。
                orders.setAddressBookId(addressId);
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
            boolean containsPhysicalGoods = containsNonServiceItem;
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
            scheduleOrderClose(orders);
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
    @Transactional
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
    // 改数据库状态和保存支付成功事件
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

        // 订单状态是支付的同步核心结果；运营提醒改由事务提交后异步消费。
        // 这样 MQ 或 WebSocket 短暂故障不会影响用户本次支付结果。
        saveOrderPaidEvent(OrderPaidEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(ordersDB.getId())
                .orderNumber(ordersDB.getNumber())
                .userId(ordersDB.getUserId())
                .amount(ordersDB.getAmount())
                .paidAt(orders.getCheckoutTime())
                .build());
    }
    // 发送订单支付成功事件
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
            // 不能仅改成已取消：下单时已扣 SKU、活动配额和限购额度，必须一起释放。
            if (!orderTimeoutService.closeUnpaid(id, "user-cancel", "用户取消订单")) {
                throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
            }
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

    /** 按分类名称识别悦选“上门服务”业务语义。 */
    private boolean isOnsiteServiceCart(ShoppingCart cart) {
        if (cart == null || cart.getDishId() == null) {
            return false;
        }
        Dish dish = dishMapper.getById(cart.getDishId());
        if (dish == null || dish.getCategoryId() == null) {
            return false;
        }
        Category category = categoryMapper.getById(dish.getCategoryId());
        return category != null && "上门服务".equals(category.getName());
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

    /**
     * 与订单支付状态处于同一个数据库事务；事务提交后由 Outbox 后台任务投递 RabbitMQ。
     * 因此不会出现订单已经支付但进程在 afterCommit 发送前退出而永久丢消息的窗口。
     */
    private void saveOrderPaidEvent(OrderPaidEvent event) {
        outboxService.saveOrderPaidEvent(event);
    }

    /** 与订单创建事务一起保存延迟关闭事件，提交后由 Outbox 投递器发送。 */
    private void scheduleOrderClose(Orders order) {
        OrderCloseEvent event = OrderCloseEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(order.getId())
                .orderNumber(order.getNumber())
                .createdAt(order.getOrderTime())
                .closeAt(order.getOrderTime().plusMinutes(15))
                .build();
        outboxService.saveOrderCloseEvent(event);
    }
    @Override
    public OrderStatisticsVO getStatistics() {
        return orderMapper.getStatistics();
    }
}
