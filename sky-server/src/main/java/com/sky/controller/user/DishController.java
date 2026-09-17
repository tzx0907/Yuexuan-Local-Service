package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "用户端-商品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
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
        //动态设置查询条件
        String key = "dish_" + categoryId;
        //从缓存中查询
        //如果存在，直接返回
        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
        if (list != null) {
            log.info("从缓存中查询商品数据");
            return Result.success(list);
        }
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);
        // 查询上架中的商品
         list = dishService.listWithFlavor(dish);
        log.info("从数据库中查询商品数据");
        //将查询结果写入缓存
        redisTemplate.opsForValue().set(key, list);
        return Result.success(list);
    }

}
