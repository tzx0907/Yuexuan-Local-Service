-- 普通商品库存。历史商品默认库存为 0，避免升级后出现意外超卖。
-- 当前基础 Schema 已包含该列；保留条件判断以兼容旧库和全新 Docker 初始化库。
DROP PROCEDURE IF EXISTS add_product_stock;
DELIMITER //
CREATE PROCEDURE add_product_stock()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'dish' AND column_name = 'stock'
    ) THEN
        ALTER TABLE dish ADD COLUMN stock INT NOT NULL DEFAULT 0 COMMENT '可售库存' AFTER status;
    END IF;
END //
DELIMITER ;
CALL add_product_stock();
DROP PROCEDURE add_product_stock;
