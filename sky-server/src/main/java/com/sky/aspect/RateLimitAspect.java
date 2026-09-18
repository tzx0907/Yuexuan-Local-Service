package com.sky.aspect;

import com.sky.annotation.RateLimit;
import com.sky.context.BaseContext;
import com.sky.exception.RateLimitException;
import com.sky.service.RateLimitService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 在 JWT 拦截器确认用户身份后，按“业务场景 + 用户 ID”进行限流。
 */
@Aspect
@Component
public class RateLimitAspect {

    private final RateLimitService rateLimitService;

    public RateLimitAspect(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Pointcut("@annotation(com.sky.annotation.RateLimit)")
    public void rateLimitedMethod() {
    }

    @Around("rateLimitedMethod() && @annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new RateLimitException("未获取到当前用户，无法处理请求");
        }
        boolean allowed = rateLimitService.tryAcquire(rateLimit.key(), userId,
                rateLimit.limit(), rateLimit.windowSeconds());
        if (!allowed) {
            throw new RateLimitException("操作过于频繁，请稍后再试");
        }
        return joinPoint.proceed();
    }
}
