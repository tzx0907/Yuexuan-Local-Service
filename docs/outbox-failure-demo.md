# Outbox、RabbitMQ 重试与 DLQ 演示

本演示在本仓库的 Docker 演示环境中验证两类不同失败：

1. **生产端失败**：订单事务已经提交，但 RabbitMQ 暂时不可用；`outbox_event` 必须保留为 `RETRY`，恢复后最终变为 `SENT`。
2. **消费端失败**：RabbitMQ 已接收消息，但支付通知消费者无法完成业务；消息必须经历有限重试后进入 DLQ，恢复后可人工重放，并且不会产生重复业务副作用。

不要对本机旧 `sky_take_out` 数据库执行这些步骤；不要以删除订单、修改 `processed_message` 或手工改 `outbox_event.status` 的方式伪造恢复结果。

## 一、生产端确认失败后的 Outbox 重投

1. 启动 Docker 依赖和后端，在一个独立演示订单上完成下单或模拟支付。
2. 在创建新订单前，仅停止本项目 RabbitMQ 服务（不删除卷）：

   ```powershell
   docker compose stop rabbitmq
   ```

3. 创建订单。订单、库存和 `ORDER_CLOSE` Outbox 事件会在同一个 MySQL 事务内提交；投递器无法获得 RabbitMQ confirm，事件会从 `PENDING` 变为 `RETRY`，`retry_count` 增加，`last_error` 记录失败原因，`next_attempt_time` 按 2、4、8 … 秒退避。
4. 恢复 RabbitMQ：

   ```powershell
   docker compose start rabbitmq
   ```

5. 等待一个扫描周期（当前为 10 秒）加本次退避时间。预期该事件最终变为 `SENT`，且 `sent_time` 有值。它可能曾多次发布，因此消费者仍必须按 `eventId` 幂等。

## 二、故意触发消费者失败、重试与 DLQ

该故障注入只在 `dev` profile 且显式开启时生效，默认关闭。它在 `ORDER_PAID` 消费者 ACK 前抛出异常，不会改动订单、库存、Outbox 或幂等表。

1. 在**本机忽略**的 `sky-server/src/main/resources/application-dev.yml` 增加：

   ```yaml
   sky:
     messaging:
       fault-injection:
         order-paid-consumer-fail: true
   ```

2. 确认应用 profile 为 `dev`，重启后端，创建并模拟支付一笔新的演示订单。
3. 查看应用日志：同一条消息会在该次消费者投递中最多尝试 3 次，退避为 500ms、1s；第 3 次仍失败时消费者显式执行 `basicNack(deliveryTag, false, false)`，由队列 DLX 路由到 `yuexuan.order.paid.dlq`。
4. 在 RabbitMQ 管理台 `http://localhost:15672` 查看 `yuexuan.order.paid.dlq` 的 Ready 数量。此时订单已经支付、其 `ORDER_PAID` Outbox 事件仍为 `SENT`——因为生产端确认已经成功，失败发生在消费端。
5. 关闭上面的故障开关，并在本机 `application-dev.yml` 显式开启受控重放入口：

   ```yaml
   sky:
     messaging:
       replay:
         enabled: true
   ```

   重启后端后，从对应 `outbox_event` 读取 `event_id`，调用：

   ```powershell
   Invoke-RestMethod -Method Post `
     -Uri "http://localhost:8080/_dev/messaging/replay/order-paid/<eventId>"
   ```

   该入口仅在 `dev` profile 且显式开关开启时注册；它会按原始 `eventId` 从 Outbox payload 重发到正常 `order.paid` 路由，不修改 `outbox_event` 的 `SENT` 状态，也不会自动删除 DLQ 原消息。预期 WebSocket 通知成功，`processed_message` 新增一条 `(event_id, admin-order-notifier)`；再次重放同一 eventId 时消费者只 ACK，不再重复通知。确认恢复成功后，再在 RabbitMQ 管理台删除对应的 DLQ 原消息。
6. 演示结束后将 `fault-injection.order-paid-consumer-fail` 与 `replay.enabled` 保持为 `false` 或删除这两段本机配置，重启后端。它们不应出现在任何生产 profile 或提交文件中。

## 三、排查 SQL（只读）

```sql
USE `yuexuan_local_service`;

-- 待投递、退避重试、发送中和已发送的积压情况
SELECT status, COUNT(*) AS event_count, MAX(retry_count) AS max_retry_count,
       MIN(created_time) AS oldest_created_time
FROM outbox_event
GROUP BY status;

-- 需要优先排查的 Outbox：长时间未投递、频繁重试，或异常卡在 SENDING。
SELECT id, event_id, event_type, status, retry_count, next_attempt_time,
       processing_time, created_time, last_error
FROM outbox_event
WHERE (status IN ('PENDING', 'RETRY') AND created_time < NOW() - INTERVAL 5 MINUTE)
   OR (status = 'SENDING' AND processing_time < NOW() - INTERVAL 5 MINUTE)
   OR retry_count >= 5
ORDER BY created_time ASC;

-- 消费幂等表不应出现同一消费者对同一事件的重复副作用。
SELECT event_id, consumer_name, COUNT(*) AS duplicate_count
FROM processed_message
GROUP BY event_id, consumer_name
HAVING COUNT(*) > 1;
```

## 状态边界

| 观察到的状态 | 表示什么 | 下一步 |
| --- | --- | --- |
| `outbox_event = RETRY` | 生产端尚未获得确认，事件仍受 Outbox 重投保护 | 检查 RabbitMQ 连通性、`last_error`、下一次重试时间 |
| `outbox_event = SENT` | Broker 已 ACK 且已路由 | 继续检查消费队列 / DLQ；不代表消费者业务已经完成 |
| `yuexuan.order.paid.dlq` 有消息 | 消费者连续失败，消息没有被静默丢弃 | 修复原因后人工回放，检查幂等表和通知结果 |
| `processed_message` 有记录 | 对应消费者已成功完成其业务并 ACK | 重复消息应只 ACK，不重复产生副作用 |
