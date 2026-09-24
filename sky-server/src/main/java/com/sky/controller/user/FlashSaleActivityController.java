package com.sky.controller.user;

import com.sky.vo.FlashSaleActivityVO;
import com.sky.result.Result;
import com.sky.service.FlashSaleActivityService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userFlashSaleActivityController")
@RequestMapping("/user/flash-sale")
@Api(tags = "用户端-限时购")
public class FlashSaleActivityController {
    private final FlashSaleActivityService activityService;
    public FlashSaleActivityController(FlashSaleActivityService activityService) { this.activityService = activityService; }

    @GetMapping("/active")
    @ApiOperation("查询正在进行的限时购")
    public Result<List<FlashSaleActivityVO>> active() { return Result.success(activityService.listActive()); }
}
