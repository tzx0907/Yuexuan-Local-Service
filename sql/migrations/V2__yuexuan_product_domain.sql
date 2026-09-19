-- 悦选本地到家服务平台：商品/SKU 兼容迁移
--
-- 适用范围：已使用旧版 sky_take_out 初始化脚本创建的数据库。
-- 原表 dish、setmeal、shopping_cart 不删除、不改名，避免影响既有订单、Mapper 和历史数据。
-- 对外产品语义统一为“商品 / 服务组合”；物理表的旧名称仅作为兼容实现细节。
--
-- 执行方式：在目标 MySQL 数据库中执行一次本文件。
-- 兼容 MySQL 5.7+ / 8.0+，可重复执行。

USE sky_take_out;

-- 早期课程库没有 SKU 表，但当前购物车与下单服务已经依赖该模型。
CREATE TABLE IF NOT EXISTS product_sku (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    dish_id BIGINT NOT NULL COMMENT '商品 ID（兼容历史 dish 表）',
    spec_name VARCHAR(64) NOT NULL COMMENT '规格名称',
    spec_value VARCHAR(128) NOT NULL COMMENT '规格值',
    price DECIMAL(10, 2) NOT NULL COMMENT 'SKU 售价',
    stock INT NOT NULL DEFAULT 0 COMMENT '可售库存',
    status INT NOT NULL DEFAULT 1 COMMENT '0 下架 1 上架',
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku_spec (dish_id, spec_name, spec_value),
    KEY idx_product_sku_dish_id (dish_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='悦选商品 SKU';

-- 当前后端 ShoppingCart 已使用 sku_id；旧初始化库缺少该列时会造成 SKU 购物车写入失败。
-- 较早的 MySQL 版本不支持 ADD ... IF NOT EXISTS，因此用 information_schema 保证可重复执行。
DROP PROCEDURE IF EXISTS upgrade_yuexuan_product_domain;
DELIMITER //
CREATE PROCEDURE upgrade_yuexuan_product_domain()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'shopping_cart' AND column_name = 'sku_id'
    ) THEN
        ALTER TABLE shopping_cart ADD COLUMN sku_id BIGINT NULL COMMENT '商品 SKU id' AFTER dish_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'shopping_cart' AND index_name = 'idx_cart_user_product_sku'
    ) THEN
        ALTER TABLE shopping_cart ADD INDEX idx_cart_user_product_sku (user_id, dish_id, sku_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'dish' AND index_name = 'idx_product_category_status'
    ) THEN
        ALTER TABLE dish ADD INDEX idx_product_category_status (category_id, status);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'product_sku' AND index_name = 'idx_product_sku_status'
    ) THEN
        ALTER TABLE product_sku ADD INDEX idx_product_sku_status (dish_id, status);
    END IF;
END //
DELIMITER ;
CALL upgrade_yuexuan_product_domain();
DROP PROCEDURE upgrade_yuexuan_product_domain;

-- 仅提供阅读语义的兼容视图；不改变现有后端 Mapper 对 dish/setmeal 表的依赖。
CREATE OR REPLACE VIEW yuexuan_product AS
SELECT id,
       name,
       category_id,
       price,
       image,
       description,
       status,
       stock,
       create_time,
       update_time
FROM dish;

CREATE OR REPLACE VIEW yuexuan_service_package AS
SELECT id,
       category_id,
       name,
       price,
       status,
       description,
       image,
       create_time,
       update_time
FROM setmeal;

-- 数据模型约定：
-- dish               -> 悦选商品（兼容历史表名）
-- setmeal            -> 悦选服务组合（兼容历史表名）
-- product_sku        -> 商品可售规格
-- shopping_cart.sku_id -> 用户选择的具体规格
