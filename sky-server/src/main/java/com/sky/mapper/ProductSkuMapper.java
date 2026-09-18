package com.sky.mapper;

import com.sky.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductSkuMapper {
    void insert(ProductSku productSku);
    void update(ProductSku productSku);
    ProductSku getById(Long id);
    List<ProductSku> listByDishId(Long dishId);
    int decrementStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);
}
