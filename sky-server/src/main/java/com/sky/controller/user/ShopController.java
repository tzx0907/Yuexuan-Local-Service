package com.sky.controller.user;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("UserShopController")
@RequestMapping("/user/shop")
@Slf4j
@Api(tags = "用户端-门店状态接口")
public class ShopController {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @GetMapping("/status")
    @ApiOperation("获取门店营业状态")
    public Result<Integer> getStatus(){
        Integer status = (Integer) redisTemplate.opsForValue().get("SHOP_STATUS");
        log.info("获取营业状态：{}",status == 1 ? "营业中" : "打烊中");
        // 尚未人工设置时默认服务中；只有管理端明确设为 0 才禁止新订单。
        return Result.success(status == null ? 1 : status);
    }
}
