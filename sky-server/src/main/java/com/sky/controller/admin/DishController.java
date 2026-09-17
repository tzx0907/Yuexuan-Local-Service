package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/admin/dish")
@Api(tags = "商品管理接口")
@Slf4j
public class DishController {
    @Autowired
    DishService dishService;
    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    @ApiOperation("新增商品")
    @PostMapping
    public Result<?> save(@RequestBody DishDTO dishDTO){
        log.info("新增商品:{}", dishDTO);
        dishService.saveWithFlavor(dishDTO);
        return Result.success();
    }
    @ApiOperation("商品分页查询")
    @GetMapping("/page")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO){
        log.info("分页查询:{}", dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }
    @ApiOperation("根据 ID 查询商品")
    @GetMapping("/{id}")
    public Result<DishVO> getById(@PathVariable Long id){
        log.info("根据 id 查询商品:{}", id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }
    @ApiOperation("商品上架下架")
    @PostMapping("/status/{status}")
    public Result<?> startOrStop(@PathVariable Integer status, Long id){
        log.info("商品上架下架:{},{}", status, id);
        dishService.startOrStop(status, id);
        cleanCache("dish_*");
        return Result.success();
    }
    @ApiOperation("修改商品")
    @PutMapping
    public Result<?> update(@RequestBody DishDTO dishDTO){
        log.info("修改商品:{}", dishDTO);
        dishService.update(dishDTO);
        cleanCache("dish_*");
        return Result.success();
    }
    @ApiOperation("根据分类 ID 查询商品")
    @GetMapping("/list")
    public Result<List<Dish>> list(Long categoryId){
        log.info("根据分类 ID 查询商品:{}", categoryId);
        List<Dish> list = dishService.list(categoryId);
        return Result.success(list);
    }
    @ApiOperation("批量删除商品")
    @DeleteMapping
    public Result<?> delete(@RequestParam(required = false) List<Long> ids){
        if(ids == null || ids.isEmpty()){
            log.error("批量删除商品失败：参数 ids 为空");
            return Result.error("删除参数不能为空");
        }
        log.info("批量删除商品:{}", ids);
        dishService.delete(ids);
        return Result.success();
    }
    private void cleanCache(String pattern){
        log.info("根据pattern删除缓存：{}", pattern);
        Set<String> keys = redisTemplate.keys(pattern);
        redisTemplate.delete(keys);
    }
}
