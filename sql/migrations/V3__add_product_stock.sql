-- 普通商品库存。历史商品默认库存为 0，避免升级后出现意外超卖。
ALTER TABLE dish
    ADD COLUMN stock INT NOT NULL DEFAULT 0 COMMENT '可售库存' AFTER status;
