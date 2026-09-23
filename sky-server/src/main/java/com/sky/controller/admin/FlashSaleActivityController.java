package com.sky.controller.admin;

import com.sky.dto.FlashSaleActivityDTO;
import com.sky.result.Result;
import com.sky.service.FlashSaleActivityService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/flash-sale")
@Api(tags = "管理端-限时购活动")
public class FlashSaleActivityController {
    private final FlashSaleActivityService activityService;

    public FlashSaleActivityController(FlashSaleActivityService activityService) { this.activityService = activityService; }

    @PostMapping
    @ApiOperation("创建限时购活动")
    public Result<Long> create(@RequestBody FlashSaleActivityDTO dto) { return Result.success(activityService.create(dto)); }

    @PutMapping
    @ApiOperation("修改限时购活动")
    public Result<Void> update(@RequestBody FlashSaleActivityDTO dto) { activityService.update(dto); return Result.success(); }
}
