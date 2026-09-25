-- 悦选目录定价与规格统一：鲜花绿植保留单品；其他商品/服务均以 SKU 为唯一售价。
-- 有 SKU 的 dish.price 置空，dish.stock 仅为所有在售 SKU 库存之和的展示值。
USE `yuexuan_local_service`;

START TRANSACTION;

-- 补齐此前未规格化商品的两档可售 SKU；库存从原商品库存拆分，不创造第二份库存。
INSERT INTO product_sku (dish_id,spec_name,spec_value,price,stock,status,create_time,update_time)
SELECT d.id,'规格','500g',5.90,FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='精品香蕉'
UNION ALL SELECT d.id,'规格','1kg',10.90,d.stock-FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='精品香蕉'
UNION ALL SELECT d.id,'规格','1kg',8.90,FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='山东蜜薯'
UNION ALL SELECT d.id,'规格','2kg',16.90,d.stock-FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='山东蜜薯'
UNION ALL SELECT d.id,'规格','300g',6.90,FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='有机西兰花'
UNION ALL SELECT d.id,'规格','500g',10.90,d.stock-FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='有机西兰花'
UNION ALL SELECT d.id,'规格','2支',9.90,FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='鲜切玉米'
UNION ALL SELECT d.id,'规格','4支',18.90,d.stock-FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='鲜切玉米'
UNION ALL SELECT d.id,'规格','400g',12.90,FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='全麦吐司'
UNION ALL SELECT d.id,'规格','800g',23.90,d.stock-FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='全麦吐司'
UNION ALL SELECT d.id,'规格','3只',12.90,FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='黄油可颂'
UNION ALL SELECT d.id,'规格','6只',23.90,d.stock-FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='黄油可颂'
UNION ALL SELECT d.id,'规格','26颗',29.90,FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='浓缩洗衣凝珠'
UNION ALL SELECT d.id,'规格','52颗',52.90,d.stock-FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='浓缩洗衣凝珠'
UNION ALL SELECT d.id,'规格','40抽×3包',14.90,FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='多用途除菌湿巾'
UNION ALL SELECT d.id,'规格','80抽×3包',25.90,d.stock-FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='多用途除菌湿巾'
UNION ALL SELECT d.id,'规格','500ml',19.90,FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='浴室清洁喷雾'
UNION ALL SELECT d.id,'规格','1L',35.90,d.stock-FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='浴室清洁喷雾'
UNION ALL SELECT d.id,'规格','4卷×15只',6.90,FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='宠物拾便袋'
UNION ALL SELECT d.id,'规格','8卷×15只',12.90,d.stock-FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='宠物拾便袋'
UNION ALL SELECT d.id,'规格','80g',12.90,FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='宠物洁齿零食'
UNION ALL SELECT d.id,'规格','160g',23.90,d.stock-FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='宠物洁齿零食'
UNION ALL SELECT d.id,'规格','10只',8.90,FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='医用护理口罩'
UNION ALL SELECT d.id,'规格','30只',23.90,d.stock-FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='医用护理口罩'
UNION ALL SELECT d.id,'规格','100支',7.90,FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='碘伏消毒棉签'
UNION ALL SELECT d.id,'规格','200支',13.90,d.stock-FLOOR(d.stock / 2),1,NOW(),NOW() FROM dish d WHERE d.name='碘伏消毒棉签';

-- 调整原有 SKU 价格；拾便袋改为按卷数销售，取消不合理的 58 元单价。
UPDATE product_sku ps JOIN dish d ON d.id=ps.dish_id SET ps.price=12.90 WHERE d.name='宠物拾便袋' AND ps.spec_value='8卷×15只';

-- 浏览规格元数据与 SKU 保持一一对应，旧“口味”不再参与用户端选择。
DELETE df FROM dish_flavor df JOIN dish d ON d.id=df.dish_id JOIN category c ON c.id=d.category_id WHERE c.id<>19;
INSERT INTO dish_flavor (dish_id,name,value)
SELECT ps.dish_id, MAX(ps.spec_name), CONCAT('[', GROUP_CONCAT(CONCAT('"',ps.spec_value,'"') ORDER BY ps.id SEPARATOR ','), ']')
FROM product_sku ps JOIN dish d ON d.id=ps.dish_id JOIN category c ON c.id=d.category_id
WHERE c.id<>19 AND ps.status=1 GROUP BY ps.dish_id;

-- 组合必须引用明确 SKU，以同一份 SKU 库存扣减；同步规格快照和组成价。
UPDATE setmeal_dish sd JOIN dish d ON d.id=sd.dish_id JOIN product_sku ps ON ps.dish_id=d.id AND ps.status=1
SET sd.sku_id=ps.id, sd.sku_snapshot=CONCAT(ps.spec_name,'：',ps.spec_value), sd.price=ps.price
WHERE (d.name='多用途除菌湿巾' AND ps.spec_value='80抽×3包')
   OR (d.name='浓缩洗衣凝珠' AND ps.spec_value='52颗')
   OR (d.name='浴室清洁喷雾' AND ps.spec_value='500ml')
   OR (d.name='宠物拾便袋' AND ps.spec_value='8卷×15只');
UPDATE setmeal s SET s.price=(SELECT ROUND(SUM(sd.price*sd.copies)*0.80,2) FROM setmeal_dish sd WHERE sd.setmeal_id=s.id)
WHERE s.name IN ('居家补货组合','家庭清洁组合','宠物日常组合');

-- 旧购物车中没有 SKU 的非绿植商品统一绑定默认 SKU 并刷新单价，避免按已废弃 dish.price 结算。
UPDATE shopping_cart sc JOIN dish d ON d.id=sc.dish_id JOIN category c ON c.id=d.category_id
JOIN product_sku ps ON ps.id=(SELECT MIN(p2.id) FROM product_sku p2 WHERE p2.dish_id=d.id AND p2.status=1)
SET sc.sku_id=ps.id, sc.dish_flavor=CONCAT(ps.spec_name,':',ps.spec_value), sc.amount=ps.price
WHERE sc.dish_id IS NOT NULL AND sc.sku_id IS NULL AND c.id<>19;

-- SKU 商品没有商品级固定售价，避免前端误把一个规格价格展示为全商品价格；绿植保持单品价。
UPDATE dish d JOIN category c ON c.id=d.category_id SET d.price=NULL
WHERE c.id<>19 AND EXISTS (SELECT 1 FROM product_sku ps WHERE ps.dish_id=d.id AND ps.status=1);
UPDATE dish d SET d.stock=(SELECT COALESCE(SUM(ps.stock),0) FROM product_sku ps WHERE ps.dish_id=d.id AND ps.status=1)
WHERE EXISTS (SELECT 1 FROM product_sku ps WHERE ps.dish_id=d.id AND ps.status=1);

COMMIT;
