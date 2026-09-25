-- 用户已确认不保留历史交易记录。本迁移清空旧交易与旧服务组合，
-- 不删除 user / address_book，以便继续进行小程序验收。
USE `yuexuan_local_service`;

START TRANSACTION;
DELETE FROM order_detail;
DELETE FROM orders;
DELETE FROM shopping_cart;
DELETE FROM setmeal_dish;
DELETE FROM setmeal;
COMMIT;
