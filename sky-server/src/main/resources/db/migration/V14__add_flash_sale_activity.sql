-- 限时购的“活动库存”是促销配额，SKU.stock 仍是真实商品库存；下单时两者都必须条件扣减。
USE `yuexuan_local_service`;

CREATE TABLE IF NOT EXISTS flash_sale_activity (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '活动 id',
    sku_id BIGINT NOT NULL COMMENT '参与活动的 SKU',
    sale_price DECIMAL(10,2) NOT NULL COMMENT '限时购成交单价',
    activity_stock INT NOT NULL COMMENT '活动总配额',
    sold_stock INT NOT NULL DEFAULT 0 COMMENT '已售活动配额',
    per_user_limit INT NOT NULL DEFAULT 1 COMMENT '每个用户限购数量',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_flash_sale_active (status, start_time, end_time),
    KEY idx_flash_sale_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='限时购活动';
