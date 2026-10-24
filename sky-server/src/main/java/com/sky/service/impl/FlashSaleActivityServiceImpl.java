package com.sky.service.impl;

import com.sky.dto.FlashSaleActivityDTO;
import com.sky.entity.FlashSaleActivity;
import com.sky.entity.ProductSku;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.FlashSaleActivityMapper;
import com.sky.mapper.ProductSkuMapper;
import com.sky.service.FlashSaleActivityService;
import com.sky.vo.FlashSaleActivityVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FlashSaleActivityServiceImpl implements FlashSaleActivityService {
    private final FlashSaleActivityMapper activityMapper;
    private final ProductSkuMapper productSkuMapper;
    private final RedisFlashSaleStockGuard stockGuard;

    public FlashSaleActivityServiceImpl(FlashSaleActivityMapper activityMapper, ProductSkuMapper productSkuMapper,
                                        RedisFlashSaleStockGuard stockGuard) {
        this.activityMapper = activityMapper;
        this.productSkuMapper = productSkuMapper;
        this.stockGuard = stockGuard;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FlashSaleActivityDTO dto) {
        FlashSaleActivity activity = toValidatedActivity(dto, null);
        if (activityMapper.insert(activity) != 1 || activity.getId() == null) {
            throw new OrderBusinessException("限时购活动创建失败，未写入数据库");
        }
        // 新建活动立刻预热，否则要等应用重启或对账任务，期间会一直降级为纯 MySQL 判定。
        if (stockGuard.isEnabled() && Integer.valueOf(1).equals(activity.getStatus())) {
            stockGuard.warmUp(activity.getId(), activity.getActivityStock());
        }
        return activity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FlashSaleActivityDTO dto) {
        FlashSaleActivity existing = dto.getId() == null ? null : activityMapper.getById(dto.getId());
        if (existing == null) {
            throw new OrderBusinessException("限时购活动不存在");
        }
        FlashSaleActivity activity = toValidatedActivity(dto, existing);
        if (activity.getActivityStock() < existing.getSoldStock()) {
            throw new OrderBusinessException("活动总配额不能小于已售数量 " + existing.getSoldStock());
        }
        if (activityMapper.update(activity) != 1) {
            throw new OrderBusinessException("限时购活动更新失败");
        }
        if (stockGuard.isEnabled()) {
            if (!Integer.valueOf(1).equals(activity.getStatus())) {
                // 活动被停用：清掉闸门，后续请求直接降级到 MySQL 判定（MySQL 会因 status 拒绝）
                stockGuard.release(existing.getId(), 0L, 0);
            } else {
                // 只按配额差额调整，不整体覆盖：覆盖会抹掉正在进行的在途预扣。
                stockGuard.adjustStock(existing.getId(), activity.getActivityStock() - existing.getActivityStock());
            }
        }
    }

    @Override
    public List<FlashSaleActivityVO> listActive() {
        return activityMapper.listActive();
    }

    @Override
    public List<FlashSaleActivityVO> listAll() { return activityMapper.listAll(); }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (id == null || (status == null || (status != 0 && status != 1)) || activityMapper.updateStatus(id, status) != 1) {
            throw new OrderBusinessException("限时购活动不存在或状态不合法");
        }
    }

    /**
     * @param existing 非 null 表示更新：缺失字段沿用原值，避免动态更新把 status 写成 NULL。
     */
    private FlashSaleActivity toValidatedActivity(FlashSaleActivityDTO dto, FlashSaleActivity existing) {
        if (dto.getSkuId() == null || dto.getSalePrice() == null || dto.getActivityStock() == null
                || dto.getPerUserLimit() == null || dto.getStartTime() == null || dto.getEndTime() == null) {
            throw new OrderBusinessException("限时购活动参数不完整");
        }
        ProductSku sku = productSkuMapper.getById(dto.getSkuId());
        if (sku == null || !Integer.valueOf(1).equals(sku.getStatus())) {
            throw new OrderBusinessException("活动 SKU 不存在或已下架");
        }
        if (dto.getSalePrice().compareTo(BigDecimal.ZERO) < 0 || dto.getSalePrice().compareTo(sku.getPrice()) > 0) {
            throw new OrderBusinessException("活动价必须在 0 到 SKU 原价之间");
        }
        if (dto.getActivityStock() <= 0 || dto.getPerUserLimit() <= 0 || !dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new OrderBusinessException("活动库存、限购数量或时间范围不合法");
        }
        FlashSaleActivity activity = new FlashSaleActivity();
        BeanUtils.copyProperties(dto, activity);
        if (existing == null) {
            activity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        } else {
            // 更新时 status 缺省沿用原值：SQL 是固定 set status=#{status}，写 null 会违反 NOT NULL。
            activity.setStatus(dto.getStatus() == null ? existing.getStatus() : dto.getStatus());
        }
        return activity;
    }
}
