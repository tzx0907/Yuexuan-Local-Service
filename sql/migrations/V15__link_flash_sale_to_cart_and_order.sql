USE `Yuexuan-Local-Service`;

-- 当前本地 MySQL 不支持 ADD COLUMN IF NOT EXISTS，故按元数据决定是否执行。
SET @has_cart_column := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'shopping_cart' AND column_name = 'flash_sale_activity_id');
SET @cart_column_sql := IF(@has_cart_column = 0,
    'ALTER TABLE shopping_cart ADD COLUMN flash_sale_activity_id BIGINT NULL COMMENT ''限时购活动 id'' AFTER sku_id',
    'SELECT 1');
PREPARE cart_column_stmt FROM @cart_column_sql;
EXECUTE cart_column_stmt;
DEALLOCATE PREPARE cart_column_stmt;

SET @has_detail_column := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'order_detail' AND column_name = 'flash_sale_activity_id');
SET @detail_column_sql := IF(@has_detail_column = 0,
    'ALTER TABLE order_detail ADD COLUMN flash_sale_activity_id BIGINT NULL COMMENT ''限时购活动 id'' AFTER sku_id',
    'SELECT 1');
PREPARE detail_column_stmt FROM @detail_column_sql;
EXECUTE detail_column_stmt;
DEALLOCATE PREPARE detail_column_stmt;

SET @has_cart_index := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'shopping_cart' AND index_name = 'idx_cart_flash_sale_activity');
SET @cart_index_sql := IF(@has_cart_index = 0,
    'CREATE INDEX idx_cart_flash_sale_activity ON shopping_cart (flash_sale_activity_id)', 'SELECT 1');
PREPARE cart_index_stmt FROM @cart_index_sql;
EXECUTE cart_index_stmt;
DEALLOCATE PREPARE cart_index_stmt;

SET @has_detail_index := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'order_detail' AND index_name = 'idx_order_detail_flash_sale_activity');
SET @detail_index_sql := IF(@has_detail_index = 0,
    'CREATE INDEX idx_order_detail_flash_sale_activity ON order_detail (flash_sale_activity_id)', 'SELECT 1');
PREPARE detail_index_stmt FROM @detail_index_sql;
EXECUTE detail_index_stmt;
DEALLOCATE PREPARE detail_index_stmt;
