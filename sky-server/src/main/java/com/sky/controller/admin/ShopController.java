package com.sky.controller.admin;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("AdminShopController")
@RequestMapping("/admin/shop")

@Slf4j
@Api(tags = "门店运营状态接口")
public class ShopController {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @PutMapping("/{status}")
    @ApiOperation("设置门店营业状态")
    public Result setStatus(@PathVariable Integer status){
        if (status == null || (status != 0 && status != 1)) {
            return Result.error("服务状态仅支持 0（暂停接单）或 1（服务中）");
        }
        log.info("设置营业状态：{}",status == 1 ? "营业中" : "打烊中");
        redisTemplate.opsForValue().set("SHOP_STATUS",status);
        return Result.success();
    }
    @GetMapping("/status")
    @ApiOperation("获取门店营业状态")
    public Result<Integer> getStatus(){
        Integer status = (Integer) redisTemplate.opsForValue().get("SHOP_STATUS");
        log.info("获取营业状态：{}",status == 1 ? "营业中" : "打烊中");
        return Result.success(status == null ? 1 : status);
    }
}
