-- 兼容旧库和已包含 SKU 基础表的全新 Docker 初始化库。
CREATE TABLE IF NOT EXISTS product_sku (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    dish_id BIGINT NOT NULL COMMENT '商品 ID',
    spec_name VARCHAR(64) NOT NULL COMMENT '规格名称',
    spec_value VARCHAR(128) NOT NULL COMMENT '规格值',
    price DECIMAL(10,2) NOT NULL COMMENT 'SKU 售价',
    stock INT NOT NULL DEFAULT 0 COMMENT '可售库存',
    status INT NOT NULL DEFAULT 1 COMMENT '0 下架 1 上架',
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku_spec (dish_id, spec_name, spec_value),
    KEY idx_product_sku_dish_id (dish_id)
) COMMENT='商品 SKU';

DROP PROCEDURE IF EXISTS add_product_sku_columns;
DELIMITER //
CREATE PROCEDURE add_product_sku_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'shopping_cart' AND column_name = 'sku_id'
    ) THEN
        ALTER TABLE shopping_cart ADD COLUMN sku_id BIGINT NULL COMMENT '商品 SKU id' AFTER dish_id;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'order_detail' AND column_name = 'sku_id'
    ) THEN
        ALTER TABLE order_detail ADD COLUMN sku_id BIGINT NULL COMMENT '商品 SKU id' AFTER dish_id;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'order_detail' AND column_name = 'sku_snapshot'
    ) THEN
        ALTER TABLE order_detail ADD COLUMN sku_snapshot VARCHAR(255) NULL COMMENT '下单时 SKU 规格快照' AFTER sku_id;
    END IF;
END //
DELIMITER ;
CALL add_product_sku_columns();
DROP PROCEDURE add_product_sku_columns;
