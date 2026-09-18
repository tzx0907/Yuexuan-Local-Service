package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ProductSku;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ProductSkuMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {
    @Autowired
    ShoppingCartMapper shoppingCartMapper;
    @Autowired
    DishMapper dishMapper;
    @Autowired
    SetmealMapper setmealMapper;
    @Autowired
    ProductSkuMapper productSkuMapper;
    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    public void add(ShoppingCartDTO shoppingCartDTO){
        //查询购物车看是否存在当前数据
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        //如果存在 num++
        if(shoppingCartList != null && !shoppingCartList.isEmpty()){
            ShoppingCart cart = shoppingCartList.get(0);
            cart.setNumber(cart.getNumber() + 1);
            shoppingCartMapper.update(cart);
        }else{
            //如果不存在 添加数据
            Long dishId = shoppingCartDTO.getDishId();
            if(dishId!=null){
                Dish dish = dishMapper.getById(dishId);
                if (shoppingCartDTO.getSkuId() != null) {
                    ProductSku sku = productSkuMapper.getById(shoppingCartDTO.getSkuId());
                    if (sku == null || !dishId.equals(sku.getDishId()) || !Integer.valueOf(1).equals(sku.getStatus())) {
                        throw new IllegalArgumentException("商品规格不可售");
                    }
                    shoppingCart.setSkuId(sku.getId());
                    shoppingCart.setDishFlavor(sku.getSpecName() + ":" + sku.getSpecValue());
                    shoppingCart.setAmount(sku.getPrice());
                }
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setName(dish.getName());
                if (shoppingCart.getAmount() == null) {
                    shoppingCart.setAmount(dish.getPrice());
                }
            }else{
                Setmeal setmeal = setmealMapper.getById(shoppingCartDTO.getSetmealId());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setAmount(setmeal.getPrice());
            }
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }
    }
    /**
     * 单独减少购物车数量
     * @param shoppingCartDTO
     */
    public void sub(ShoppingCartDTO shoppingCartDTO){
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if(shoppingCartList == null || shoppingCartList.isEmpty()){
            return;
        }
        ShoppingCart cart = shoppingCartList.get(0);
        cart.setNumber(cart.getNumber() - 1);
        if(cart.getNumber() != 0){
            shoppingCartMapper.update(cart);
        }else {
            shoppingCartMapper.delete(cart.getId());
        }
    }

    /**
     * 购物车列表
     * @return
     */
    @Override
    public List<ShoppingCart> listAll() {
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(BaseContext.getCurrentId());
        return shoppingCartMapper.list(shoppingCart);
    }
    /**
     * 清空购物车
     */
    public void clear() {
        shoppingCartMapper.cleanByUserId(BaseContext.getCurrentId());
    }
}
