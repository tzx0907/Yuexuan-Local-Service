-- 单规格商品的唯一库存口径是 dish.stock。
-- V19 为展示组合规格而创建的 SKU 会造成“单买扣 dish、组合买扣 SKU”的双库存问题，现撤销它们。
UPDATE setmeal_dish sd
JOIN dish d ON d.id = sd.dish_id
SET sd.sku_id = NULL,
    sd.sku_snapshot = CASE d.name
        WHEN '多用途除菌湿巾' THEN '规格：80抽×3包'
        WHEN '浓缩洗衣凝珠' THEN '规格：52颗'
        WHEN '浴室清洁喷雾' THEN '规格：500ml'
        WHEN '宠物拾便袋' THEN '规格：8卷×15只'
    END
WHERE d.name IN ('多用途除菌湿巾', '浓缩洗衣凝珠', '浴室清洁喷雾', '宠物拾便袋');

-- 仅删除 V19 为上述四个单规格商品创建的、没有其他组合引用的 SKU。
DELETE ps
FROM product_sku ps
JOIN dish d ON d.id = ps.dish_id
LEFT JOIN setmeal_dish sd ON sd.sku_id = ps.id
WHERE sd.id IS NULL
  AND ((d.name = '多用途除菌湿巾' AND ps.spec_value = '80抽×3包')
    OR (d.name = '浓缩洗衣凝珠' AND ps.spec_value = '52颗')
    OR (d.name = '浴室清洁喷雾' AND ps.spec_value = '500ml')
    OR (d.name = '宠物拾便袋' AND ps.spec_value = '8卷×15只'));
