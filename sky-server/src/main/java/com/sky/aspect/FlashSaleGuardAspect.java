package com.sky.aspect;

import com.sky.annotation.FlashSaleGuard;
import com.sky.context.BaseContext;
import com.sky.entity.ShoppingCart;
import com.sky.exception.RateLimitException;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.FlashSaleRequestGuard;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 只负责限时购请求削峰。活动库存、累计限购和创建订单仍在 OrderService 的事务内完成。
 */
@Aspect
@Component
public class FlashSaleGuardAspect {
    private final ShoppingCartMapper shoppingCartMapper;
    private final FlashSaleRequestGuard flashSaleRequestGuard;

    public FlashSaleGuardAspect(ShoppingCartMapper shoppingCartMapper, FlashSaleRequestGuard flashSaleRequestGuard) {
        this.shoppingCartMapper = shoppingCartMapper;
        this.flashSaleRequestGuard = flashSaleRequestGuard;
    }

    @Around("@annotation(flashSaleGuard)")
    public Object guard(ProceedingJoinPoint joinPoint, FlashSaleGuard flashSaleGuard) throws Throwable {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new RateLimitException("未获取到当前用户，无法处理限时购请求");
        }
        List<ShoppingCart> carts = shoppingCartMapper.list(ShoppingCart.builder().userId(userId).build());
        for (ShoppingCart cart : carts) {
            if (cart.getFlashSaleActivityId() != null
                    && !flashSaleRequestGuard.tryAcquire(cart.getFlashSaleActivityId(), userId)) {
                throw new RateLimitException("抢购操作过于频繁，请稍后再试");
            }
        }
        return joinPoint.proceed();
    }
}
