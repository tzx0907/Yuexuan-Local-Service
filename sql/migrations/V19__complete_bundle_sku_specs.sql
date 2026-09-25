-- 为此前没有 SKU 的组合组成商品补齐规格展示数据。
-- 注意：这些商品当前采用商品级库存；实际 SKU 创建与扣减策略由后续迁移统一调整。
INSERT INTO product_sku (dish_id, spec_name, spec_value, price, stock, status, create_time, update_time)
SELECT d.id, '规格', '80抽×3包', d.price, d.stock, 1, NOW(), NOW()
FROM dish d WHERE d.name = '多用途除菌湿巾'
  AND NOT EXISTS (SELECT 1 FROM product_sku ps WHERE ps.dish_id = d.id);
INSERT INTO product_sku (dish_id, spec_name, spec_value, price, stock, status, create_time, update_time)
SELECT d.id, '规格', '52颗', d.price, d.stock, 1, NOW(), NOW()
FROM dish d WHERE d.name = '浓缩洗衣凝珠'
  AND NOT EXISTS (SELECT 1 FROM product_sku ps WHERE ps.dish_id = d.id);
INSERT INTO product_sku (dish_id, spec_name, spec_value, price, stock, status, create_time, update_time)
SELECT d.id, '规格', '500ml', d.price, d.stock, 1, NOW(), NOW()
FROM dish d WHERE d.name = '浴室清洁喷雾'
  AND NOT EXISTS (SELECT 1 FROM product_sku ps WHERE ps.dish_id = d.id);
INSERT INTO product_sku (dish_id, spec_name, spec_value, price, stock, status, create_time, update_time)
SELECT d.id, '规格', '8卷×15只', d.price, d.stock, 1, NOW(), NOW()
FROM dish d WHERE d.name = '宠物拾便袋'
  AND NOT EXISTS (SELECT 1 FROM product_sku ps WHERE ps.dish_id = d.id);

UPDATE setmeal_dish sd
JOIN product_sku ps ON ps.dish_id = sd.dish_id AND ps.status = 1
SET sd.sku_id = ps.id,
    sd.sku_snapshot = CONCAT(ps.spec_name, '：', ps.spec_value)
WHERE sd.sku_id IS NULL
  AND sd.dish_id IN (SELECT id FROM dish WHERE name IN ('多用途除菌湿巾', '浓缩洗衣凝珠', '浴室清洁喷雾', '宠物拾便袋'));
