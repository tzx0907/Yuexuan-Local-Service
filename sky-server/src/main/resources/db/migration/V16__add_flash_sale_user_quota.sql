USE `yuexuan_local_service`;

CREATE TABLE IF NOT EXISTS flash_sale_user_quota (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL COMMENT '限时购活动 id',
    user_id BIGINT NOT NULL COMMENT '用户 id',
    reserved_quantity INT NOT NULL DEFAULT 0 COMMENT '已支付或待支付占用数量',
    limit_quantity INT NOT NULL COMMENT '该用户首次参与时活动限购数快照',
    update_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flash_sale_activity_user (activity_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='限时购用户限购占用';
