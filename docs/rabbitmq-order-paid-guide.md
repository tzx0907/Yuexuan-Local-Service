# RabbitMQ 订单支付事件：为什么、怎么做、如何验证

## 1. 先理解这次解决的业务问题

用户完成支付时有两类工作：

1. **核心且必须同步完成**：订单从“待支付”变成“待接单”。这一步仍在 MySQL 事务内使用条件更新完成，失败就向用户返回失败；
2. **非核心且可以异步完成**：通知运营端出现新订单。以后还可增加积分、优惠券、短信、经营埋点等。

如果在支付接口里直接依次调用所有非核心能力，一个短信接口超时或 WebSocket 临时故障就会拖慢支付，甚至让支付结果看起来失败。因此用 RabbitMQ 充当可靠的“待办队列”：支付接口只把后续工作交给消息队列，消费者按自己的速度处理。

```
支付成功
  ├── 同步：UPDATE orders ... WHERE status = 待支付
  └── 事务提交后：发布 ORDER_PAID
                          │
                          ▼
             yuexuan.order.event.exchange
                          │ order.paid
                          ▼
               yuexuan.order.paid.queue
                          │
                          ▼
        OrderPaidNotificationConsumer（手动 ACK）
                          │
                          ▼
                  WebSocket 通知运营端
```

## 2. 交换机、队列和路由键分别是什么

- **Producer（生产者）**：`OrderPaidEventPublisher`，发布 `OrderPaidEvent`；
- **Exchange（交换机）**：`yuexuan.order.event.exchange`，不保存消息，只按路由规则转发；
- **Routing key（路由键）**：`order.paid`，相当于事件类型；
- **Queue（队列）**：`yuexuan.order.paid.queue`，真正暂存消息；
- **Consumer（消费者）**：`OrderPaidNotificationConsumer`，从队列取消息并通知运营端。

这里用 Topic Exchange，是为了未来可为 `order.paid.points`、`order.paid.coupon` 等事件配置独立队列，而不会改动支付主链路。

## 3. 为什么要 Confirm、手动 ACK、重试、DLQ 和幂等

|机制|防范的问题|本项目实现|
|---|---|---|
|Producer Confirm|消息是否已到达 RabbitMQ|`publisher-confirm-type: correlated`，按 `eventId` 记录确认/失败日志|
|Return callback|消息到了交换机却没有队列接收|记录 exchange、routing key、eventId|
|手动 ACK|消费者还没完成业务就宕机而丢消息|WebSocket 通知处理完成后才 `basicAck`|
|重试|瞬时数据库/网络故障|消费失败最多尝试 3 次，指数退避|
|DLQ（死信队列）|反复失败的消息不能无限重投|最终 reject 后路由至 `yuexuan.order.paid.dlq`，可在管理台排查|
|消费者幂等|ACK 丢失等情况会引起重复投递|`processed_message(event_id, consumer_name)` 唯一键，重复事件只 ACK、不再通知|

RabbitMQ 的常见语义是“至少一次”，不是“恰好一次”。所以重复消息是正常情况；正确做法是让业务消费者能够安全地重复执行。

## 4. 代码入口与实际执行顺序

1. `OrderServiceImpl.paySuccess` 先通过 `WHERE id = ? AND status = 待支付` 改订单状态；
2. 只有条件更新成功，才构造一次性的 `OrderPaidEvent`；重复支付会在前面直接返回，绝不再发事件；
3. `publishOrderPaidAfterCommit` 注册事务提交回调，防止事务最终回滚却已经提醒运营端；
4. `OrderPaidEventPublisher` 发送 JSON 消息并等待 RabbitMQ Confirm；
5. 消费者先尝试写入幂等表，再发 WebSocket，并最后 ACK；重复投递只 ACK。

> 当前阶段仍有一个必须诚实说明的窗口：数据库已经提交、但 JVM 在直接发布 MQ 前崩溃时，事件可能未发出。下一阶段 **Outbox** 会把事件记录和订单更新写在同一 MySQL 事务里，由独立投递器扫描并补投，从而解决数据库与 MQ 双写不一致。

## 5. 本地启动和演示

在项目根目录的 `.env` 中设置（不要提交该文件），并将
`sky-server/src/main/resources/application-dev.yml.example` 复制为
`application-dev.yml` 后填写同一组 RabbitMQ 用户名和密码：

```env
MYSQL_ROOT_PASSWORD=你的本地密码
RABBITMQ_DEFAULT_PASS=本地演示密码
```

启动依赖：

```powershell
docker compose up -d mysql redis rabbitmq
```

RabbitMQ 管理台：`http://localhost:15672`，用户名为 `yuexuan`（或 `.env` 的 `RABBITMQ_DEFAULT_USER`），密码为 `.env` 中配置的值。可以观察 exchange、两个 queue、Ready/Unacked 消息以及 DLQ。

执行 `sql/migrations/V9__add_message_consume_idempotency.sql` 后，启动后端并完成一次模拟支付。预期日志顺序是：

```text
已投递 ORDER_PAID 事件，等待 RabbitMQ Confirm
RabbitMQ 确认收到 ORDER_PAID 事件
ORDER_PAID 事件消费完成并 ACK
```

可通过管理台临时停止消费者或制造数据库不可用来观察重试与 DLQ；不要在正式演示数据库中手工删除业务订单来制造失败。

## 6. 面试中可以怎么解释

“支付状态是强一致的核心交易数据，因此在本地事务中同步更新；运营提醒不是支付成功的前提，改为 MQ 异步处理。生产端 Confirm 确认 Broker 收到消息，消费端手动 ACK 保证业务完成后才确认。MQ 可能重复投递，所以以业务 eventId 加数据库唯一键实现幂等。重复失败的消息进入 DLQ 留给排查。阶段 A 仍有数据库与 MQ 的双写窗口，阶段 C 用 Outbox 补齐最终一致性。”
