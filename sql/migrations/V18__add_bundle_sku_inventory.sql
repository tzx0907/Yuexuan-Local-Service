-- 组合商品固定到具体 SKU，确保扣减、回补和订单展示都对应同一规格。
-- 当前基础 Schema 已包含这两个字段，使用元数据检查兼容全新 Docker 库与旧库。
DROP PROCEDURE IF EXISTS add_bundle_sku_inventory_columns;
DELIMITER //
CREATE PROCEDURE add_bundle_sku_inventory_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'setmeal_dish' AND column_name = 'sku_id'
    ) THEN
        ALTER TABLE setmeal_dish ADD COLUMN sku_id BIGINT NULL COMMENT '组合中指定的商品SKU' AFTER dish_id;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'setmeal_dish' AND column_name = 'sku_snapshot'
    ) THEN
        ALTER TABLE setmeal_dish ADD COLUMN sku_snapshot VARCHAR(128) NULL COMMENT '组合SKU规格快照' AFTER sku_id;
    END IF;
END //
DELIMITER ;
CALL add_bundle_sku_inventory_columns();
DROP PROCEDURE add_bundle_sku_inventory_columns;

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
