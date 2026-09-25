package com.sky.controller.admin;

import com.sky.dto.ProductSkuDTO;
import com.sky.entity.ProductSku;
import com.sky.mapper.ProductSkuMapper;
import com.sky.mapper.DishMapper;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/product-sku")
@Api(tags = "商品 SKU 管理接口")
public class ProductSkuController {
    @Autowired
    private ProductSkuMapper productSkuMapper;
    @Autowired
    private DishMapper dishMapper;

    @PostMapping
    @ApiOperation("新增商品 SKU")
    public Result<Long> create(@RequestBody ProductSkuDTO dto) {
        ProductSku sku = new ProductSku();
        BeanUtils.copyProperties(dto, sku);
        if (sku.getStatus() == null) {
            sku.setStatus(1);
        }
        if (sku.getStock() == null) {
            sku.setStock(0);
        }
        productSkuMapper.insert(sku);
        dishMapper.syncStockFromSkus(sku.getDishId());
        return Result.success(sku.getId());
    }

    @PutMapping
    @ApiOperation("修改商品 SKU")
    public Result<Void> update(@RequestBody ProductSkuDTO dto) {
        ProductSku sku = new ProductSku();
        BeanUtils.copyProperties(dto, sku);
        productSkuMapper.update(sku);
        ProductSku saved = productSkuMapper.getById(sku.getId());
        if (saved != null) {
            dishMapper.syncStockFromSkus(saved.getDishId());
        }
        return Result.success();
    }

    @GetMapping("/list")
    @ApiOperation("查询商品 SKU 列表")
    public Result<List<ProductSku>> list(Long dishId) {
        return Result.success(productSkuMapper.listByDishId(dishId));
    }
}
