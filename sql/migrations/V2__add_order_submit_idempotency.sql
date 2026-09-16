-- Adds a durable idempotency key for order submission.
-- MySQL unique indexes permit multiple NULL values, so historical orders remain valid.
ALTER TABLE orders
    ADD COLUMN submit_request_id VARCHAR(64) NULL COMMENT '客户端下单幂等请求号';

ALTER TABLE orders
    ADD UNIQUE KEY uk_orders_user_submit_request (user_id, submit_request_id);
