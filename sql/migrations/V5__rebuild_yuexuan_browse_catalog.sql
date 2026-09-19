-- 悦选用户端浏览目录重建。
-- 安全策略：保留 dish ID、订单、订单明细和购物车；不删除历史交易数据。
-- dish_flavor 仅是旧模板的可选项元数据，先备份后替换为商品规格。
USE sky_take_out;

CREATE TABLE IF NOT EXISTS backup_20260918_category AS SELECT * FROM category;
CREATE TABLE IF NOT EXISTS backup_20260918_dish AS SELECT * FROM dish;
CREATE TABLE IF NOT EXISTS backup_20260918_dish_flavor AS SELECT * FROM dish_flavor;
CREATE TABLE IF NOT EXISTS backup_20260918_product_sku AS SELECT * FROM product_sku;

START TRANSACTION;

-- 用户端仅保留八个悦选分类，排序与首页一致。
-- category.name 有唯一索引，先写入临时名称避免“宠物用品”等名称互换时冲突。
UPDATE category SET name=CONCAT('__yx_reset_', id) WHERE id IN (11,12,16,17,18,19,20,21,26,13,15);
UPDATE category SET name='日用百货', type=1, sort=1, status=1 WHERE id=11;
UPDATE category SET name='新鲜果蔬', type=1, sort=2, status=1 WHERE id=12;
UPDATE category SET name='乳品烘焙', type=1, sort=3, status=1 WHERE id=16;
UPDATE category SET name='家庭清洁', type=1, sort=4, status=1 WHERE id=17;
UPDATE category SET name='宠物用品', type=1, sort=5, status=1 WHERE id=18;
UPDATE category SET name='鲜花绿植', type=1, sort=6, status=1 WHERE id=19;
UPDATE category SET name='健康护理', type=1, sort=7, status=1 WHERE id=20;
UPDATE category SET name='上门服务', type=1, sort=8, status=1 WHERE id=26;
UPDATE category SET status=0 WHERE id IN (13,15,21);

-- 旧 dish 表保留为兼容物理表，但全部在售记录均改为悦选商品/服务。
-- 同理，商品名有唯一索引，先使用临时名称以允许跨分类的重命名。
UPDATE dish SET name=CONCAT('__yx_reset_', id) WHERE id IN (46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,72);
UPDATE dish SET
 category_id=CASE id
   WHEN 46 THEN 11 WHEN 47 THEN 11 WHEN 48 THEN 11
   WHEN 49 THEN 12 WHEN 50 THEN 12 WHEN 51 THEN 12 WHEN 52 THEN 12 WHEN 53 THEN 12
   WHEN 65 THEN 16 WHEN 66 THEN 16 WHEN 67 THEN 16
   WHEN 62 THEN 17 WHEN 63 THEN 17 WHEN 64 THEN 17
   WHEN 58 THEN 18 WHEN 59 THEN 18 WHEN 60 THEN 18 WHEN 61 THEN 18
   WHEN 54 THEN 19 WHEN 55 THEN 19 WHEN 56 THEN 19 WHEN 57 THEN 19
   WHEN 68 THEN 20 WHEN 69 THEN 20 WHEN 72 THEN 26 END,
 name=CASE id
   WHEN 46 THEN '原生木浆抽纸' WHEN 47 THEN '加厚家用垃圾袋' WHEN 48 THEN '天然矿泉水'
   WHEN 49 THEN '红富士苹果' WHEN 50 THEN '精品香蕉' WHEN 51 THEN '山东蜜薯' WHEN 52 THEN '有机西兰花' WHEN 53 THEN '鲜切玉米'
   WHEN 65 THEN '鲜牛奶家庭装' WHEN 66 THEN '全麦吐司' WHEN 67 THEN '黄油可颂'
   WHEN 62 THEN '浓缩洗衣凝珠' WHEN 63 THEN '多用途除菌湿巾' WHEN 64 THEN '浴室清洁喷雾'
   WHEN 58 THEN '猫咪主食罐头' WHEN 59 THEN '天然豆腐猫砂' WHEN 60 THEN '宠物拾便袋' WHEN 61 THEN '宠物洁齿零食'
   WHEN 54 THEN '向日葵花束' WHEN 55 THEN '白玫瑰花束' WHEN 56 THEN '绿萝盆栽' WHEN 57 THEN '多肉组合盆栽'
   WHEN 68 THEN '医用护理口罩' WHEN 69 THEN '碘伏消毒棉签' WHEN 72 THEN '上门家电清洗' END,
 description=CASE id
   WHEN 46 THEN '家庭日用，柔韧亲肤' WHEN 47 THEN '韧性加厚，日常收纳' WHEN 48 THEN '整箱配送，随时补货'
   WHEN 49 THEN '脆甜多汁，新鲜到家' WHEN 50 THEN '自然成熟，香甜软糯' WHEN 51 THEN '软糯香甜，当日精选' WHEN 52 THEN '翠绿新鲜，营养丰富' WHEN 53 THEN '香甜软糯，早餐优选'
   WHEN 65 THEN '冷链配送，新鲜到家' WHEN 66 THEN '麦香浓郁，早餐常备' WHEN 67 THEN '黄油香气，现烤风味'
   WHEN 62 THEN '深层洁净，留香持久' WHEN 63 THEN '居家清洁，一擦即净' WHEN 64 THEN '快速除垢，清新无异味'
   WHEN 58 THEN '营养配方，宠物喜爱' WHEN 59 THEN '低尘易结团，除味清新' WHEN 60 THEN '外出遛宠，随手清洁' WHEN 61 THEN '帮助清洁牙齿，营养美味'
   WHEN 54 THEN '明亮温暖，节日送礼' WHEN 55 THEN '优雅浪漫，精致包装' WHEN 56 THEN '净化空气，好养耐活' WHEN 57 THEN '小巧可爱，桌面点缀'
   WHEN 68 THEN '独立包装，日常防护' WHEN 69 THEN '温和消毒，家庭常备' WHEN 72 THEN '专业技师，预约上门' END,
 image='http://localhost:8080/assets/yuexuan-product.svg', status=1, stock=100, update_time=NOW()
