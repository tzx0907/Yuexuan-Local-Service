-- The original V4 migration was not applied to some existing local databases.
-- Keep this repair idempotent so a fresh or already-repaired development
-- database can both run it safely.
ALTER TABLE order_detail
    ADD COLUMN IF NOT EXISTS sku_id BIGINT NULL COMMENT '商品 SKU id' AFTER dish_id,
    ADD COLUMN IF NOT EXISTS sku_snapshot VARCHAR(255) NULL COMMENT '下单时 SKU 规格快照' AFTER sku_id;
