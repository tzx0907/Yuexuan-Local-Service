-- The original V4 migration was not applied to some existing local databases.
-- Do not use ADD COLUMN IF NOT EXISTS: the Docker MySQL image used for local
-- validation does not support that syntax. Use metadata checks instead.
DROP PROCEDURE IF EXISTS ensure_order_detail_sku_columns;
DELIMITER //
CREATE PROCEDURE ensure_order_detail_sku_columns()
BEGIN
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
CALL ensure_order_detail_sku_columns();
DROP PROCEDURE ensure_order_detail_sku_columns;
