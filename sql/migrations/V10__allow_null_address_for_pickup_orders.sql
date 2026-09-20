-- 到店自提订单没有收货地址，address_book_id 必须允许为 NULL。
-- 本迁移只变更列约束，不修改任何已有订单记录。
ALTER TABLE orders
    MODIFY COLUMN address_book_id BIGINT NULL COMMENT '地址id；到店自提无需收货地址';
