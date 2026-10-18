-- 悦选下单压测的一致性校验（只读）。
-- 在 Docker MySQL 的 yuexuan_local_service 库中执行；不操作本机旧 sky_take_out 库。
-- 运行 k6 前记录 SKU 库存，将本次压测的起止时刻、SKU 与预期成功数填入下方变量。

USE `yuexuan_local_service`;

SET @run_id = 'stock100-rerun-20261017183019';
SET @started_at = '2026-10-17 18:32:30';
SET @ended_at = '2026-10-17 18:36:30';
SET @sku_id = 37;
SET @stock_before = 100;
SET @expected_successes = 100;

-- 1) 当前库存不能为负；压测 SKU 的扣减量应等于本次成功下单数。
SELECT
    p.id AS sku_id,
    p.stock AS stock_after,
    @stock_before AS stock_before,
    @stock_before - p.stock AS actual_stock_decrement,
    @expected_successes AS expected_successes,
    CASE
        WHEN p.stock < 0 THEN 'FAIL: 库存为负'
        WHEN @stock_before - p.stock <> @expected_successes THEN 'CHECK: 扣减量与成功订单数不一致'
        ELSE 'PASS'
    END AS result
FROM product_sku p
WHERE p.id = @sku_id;

-- 2) 本次运行创建的订单数、已支付订单数，以及订单明细数量。
-- remark 是服务端保存的 k6:<RUN_ID>，避免以不稳定的订单号或用户 ID 识别压测数据。
SELECT
    COUNT(*) AS created_orders,
    SUM(o.pay_status = 1) AS paid_orders,
    SUM(o.status = 6) AS cancelled_orders,
    SUM(o.status = 1) AS pending_payment_orders,
    CASE WHEN COUNT(*) <= @expected_successes THEN 'PASS' ELSE 'FAIL: 成功订单超过预期库存' END AS oversell_check
FROM orders o
WHERE o.remark = CONCAT('k6:', @run_id)
  AND o.order_time >= @started_at
  AND o.order_time < @ended_at;

SELECT
    od.sku_id,
    COUNT(*) AS order_detail_rows,
    SUM(od.number) AS submitted_quantity
FROM order_detail od
JOIN orders o ON o.id = od.order_id
WHERE o.remark = CONCAT('k6:', @run_id)
  AND o.order_time >= @started_at
  AND o.order_time < @ended_at
GROUP BY od.sku_id;

-- 3) 订单表的最终幂等兜底：(user_id, submit_request_id) 不应存在重复。
SELECT user_id, submit_request_id, COUNT(*) AS duplicate_count
FROM orders
WHERE remark = CONCAT('k6:', @run_id)
  AND order_time >= @started_at
  AND order_time < @ended_at
GROUP BY user_id, submit_request_id
HAVING COUNT(*) > 1;
-- 预期：0 行。

-- 4) 下单成功后，每笔订单会在同一事务写入一条 ORDER_CLOSE Outbox 事件。
-- payload 由应用 ObjectMapper 生成 JSON，按 orderId 精确关联，不使用模糊 LIKE。
SELECT
    o.id AS order_id,
    COUNT(e.id) AS order_close_events,
    GROUP_CONCAT(e.status ORDER BY e.id) AS event_statuses
FROM orders o
LEFT JOIN outbox_event e
  ON e.event_type = 'ORDER_CLOSE'
 AND JSON_UNQUOTE(JSON_EXTRACT(e.payload, '$.orderId')) = CAST(o.id AS CHAR)
WHERE o.remark = CONCAT('k6:', @run_id)
  AND o.order_time >= @started_at
  AND o.order_time < @ended_at
GROUP BY o.id
HAVING COUNT(e.id) <> 1;
-- 预期：0 行。若有行，说明订单与 Outbox 本地事务闭环需要排查。

SELECT status, COUNT(*) AS event_count, MAX(retry_count) AS max_retry_count
FROM outbox_event
WHERE event_type = 'ORDER_CLOSE'
  AND created_time >= @started_at
  AND created_time < @ended_at
GROUP BY status;

-- 5) processed_message 的唯一键是 (event_id, consumer_name)，重复消费不应留下重复副作用记录。
SELECT event_id, consumer_name, COUNT(*) AS duplicate_count
FROM processed_message
GROUP BY event_id, consumer_name
HAVING COUNT(*) > 1;
-- 预期：0 行。ORDER_CLOSE 是 15 分钟延迟消息；未到期前没有消费记录是正常的。

-- 6) 全库底线检查：任何 SKU 均不能为负库存。
SELECT id AS sku_id, dish_id, stock
FROM product_sku
WHERE stock < 0;
-- 预期：0 行。
