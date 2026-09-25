-- 有可售 SKU 的商品：dish.stock 是所有在售 SKU 库存总和，仅作展示，不是独立库存池。
UPDATE dish d
SET d.stock = (
    SELECT COALESCE(SUM(ps.stock), 0)
    FROM product_sku ps
    WHERE ps.dish_id = d.id AND ps.status = 1
)
WHERE EXISTS (
    SELECT 1 FROM product_sku ps WHERE ps.dish_id = d.id AND ps.status = 1
);
