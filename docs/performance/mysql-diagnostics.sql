-- 下单压测中的 MySQL 诊断快照（只读）。
-- 请在 k6 正在运行、且出现延迟时执行，而不是压测结束后再执行。
-- 目标数据库：Docker 演示库 yuexuan_local_service；绝不操作旧 sky_take_out。

USE `yuexuan_local_service`;

-- 1) 当前连接数与并发上限；用于判断是否存在连接池或数据库连接排队。
-- 不依赖 performance_schema，适用于 Docker MySQL 的基础诊断。
SHOW GLOBAL STATUS LIKE 'Threads_connected';
SHOW GLOBAL STATUS LIKE 'Threads_running';
SHOW GLOBAL STATUS LIKE 'Max_used_connections';
SHOW VARIABLES LIKE 'max_connections';

-- 2) 当前正在执行、等待或阻塞的数据库线程。压测结束后通常没有结果是正常的。
SHOW FULL PROCESSLIST;

-- 3) InnoDB 运行中事务。压测结束后为空或只有当前诊断连接是正常的。
-- 同一 SKU 的条件扣库存会产生短暂串行等待；运行中可观察事务开始时间和当前 SQL。
SELECT trx_id, trx_state, trx_started, trx_wait_started,
       trx_tables_locked, trx_rows_locked, trx_rows_modified, trx_query
FROM information_schema.INNODB_TRX
ORDER BY trx_started;

-- 4) InnoDB 最近状态，其中的 TRANSACTIONS 段可显示锁等待、死锁和行锁信息。
SHOW ENGINE INNODB STATUS;

-- 5) 本轮 SKU 仍不能出现负库存（替换为实际 sku_id）。
SET @sku_id = 37;
SELECT id, dish_id, stock, status
FROM product_sku
WHERE id = @sku_id;
