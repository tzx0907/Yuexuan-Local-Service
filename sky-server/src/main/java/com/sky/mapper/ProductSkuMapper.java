package com.sky.mapper;

import com.sky.entity.ProductSku;
import com.sky.vo.ProductSkuSearchVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductSkuMapper {
    void insert(ProductSku productSku);
    void update(ProductSku productSku);
    ProductSku getById(Long id);
    ProductSku getByDishIdAndSpecValue(@Param("dishId") Long dishId, @Param("specValue") String specValue);
    List<ProductSku> listByDishId(Long dishId);
    List<ProductSkuSearchVO> searchSellable(@Param("keyword") String keyword);
    int decrementStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);
    int incrementStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);
}
