package com.sky.constant;

/**
 * 悦选商品浏览缓存 Key 约定。
 *
 * 当前内部实体仍沿用 Dish，但对外缓存语义统一使用 product，避免新的 Redis Key
 * 继续绑定历史课程项目命名。
 */
public final class ProductCacheKey {

    private static final String PRODUCT_LIST_BY_CATEGORY_PREFIX = "yuexuan:product:list:";

    private ProductCacheKey() {
    }

    public static String productListByCategory(Long categoryId) {
        return PRODUCT_LIST_BY_CATEGORY_PREFIX + categoryId;
    }
}
