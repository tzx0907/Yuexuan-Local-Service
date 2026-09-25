-- 组合商品固定到具体 SKU，确保扣减、回补和订单展示都对应同一规格。
ALTER TABLE setmeal_dish
    ADD COLUMN sku_id BIGINT NULL COMMENT '组合中指定的商品SKU' AFTER dish_id,
    ADD COLUMN sku_snapshot VARCHAR(128) NULL COMMENT '组合SKU规格快照' AFTER sku_id;

-- 既有组合自动选取每个商品第一个在售 SKU；无 SKU 的商品继续使用 dish 库存。
UPDATE setmeal_dish sd
JOIN (
    SELECT dish_id, MIN(id) AS sku_id
    FROM product_sku
    WHERE status = 1
    GROUP BY dish_id
) chosen ON chosen.dish_id = sd.dish_id
JOIN product_sku sku ON sku.id = chosen.sku_id
SET sd.sku_id = sku.id,
    sd.sku_snapshot = CONCAT(COALESCE(sku.spec_name, '规格'), '：', COALESCE(sku.spec_value, '默认'))
WHERE sd.sku_id IS NULL;
