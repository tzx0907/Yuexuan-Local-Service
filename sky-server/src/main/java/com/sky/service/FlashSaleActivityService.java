package com.sky.service;

import com.sky.dto.FlashSaleActivityDTO;
import com.sky.vo.FlashSaleActivityVO;

import java.util.List;

public interface FlashSaleActivityService {
    Long create(FlashSaleActivityDTO dto);
    void update(FlashSaleActivityDTO dto);
    List<FlashSaleActivityVO> listActive();
    List<FlashSaleActivityVO> listAll();
    void updateStatus(Long id, Integer status);
}
