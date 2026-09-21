package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.entity.Dish;
import com.sky.entity.Category;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.ProductSkuMapper;
import com.sky.mapper.CategoryMapper;
import com.sky.exception.OrderBusinessException;
import com.sky.service.OutboxService;
import com.sky.vo.OrderSubmitVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceSubmitTest {

    @InjectMocks
    private OrderServiceImpl orderService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderDetailMapper orderDetailMapper;

    @Mock
    private ShoppingCartMapper shoppingCartMapper;

    @Mock
    private DishMapper dishMapper;

    @Mock
    private ProductSkuMapper productSkuMapper;

    @Mock
    private AddressBookMapper addressBookMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(1L);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldCreateOrderForFirstSubmission() {
        // 1. 模拟 Redis：当前请求第一次到达，抢到幂等 Key
        when(valueOperations.setIfAbsent(
                anyString(),
                eq("PROCESSING"),
                eq(5L),
                eq(TimeUnit.MINUTES)
        )).thenReturn(true);

        // 2. 模拟地址簿：用户地址存在
        AddressBook addressBook = AddressBook.builder()
                .id(1L)
                .userId(1L)
                .consignee("测试用户")
                .phone("13800000000")
                .provinceName("四川省")
                .cityName("成都市")
                .districtName("郫都区")
                .detail("犀浦镇测试路 1 号")
                .build();

        when(addressBookMapper.getById(1L)).thenReturn(addressBook);

        // 3. 模拟购物车：存在两件商品
        ShoppingCart dishCart = ShoppingCart.builder()
                .dishId(11L)
                .name("宫保鸡丁")
                .number(1)
                .amount(new BigDecimal("18.00"))
                .image("dish.png")
                .build();

        ShoppingCart setmealCart = ShoppingCart.builder()
                .setmealId(21L)
                .name("双人套餐")
                .number(1)
                .amount(new BigDecimal("36.00"))
                .image("setmeal.png")
                .build();

        when(shoppingCartMapper.list(any(ShoppingCart.class)))
                .thenReturn(Arrays.asList(dishCart, setmealCart));
        when(dishMapper.decrementStock(11L, 1)).thenReturn(1);

        // 4. 模拟 MyBatis 插入订单后回填数据库主键
        doAnswer(invocation -> {
            Orders insertedOrder = invocation.getArgument(0);
            insertedOrder.setId(1001L);
            return null;
        }).when(orderMapper).insert(any(Orders.class));

        // 5. 构造下单请求
        OrdersSubmitDTO submitDTO = new OrdersSubmitDTO();
        submitDTO.setAddressBookId(1L);
        submitDTO.setPayMethod(1);
        submitDTO.setAmount(new BigDecimal("54.00"));
        submitDTO.setDeliveryStatus(1);
        submitDTO.setTablewareNumber(2);
        submitDTO.setTablewareStatus(1);
        submitDTO.setPackAmount(0);
        submitDTO.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(30));

        String idempotencyKey = "test-submit-key-001";

        // 6. 执行真正要测试的方法
        OrderSubmitVO result = orderService.submit(submitDTO, idempotencyKey);

        // 7. 检查返回的订单结果
        assertNotNull(result);
        assertEquals(1001L, result.getId());
        // 商品小计 54.00 + 商品服务费 1.00 + 配送费 4.00
        assertEquals(new BigDecimal("59.00"), result.getOrderAmount());

        // 8. 检查是否写入一条订单
        ArgumentCaptor<Orders> orderCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(orderCaptor.capture());

        Orders insertedOrder = orderCaptor.getValue();
        assertEquals(1L, insertedOrder.getUserId());
        assertEquals(idempotencyKey, insertedOrder.getSubmitRequestId());
        assertEquals(Orders.PENDING_PAYMENT, insertedOrder.getStatus());
        assertEquals(Orders.UN_PAID, insertedOrder.getPayStatus());

        // 9. 检查订单明细是否为购物车中的两项
        ArgumentCaptor<List<OrderDetail>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderDetailMapper).batchInsert(detailsCaptor.capture());

        List<OrderDetail> insertedDetails = detailsCaptor.getValue();
        assertEquals(2, insertedDetails.size());
        assertEquals(1001L, insertedDetails.get(0).getOrderId());
        assertEquals(1001L, insertedDetails.get(1).getOrderId());

        // 10. 检查下单成功后是否清空购物车
        verify(shoppingCartMapper).cleanByUserId(1L);
        verify(dishMapper).decrementStock(11L, 1);
    }

    @Test
    void shouldRejectSubmissionWhenProductStockIsInsufficient() {
        when(valueOperations.setIfAbsent(anyString(), eq("PROCESSING"), eq(5L), eq(TimeUnit.MINUTES)))
                .thenReturn(true);
        when(addressBookMapper.getById(1L)).thenReturn(AddressBook.builder()
                .id(1L).userId(1L).consignee("测试用户").phone("13800000000")
                .provinceName("四川省").cityName("成都市").districtName("郫都区").detail("测试地址").build());
        ShoppingCart cart = ShoppingCart.builder()
                .dishId(11L).name("悦选纸巾").number(2).amount(new BigDecimal("19.90")).build();
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(List.of(cart));
        when(dishMapper.decrementStock(11L, 2)).thenReturn(0);

        OrdersSubmitDTO submitDTO = new OrdersSubmitDTO();
        submitDTO.setAddressBookId(1L);
        submitDTO.setAmount(new BigDecimal("19.90"));

        OrderBusinessException exception = assertThrows(OrderBusinessException.class,
                () -> orderService.submit(submitDTO, "test-insufficient-stock-001"));

        assertEquals("商品库存不足或已下架", exception.getMessage());
        verify(orderMapper, never()).insert(any(Orders.class));
        verify(orderDetailMapper, never()).batchInsert(any());
        verify(shoppingCartMapper, never()).cleanByUserId(1L);
    }

    @Test
    void shouldDecrementSkuStockInsteadOfProductStock() {
        when(valueOperations.setIfAbsent(anyString(), eq("PROCESSING"), eq(5L), eq(TimeUnit.MINUTES)))
                .thenReturn(true);
        when(addressBookMapper.getById(1L)).thenReturn(AddressBook.builder()
                .id(1L).userId(1L).consignee("测试用户").phone("13800000000")
                .provinceName("四川省").cityName("成都市").districtName("郫都区").detail("测试地址").build());
        ShoppingCart cart = ShoppingCart.builder().dishId(11L).skuId(101L)
                .dishFlavor("规格:家庭装").name("悦选纸巾").number(1).amount(new BigDecimal("29.90")).build();
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(List.of(cart));
        when(productSkuMapper.decrementStock(101L, 1)).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<Orders>getArgument(0).setId(1002L);
            return null;
        }).when(orderMapper).insert(any(Orders.class));

        OrdersSubmitDTO submitDTO = new OrdersSubmitDTO();
        submitDTO.setAddressBookId(1L);
        submitDTO.setAmount(new BigDecimal("29.90"));
        submitDTO.setPayMethod(1);
        submitDTO.setDeliveryStatus(1);
        submitDTO.setTablewareNumber(1);
        submitDTO.setTablewareStatus(1);
        submitDTO.setPackAmount(0);

        orderService.submit(submitDTO, "test-sku-submit-001");

        verify(productSkuMapper).decrementStock(101L, 1);
        verify(dishMapper, never()).decrementStock(any(), any());
        ArgumentCaptor<List<OrderDetail>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderDetailMapper).batchInsert(detailsCaptor.capture());
        assertEquals(101L, detailsCaptor.getValue().get(0).getSkuId());
        assertEquals("规格:家庭装", detailsCaptor.getValue().get(0).getSkuSnapshot());
    }

    @Test
    void shouldRejectMixedOnsiteServiceAndPhysicalGoodsBeforeStockIsReserved() {
        when(valueOperations.setIfAbsent(anyString(), eq("PROCESSING"), eq(5L), eq(TimeUnit.MINUTES)))
                .thenReturn(true);
        ShoppingCart serviceCart = ShoppingCart.builder().dishId(72L).name("上门家电清洗")
                .number(1).amount(new BigDecimal("188.00")).build();
        ShoppingCart goodsCart = ShoppingCart.builder().dishId(46L).name("原生木浆抽纸")
                .number(1).amount(new BigDecimal("6.00")).build();
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(List.of(serviceCart, goodsCart));
        when(dishMapper.getById(72L)).thenReturn(Dish.builder().id(72L).categoryId(26L).build());
        when(dishMapper.getById(46L)).thenReturn(Dish.builder().id(46L).categoryId(10L).build());
        when(categoryMapper.getById(26L)).thenReturn(Category.builder().id(26L).name("上门服务").build());
        when(categoryMapper.getById(10L)).thenReturn(Category.builder().id(10L).name("日用百货").build());

        OrdersSubmitDTO submitDTO = new OrdersSubmitDTO();
        // 选择自提可绕过地址前置校验，从而准确验证“混单必须拒绝”的规则。
        submitDTO.setDeliveryStatus(2);
        OrderBusinessException exception = assertThrows(OrderBusinessException.class,
                () -> orderService.submit(submitDTO, "test-mixed-service-goods-001"));

        assertEquals("上门服务和其他商品请分开下单", exception.getMessage());
        verify(dishMapper, never()).decrementStock(any(), any());
        verify(productSkuMapper, never()).decrementStock(any(), any());
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void shouldCreatePickupOrderAndPreserveAddressIdForLegacySchemaCompatibility() {
        when(valueOperations.setIfAbsent(anyString(), eq("PROCESSING"), eq(5L), eq(TimeUnit.MINUTES)))
                .thenReturn(true);
        ShoppingCart cart = ShoppingCart.builder().dishId(46L).name("原生木浆抽纸")
                .number(1).amount(new BigDecimal("6.00")).build();
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(List.of(cart));
        when(dishMapper.getById(46L)).thenReturn(Dish.builder().id(46L).categoryId(10L).build());
        when(categoryMapper.getById(10L)).thenReturn(Category.builder().id(10L).name("日用百货").build());
        when(dishMapper.decrementStock(46L, 1)).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<Orders>getArgument(0).setId(1003L);
            return null;
        }).when(orderMapper).insert(any(Orders.class));

        OrdersSubmitDTO submitDTO = new OrdersSubmitDTO();
        submitDTO.setAddressBookId(6L);
        submitDTO.setDeliveryStatus(2);
        submitDTO.setPayMethod(1);
        // 与小程序一致：餐具字段可不传，服务端应安全写入默认值而非抛 BeanUtils 异常。
        submitDTO.setTablewareNumber(null);
        submitDTO.setTablewareStatus(null);

        OrderSubmitVO result = orderService.submit(submitDTO, "test-pickup-without-address-001");

        assertEquals(1003L, result.getId());
        assertEquals(new BigDecimal("7.00"), result.getOrderAmount());
        ArgumentCaptor<Orders> orderCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertEquals(6L, orderCaptor.getValue().getAddressBookId());
        assertEquals("悦选服务点自提", orderCaptor.getValue().getConsignee());
        assertEquals("悦选服务点", orderCaptor.getValue().getAddress());
        assertEquals(0, orderCaptor.getValue().getTablewareNumber());
        assertEquals(1, orderCaptor.getValue().getTablewareStatus());
        verify(addressBookMapper, never()).getById(any());
    }
}