WHERE id IN (46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,72);

-- 与交易快照无关联的旧餐饮口味元数据可安全重建为商品规格。
DELETE FROM dish_flavor;
DELETE FROM product_sku;
INSERT INTO dish_flavor (dish_id,name,value) VALUES
 (46,'规格','["10包","20包"]'), (47,'规格','["50只","100只"]'),
 (48,'规格','["24瓶","48瓶"]'), (49,'规格','["1kg","2kg"]'),
 (58,'规格','["85g×6","85g×12"]'), (59,'规格','["2.5kg","6L"]'),
 (65,'规格','["250ml×12","250ml×24"]'), (72,'服务时长','["基础清洗","深度清洗"]');
INSERT INTO product_sku (dish_id,spec_name,spec_value,price,stock,status,create_time,update_time) VALUES
 (46,'规格','10包',6.00,100,1,NOW(),NOW()),(46,'规格','20包',10.90,100,1,NOW(),NOW()),
 (47,'规格','50只',4.00,100,1,NOW(),NOW()),(47,'规格','100只',7.50,100,1,NOW(),NOW()),
 (48,'规格','24瓶',24.00,100,1,NOW(),NOW()),(48,'规格','48瓶',45.00,100,1,NOW(),NOW()),
 (49,'规格','1kg',12.90,100,1,NOW(),NOW()),(49,'规格','2kg',23.90,100,1,NOW(),NOW()),
 (58,'规格','85g×6',39.90,100,1,NOW(),NOW()),(58,'规格','85g×12',75.90,100,1,NOW(),NOW()),
 (59,'规格','2.5kg',29.90,100,1,NOW(),NOW()),(59,'规格','6L',59.90,100,1,NOW(),NOW()),
 (65,'规格','250ml×12',39.90,100,1,NOW(),NOW()),(65,'规格','250ml×24',75.90,100,1,NOW(),NOW()),
 (72,'服务时长','基础清洗',188.00,100,1,NOW(),NOW()),(72,'服务时长','深度清洗',288.00,100,1,NOW(),NOW());
COMMIT;
