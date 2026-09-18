package com.sky.controller.user;

import com.sky.entity.ProductSku;
import com.sky.mapper.ProductSkuMapper;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController("userProductSkuController")
@RequestMapping("/user/product-sku")
@Api(tags = "用户端-商品 SKU 浏览接口")
public class ProductSkuController {
    @Autowired
    private ProductSkuMapper productSkuMapper;

    @GetMapping("/list")
    @ApiOperation("查询商品可售 SKU")
    public Result<List<ProductSku>> list(Long dishId) {
        List<ProductSku> sellableSkus = productSkuMapper.listByDishId(dishId).stream()
                .filter(sku -> Integer.valueOf(1).equals(sku.getStatus()))
                .collect(Collectors.toList());
        return Result.success(sellableSkus);
    }
}
