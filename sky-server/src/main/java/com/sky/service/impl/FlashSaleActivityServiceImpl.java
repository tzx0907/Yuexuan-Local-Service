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

    public FlashSaleActivityServiceImpl(FlashSaleActivityMapper activityMapper, ProductSkuMapper productSkuMapper) {
        this.activityMapper = activityMapper;
        this.productSkuMapper = productSkuMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FlashSaleActivityDTO dto) {
        FlashSaleActivity activity = toValidatedActivity(dto, false);
        if (activityMapper.insert(activity) != 1 || activity.getId() == null) {
            throw new OrderBusinessException("限时购活动创建失败，未写入数据库");
        }
        return activity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FlashSaleActivityDTO dto) {
        if (dto.getId() == null || activityMapper.getById(dto.getId()) == null) {
            throw new OrderBusinessException("限时购活动不存在");
        }
        FlashSaleActivity activity = toValidatedActivity(dto, true);
        if (activityMapper.update(activity) != 1) {
            throw new OrderBusinessException("限时购活动更新失败");
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

    private FlashSaleActivity toValidatedActivity(FlashSaleActivityDTO dto, boolean updating) {
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
        if (!updating) {
            activity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        }
        return activity;
    }
}
