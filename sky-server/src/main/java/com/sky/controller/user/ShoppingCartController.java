package com.sky.controller.user;

import com.sky.dto.ShoppingCartDTO;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/shoppingCart")
@Slf4j
@Api(tags="C端购物车接口")
public class ShoppingCartController {
    @Autowired
    private ShoppingCartService shoppingCartService;
    @PostMapping("/add")
    @ApiOperation("用户添加购物车")
    public Result<?> add(@RequestBody ShoppingCartDTO shoppingCartDTO){
        log.info("用户添加购物车，购物车数据：{}", shoppingCartDTO);
        shoppingCartService.add(shoppingCartDTO);
        return Result.success();
    }
    @PostMapping("/sub")
    @ApiOperation("用户减少购物车中的一个商品")
    public Result<?> sub(@RequestBody ShoppingCartDTO shoppingCartDTO){
        log.info("用户减少购物车，购物车数据：{}", shoppingCartDTO);
        shoppingCartService.sub(shoppingCartDTO);
        return Result.success();
    }
    @GetMapping("list")
    @ApiOperation("用户查看购物车")
    public Result<?> list(){
        log.info("用户查看购物车");
        return Result.success(shoppingCartService.listAll());
    }
    @DeleteMapping("/clean")
    @ApiOperation("用户清空购物车")
    public Result<?> clear(){
        log.info("用户清空购物车");
        shoppingCartService.clear();
        return Result.success();
    }
}
