package com.sky.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口的按用户访问频率限制。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Redis Key 中用于区分业务场景的名称。 */
    String key();

    /** 一个统计窗口内允许通过的最大请求数。 */
    int limit();

    /** 统计窗口时长，单位为秒。 */
    int windowSeconds();
}
