package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.exception.BaseException;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ProductSku;
import com.sky.entity.ShoppingCart;
import com.sky.entity.FlashSaleActivity;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ProductSkuMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.FlashSaleActivityMapper;
import com.sky.mapper.FlashSaleUserQuotaMapper;
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
    @Autowired
    FlashSaleActivityMapper flashSaleActivityMapper;
    @Autowired
    FlashSaleUserQuotaMapper flashSaleUserQuotaMapper;
    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    public void add(ShoppingCartDTO shoppingCartDTO){
        // 不能只相信前端对象是否残留活动 id。普通商品入口显式声明后，
        // 即使页面复用了旧对象，也绝不会写入或合并到限时购购物车行。
        if (Boolean.TRUE.equals(shoppingCartDTO.getNormalPurchase())) {
            shoppingCartDTO.setFlashSaleActivityId(null);
        }
        //查询购物车看是否存在当前数据
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.listSameSaleItem(shoppingCart);
        // 加购不占用活动库存/额度，只做只读预检查。真正占用必须在提交订单事务内完成，
        // 否则用户长期不结算会把活动名额锁死。
        validateFlashSaleQuotaBeforeAdd(shoppingCartDTO, userId);
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
                if (dish == null) {
                    throw new BaseException("商品不存在");
                }
                if (shoppingCartDTO.getSkuId() != null) {
                    ProductSku sku = productSkuMapper.getById(shoppingCartDTO.getSkuId());
                    if (sku == null || !dishId.equals(sku.getDishId()) || !Integer.valueOf(1).equals(sku.getStatus())) {
                        throw new BaseException("商品规格不可售");
                    }
                    shoppingCart.setSkuId(sku.getId());
                    shoppingCart.setDishFlavor(sku.getSpecName() + ":" + sku.getSpecValue());
                    shoppingCart.setAmount(sku.getPrice());
                    applyFlashSalePrice(shoppingCartDTO, shoppingCart, sku);
                } else if (shoppingCartDTO.getDishFlavor() != null && !shoppingCartDTO.getDishFlavor().isBlank()) {
                    String specValue = shoppingCartDTO.getDishFlavor();
                    int separator = specValue.lastIndexOf(':');
                    if (separator >= 0) {
                        specValue = specValue.substring(separator + 1);
                    }
                    ProductSku sku = productSkuMapper.getByDishIdAndSpecValue(dishId, specValue);
                    if (sku == null) {
                        throw new BaseException("商品规格不可售");
                    }
                    shoppingCart.setSkuId(sku.getId());
                    shoppingCart.setDishFlavor(sku.getSpecName() + ":" + sku.getSpecValue());
                    shoppingCart.setAmount(sku.getPrice());
                }
                // 商品一旦配置 SKU，SKU 价格就是唯一的成交价。拒绝绕过规格直接加购，
                // 防止客户端再使用已置空的商品级价格，或伪造一个错误的统一价格。
                if (shoppingCart.getAmount() == null && !productSkuMapper.listByDishId(dishId).isEmpty()) {
                    throw new BaseException("请选择商品规格");
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

    /**
     * 加购时仅用于展示活动价；提交订单时还会重新检查时间、配额和真实库存，
     * 因此用户无法靠篡改请求金额获得过期优惠。
     */
    private void applyFlashSalePrice(ShoppingCartDTO dto, ShoppingCart cart, ProductSku sku) {
        if (dto.getFlashSaleActivityId() == null) {
            return;
        }
        FlashSaleActivity activity = flashSaleActivityMapper.getById(dto.getFlashSaleActivityId());
        LocalDateTime now = LocalDateTime.now();
        if (activity == null || !sku.getId().equals(activity.getSkuId())
                || !Integer.valueOf(1).equals(activity.getStatus())
                || now.isBefore(activity.getStartTime()) || !now.isBefore(activity.getEndTime())
                || activity.getSoldStock() >= activity.getActivityStock()) {
            throw new BaseException("限时购活动未开始、已结束或库存不足");
        }
        cart.setFlashSaleActivityId(activity.getId());
        cart.setAmount(activity.getSalePrice());
    }

    private void validateFlashSaleQuotaBeforeAdd(ShoppingCartDTO dto, Long userId) {
        if (dto.getFlashSaleActivityId() == null) {
            return;
        }
        FlashSaleActivity activity = flashSaleActivityMapper.getById(dto.getFlashSaleActivityId());
        if (activity == null) {
            throw new BaseException("限时购活动不存在");
        }
        ShoppingCart condition = new ShoppingCart();
        condition.setUserId(userId);
        condition.setFlashSaleActivityId(activity.getId());
        int inCart = shoppingCartMapper.list(condition).stream()
                .mapToInt(ShoppingCart::getNumber).sum();
        Integer reserved = flashSaleUserQuotaMapper.getReservedQuantity(activity.getId(), userId);
        int alreadyUsedOrSelected = inCart + (reserved == null ? 0 : reserved);
        if (alreadyUsedOrSelected + 1 > activity.getPerUserLimit()) {
            throw new BaseException("已超过购买上限，本次活动每人最多购买" + activity.getPerUserLimit() + "件");
        }
    }
}
