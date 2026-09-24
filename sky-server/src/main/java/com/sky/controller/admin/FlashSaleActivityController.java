package com.sky.controller.admin;

import com.sky.dto.FlashSaleActivityDTO;
import com.sky.result.Result;
import com.sky.service.FlashSaleActivityService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.sky.entity.FlashSaleActivity;
import com.sky.mapper.ProductSkuMapper;
import com.sky.vo.FlashSaleActivityVO;
import com.sky.vo.ProductSkuSearchVO;

@RestController
@RequestMapping("/admin/flash-sale")
@Api(tags = "管理端-限时购活动")
public class FlashSaleActivityController {
    private final FlashSaleActivityService activityService;
    private final ProductSkuMapper productSkuMapper;

    public FlashSaleActivityController(FlashSaleActivityService activityService, ProductSkuMapper productSkuMapper) { this.activityService = activityService; this.productSkuMapper = productSkuMapper; }

    @PostMapping
    @ApiOperation("创建限时购活动")
    public Result<Long> create(@RequestBody FlashSaleActivityDTO dto) { return Result.success(activityService.create(dto)); }

    @PutMapping
    @ApiOperation("修改限时购活动")
    public Result<Void> update(@RequestBody FlashSaleActivityDTO dto) { activityService.update(dto); return Result.success(); }

    @GetMapping("/list")
    @ApiOperation("查询全部限时购活动")
    public Result<List<FlashSaleActivityVO>> list() { return Result.success(activityService.listAll()); }

    @GetMapping("/sku-options")
    @ApiOperation("按商品名或规格模糊查询可选 SKU")
    public Result<List<ProductSkuSearchVO>> skuOptions(@RequestParam(required = false) String keyword) {
        return Result.success(productSkuMapper.searchSellable(keyword));
    }

    @PutMapping("/{id}/status/{status}")
    @ApiOperation("启用或停用限时购活动")
    public Result<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        activityService.updateStatus(id, status);
        return Result.success();
    }
}
