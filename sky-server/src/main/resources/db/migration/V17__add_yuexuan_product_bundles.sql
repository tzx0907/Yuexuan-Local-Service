-- 悦选组合商品演示数据。
-- 旧 setmeal / setmeal_dish 物理表继续承载组合商品，避免破坏既有购物车、订单与管理端接口。
-- 本脚本可重复执行：只维护本脚本定义的“精选组合”及其三组组合明细。
USE `yuexuan_local_service`;

START TRANSACTION;

-- type=2 是组合商品分类；用户端分类接口会据此调用 /user/setmeal/list。
INSERT INTO category (type, name, sort, status, create_time, update_time, create_user, update_user)
VALUES (2, '精选组合', 9, 1, NOW(), NOW(), 1, 1)
ON DUPLICATE KEY UPDATE
    type = VALUES(type), sort = VALUES(sort), status = VALUES(status), update_time = NOW(), update_user = 1;

SET @bundle_category_id = (SELECT id FROM category WHERE name = '精选组合' LIMIT 1);
SET @bundle_image = 'http://localhost:8080/assets/yuexuan-product.svg';

-- 组合价按组成商品当前公开售价合计后打 8 折，确保首次导入时比单买便宜 20%；
-- 组合本身使用独立售价，购物车和订单保存组合价快照。
INSERT INTO setmeal (category_id, name, price, status, description, image, create_time, update_time, create_user, update_user)
SELECT @bundle_category_id, '居家补货组合', ROUND(SUM(price) * 0.80, 2), 1,
       '抽纸、垃圾袋、除菌湿巾，一次补齐日常所需，组合购买立省 20%', @bundle_image, NOW(), NOW(), 1, 1
FROM dish WHERE name IN ('原生木浆抽纸', '加厚家用垃圾袋', '多用途除菌湿巾')
UNION ALL
SELECT @bundle_category_id, '家庭清洁组合', ROUND(SUM(price) * 0.80, 2), 1,
       '洗衣凝珠、除菌湿巾、浴室清洁喷雾，居家焕新更省心，组合购买立省 20%', @bundle_image, NOW(), NOW(), 1, 1
FROM dish WHERE name IN ('浓缩洗衣凝珠', '多用途除菌湿巾', '浴室清洁喷雾')
UNION ALL
SELECT @bundle_category_id, '宠物日常组合', ROUND(SUM(price) * 0.80, 2), 1,
       '主食罐头、豆腐猫砂、拾便袋，毛孩子日常常备，组合购买立省 20%', @bundle_image, NOW(), NOW(), 1, 1
FROM dish WHERE name IN ('猫咪主食罐头', '天然豆腐猫砂', '宠物拾便袋')
ON DUPLICATE KEY UPDATE
    category_id = VALUES(category_id), price = VALUES(price), status = VALUES(status),
    description = VALUES(description), image = VALUES(image), update_time = NOW(), update_user = 1;

-- 用当前商品的公开售价写入组成明细；订单提交时组合商品仍按组合价结算。
DELETE sd
FROM setmeal_dish sd
INNER JOIN setmeal s ON s.id = sd.setmeal_id
WHERE s.name IN ('居家补货组合', '家庭清洁组合', '宠物日常组合');

INSERT INTO setmeal_dish (setmeal_id, dish_id, name, price, copies)
SELECT s.id, d.id, d.name, d.price, 1
FROM setmeal s
INNER JOIN dish d ON d.name IN ('原生木浆抽纸', '加厚家用垃圾袋', '多用途除菌湿巾')
WHERE s.name = '居家补货组合'
UNION ALL
SELECT s.id, d.id, d.name, d.price, 1
FROM setmeal s
INNER JOIN dish d ON d.name IN ('浓缩洗衣凝珠', '多用途除菌湿巾', '浴室清洁喷雾')
WHERE s.name = '家庭清洁组合'
UNION ALL
SELECT s.id, d.id, d.name, d.price, 1
FROM setmeal s
INNER JOIN dish d ON d.name IN ('猫咪主食罐头', '天然豆腐猫砂', '宠物拾便袋')
WHERE s.name = '宠物日常组合';

COMMIT;
