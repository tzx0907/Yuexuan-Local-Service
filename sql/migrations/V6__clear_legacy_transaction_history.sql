-- 用户已确认不保留历史交易记录。
-- V5 已创建 backup_20260918_* 快照；本迁移仅清空旧交易与旧服务组合，
-- 不删除 user / address_book，以便继续进行小程序验收。
USE sky_take_out;

CREATE TABLE IF NOT EXISTS backup_20260918_orders AS SELECT * FROM orders;
CREATE TABLE IF NOT EXISTS backup_20260918_order_detail AS SELECT * FROM order_detail;
CREATE TABLE IF NOT EXISTS backup_20260918_shopping_cart AS SELECT * FROM shopping_cart;
CREATE TABLE IF NOT EXISTS backup_20260918_setmeal AS SELECT * FROM setmeal;
CREATE TABLE IF NOT EXISTS backup_20260918_setmeal_dish AS SELECT * FROM setmeal_dish;

START TRANSACTION;
DELETE FROM order_detail;
DELETE FROM orders;
DELETE FROM shopping_cart;
DELETE FROM setmeal_dish;
DELETE FROM setmeal;
COMMIT;
