package com.sky.service;

import com.sky.dto.FlashSaleActivityDTO;
import com.sky.entity.FlashSaleActivity;

import java.util.List;

public interface FlashSaleActivityService {
    Long create(FlashSaleActivityDTO dto);
    void update(FlashSaleActivityDTO dto);
    List<FlashSaleActivity> listActive();
}
