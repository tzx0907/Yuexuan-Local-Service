-- Adds a durable idempotency key for order submission.
-- The current base schema may already contain these definitions, so keep this
-- historical migration repeatable for a fresh Docker schema and older schemas.
DROP PROCEDURE IF EXISTS add_order_submit_idempotency;
DELIMITER //
CREATE PROCEDURE add_order_submit_idempotency()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'submit_request_id'
    ) THEN
        ALTER TABLE orders ADD COLUMN submit_request_id VARCHAR(64) NULL COMMENT '客户端下单幂等请求号';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'uk_orders_user_submit_request'
    ) THEN
        ALTER TABLE orders ADD UNIQUE KEY uk_orders_user_submit_request (user_id, submit_request_id);
    END IF;
END //
DELIMITER ;
CALL add_order_submit_idempotency();
DROP PROCEDURE add_order_submit_idempotency;
