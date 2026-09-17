package com.sky.controller.admin;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/setmeal")
@Slf4j
@Api(tags = "商品组合管理接口")
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    @PostMapping
    @ApiOperation("新增商品组合")
    //新增套餐默认停售 不用缓存
    public Result save(@RequestBody SetmealDTO setmealDTO) {
        log.info("新增商品组合：{}", setmealDTO);
        setmealService.saveWithDish(setmealDTO);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("商品组合分页查询")
    public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO) {
        log.info("商品组合分页查询：{}", setmealPageQueryDTO);
        PageResult pageResult = setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @ApiOperation("根据 ID 查询商品组合")
    public Result<SetmealVO> getById(@PathVariable Long id) {
        log.info("根据 ID 查询商品组合：{}", id);
        SetmealVO setmealVO = setmealService.getByIdWithDish(id);
        return Result.success(setmealVO);
    }

    @PutMapping
    @ApiOperation("修改商品组合")
    @CacheEvict(value = "setmealCache", allEntries = true)
    public Result<?> update(@RequestBody SetmealDTO setmealDTO) {
        log.info("修改商品组合：{}", setmealDTO);
        setmealService.updateWithDish(setmealDTO);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @ApiOperation("商品组合上架下架")
    @CacheEvict(value = "setmealCache", allEntries = true)
    public Result<?> startOrStop(@PathVariable Integer status, Long id) {
        log.info("商品组合上架下架：{}, {}", status, id);
        setmealService.startOrStop(status, id);
        return Result.success();
    }

    @DeleteMapping
    @ApiOperation("批量删除商品组合")
    @CacheEvict(value = "setmealCache", allEntries = true)
    public Result<?> delete(@RequestParam(required = false) List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("删除参数不能为空");
        }
        log.info("批量删除商品组合：{}", ids);
        setmealService.delete(ids);
        return Result.success();
    }
}
