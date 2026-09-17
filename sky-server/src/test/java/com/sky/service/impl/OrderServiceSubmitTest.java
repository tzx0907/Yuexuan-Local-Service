package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
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
    private AddressBookMapper addressBookMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

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
        assertEquals(new BigDecimal("54.00"), result.getOrderAmount());

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
    }
}
