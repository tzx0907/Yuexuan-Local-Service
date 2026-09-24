package com.sky.controller.user;

import com.sky.constant.ProductCacheKey;
import com.sky.entity.Dish;
import com.sky.entity.ProductSku;
import com.sky.mapper.ProductSkuMapper;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证用户端商品浏览采用 Cache Aside：先读 Redis，未命中时才查询数据库并回填缓存。
 */
@ExtendWith(MockitoExtension.class)
class ProductBrowseCacheTest {

    @InjectMocks
    private DishController dishController;

    @Mock
    private DishService dishService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ProductSkuMapper productSkuMapper;

    @Test
    void shouldReturnCachedProductsWithoutQueryingDatabase() {
        Long categoryId = 10L;
        List<DishVO> cachedProducts = Collections.singletonList(DishVO.builder().id(1L).name("悦选纸巾").build());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductCacheKey.productListByCategory(categoryId))).thenReturn(cachedProducts);

        List<DishVO> result = dishController.list(categoryId).getData();

        assertEquals(cachedProducts, result);
        verify(dishService, never()).listWithFlavor(any(Dish.class));
        verify(valueOperations, never()).set(any(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void shouldLoadFromDatabaseAndCacheResultOnCacheMiss() {
        Long categoryId = 10L;
        List<DishVO> databaseProducts = Collections.singletonList(DishVO.builder().id(1L).name("悦选纸巾").build());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductCacheKey.productListByCategory(categoryId))).thenReturn(null);
        when(dishService.listWithFlavor(any(Dish.class))).thenReturn(databaseProducts);
        when(productSkuMapper.listByDishId(1L)).thenReturn(Collections.singletonList(
                ProductSku.builder().id(101L).dishId(1L).specName("规格").specValue("标准装").build()));

        List<DishVO> result = dishController.list(categoryId).getData();

        assertEquals(databaseProducts, result);
        assertEquals(1, result.get(0).getSkus().size());
        assertEquals("标准装", result.get(0).getSkus().get(0).getSpecValue());
        ArgumentCaptor<Dish> dishCaptor = ArgumentCaptor.forClass(Dish.class);
        verify(dishService).listWithFlavor(dishCaptor.capture());
        assertEquals(categoryId, dishCaptor.getValue().getCategoryId());

        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(valueOperations).set(
                eq(ProductCacheKey.productListByCategory(categoryId)),
                eq(databaseProducts),
                ttlCaptor.capture(),
                eq(TimeUnit.MINUTES)
        );
        assertEquals(true, ttlCaptor.getValue() >= 30 && ttlCaptor.getValue() <= 40);
    }
}
