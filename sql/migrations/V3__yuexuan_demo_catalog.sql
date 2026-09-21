-- 悦选本地到家服务平台：将课程餐饮演示数据替换为社区商品与到家服务演示数据。
-- 仅更新既有演示记录，不删除订单、地址、用户或购物车数据。
USE `Yuexuan-Local-Service`;

UPDATE category SET name = '日用百货' WHERE id = 11;
UPDATE category SET name = '新鲜果蔬' WHERE id = 12;
UPDATE category SET name = '乳品烘焙' WHERE id = 16;
UPDATE category SET name = '家庭清洁' WHERE id = 17;
UPDATE category SET name = '母婴用品' WHERE id = 18;
UPDATE category SET name = '宠物用品' WHERE id = 19;
UPDATE category SET name = '鲜花绿植' WHERE id = 20;
UPDATE category SET name = '健康护理' WHERE id = 21;
UPDATE category SET name = '上门服务' WHERE id = 26;
UPDATE category SET name = '社区团购组合' WHERE id = 13;
UPDATE category SET name = '到家服务组合' WHERE id = 15;

UPDATE dish SET name = '原生木浆抽纸', description = '家庭日用，柔韧亲肤', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 46;
UPDATE dish SET name = '加厚家用垃圾袋', description = '韧性加厚，日常收纳', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 47;
UPDATE dish SET name = '天然矿泉水', description = '整箱配送，随时补货', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 48;
UPDATE dish SET name = '免洗洗手液', description = '便携清洁，温和不伤手', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 49;
UPDATE dish SET name = '清新洗洁精', description = '去油去味，厨房常备', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 50;
UPDATE dish SET name = '精品阳光玫瑰', description = '当日精选，新鲜配送', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 65;
UPDATE dish SET name = '有机蔬菜组合', description = '3 种时令蔬菜组合', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 66;
UPDATE dish SET name = '鲜牛奶家庭装', description = '冷链配送，新鲜到家', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 67;
UPDATE dish SET name = '浓缩洗衣凝珠', description = '深层洁净，留香持久', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 62;
UPDATE dish SET name = '多用途除菌湿巾', description = '居家清洁，一擦即净', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 63;
UPDATE dish SET name = '浴室清洁喷雾', description = '快速除垢，清新无异味', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 64;
UPDATE dish SET name = '猫咪主食罐头', description = '营养配方，宠物喜爱', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 58;
UPDATE dish SET name = '天然豆腐猫砂', description = '低尘易结团，除味清新', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 59;
UPDATE dish SET name = '宠物拾便袋', description = '外出遛宠，随手清洁', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 60;
UPDATE dish SET name = '上门家电清洗', description = '专业技师，预约上门', image = 'http://localhost:8080/assets/yuexuan-product.svg' WHERE id = 72;
