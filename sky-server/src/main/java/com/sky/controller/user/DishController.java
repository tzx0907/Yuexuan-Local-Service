package com.sky.controller.user;

import com.sky.constant.ProductCacheKey;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.mapper.ProductSkuMapper;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "用户端-商品浏览接口")
public class DishController {
    private static final long PRODUCT_CACHE_TTL_MINUTES = 30;
    private static final long PRODUCT_CACHE_TTL_JITTER_MINUTES = 10;
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ProductSkuMapper productSkuMapper;
    /**
     * 根据分类 ID 查询商品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类 ID 查询商品")
    public Result<List<DishVO>> list(Long categoryId) {
        log.info("根据分类 ID 查询商品，分类 ID：{}", categoryId);
        // ProductCacheKey 本身已包含 yuexuan:v2 版本前缀。用户端读取与
        // 管理端商品变更后的精确失效必须使用同一个 Key，不能在此额外拼接版本号。
        String key = ProductCacheKey.productListByCategory(categoryId);
        // Cache Aside：先查缓存，命中后不再访问数据库。
        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
        if (list != null) {
            log.info("从缓存中查询商品数据");
            return Result.success(list);
        }
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);
        // 缓存未命中，回源数据库查询上架商品。
        list = dishService.listWithFlavor(dish);
        list.forEach(product -> product.setSkus(productSkuMapper.listByDishId(product.getId())));
        log.info("从数据库中查询商品数据");
        // 加入随机抖动，避免同一批缓存 Key 在固定时间同时过期。
        long ttlMinutes = PRODUCT_CACHE_TTL_MINUTES
                + ThreadLocalRandom.current().nextLong(PRODUCT_CACHE_TTL_JITTER_MINUTES + 1);
        redisTemplate.opsForValue().set(key, list, ttlMinutes, TimeUnit.MINUTES);
        return Result.success(list);
    }

}
