# RabbitMQ 实战三：Outbox 可靠消息

## 要解决的最后一个漏洞

上一阶段的代码在订单事务提交后，直接向 RabbitMQ 发送消息。它比事务中发送更安全，但仍有一个时间窗口：

```text
订单支付状态提交成功
    ↓
应用进程突然退出
    ↓
还没来得及调用 RabbitMQ 发送
```

此时订单已经是已支付，但运营提醒事件永久丢失。订单创建后的延迟关单事件也有相同问题。

## Outbox 怎么做

Outbox 是数据库中的 `outbox_event` 表。支付成功或创建订单时，不直接发送 RabbitMQ，而是在**同一个 MySQL 事务**中同时写入：

```text
orders 状态更新为已支付
outbox_event 插入一条 ORDER_PAID 事件
```

事务只有两种结果：两条都提交，或两条都回滚。因此不再有“订单成功但连一条待发送记录都没有”的情况。

后台 `OutboxDispatchTask` 每 10 秒扫描待发送事件，先通过条件更新抢占为 `SENDING`，再发布到 RabbitMQ。只有同时满足以下条件才标记 `SENT`：

1. RabbitMQ 的 correlated publisher confirm 返回 ACK；
2. mandatory 路由没有收到 broker returned message。

确认超时、NACK、无匹配队列、序列化异常或网络异常都会记录最近错误、增加重试次数，并按指数退避安排下一次投递。第 1 次失败等待 2 秒，随后为 4 秒、8 秒，最长 300 秒。

```text
订单事务
  ├─ orders / order_detail / 库存
  └─ outbox_event（PENDING）
           ↓ 每 10 秒
    OutboxDispatchTask
           ↓
       RabbitMQ
           ↓
  消费者的幂等处理 / 条件更新
```

## 为什么仍可能重复发送

发送成功后，如果应用恰好在标记 `SENT` 前退出，重启后这条事件会再次发送。这是可靠投递中允许的“至少一次”语义。

重复不会造成重复业务：`ORDER_PAID` 消费者通过 `processed_message(event_id, consumer_name)` 去重；超时关单通过 `WHERE status = 待支付` 的条件更新防止重复取消与重复回补库存。

## 状态说明

| 状态 | 含义 |
| --- | --- |
| `PENDING` | 事务已提交，等待首次发送 |
| `SENDING` | 某个实例已经抢到发送权 |
| `RETRY` | 发送失败，等待退避后重试 |
| `SENT` | Broker 已 confirm ACK 且消息已路由到目标队列 |

多个应用实例同时跑任务时，会先通过条件更新把一条记录抢成 `SENDING`，只有抢到的实例发送。超过 5 分钟仍是 `SENDING` 的记录会被重新调度，应对发送进程中断。

`SENT` 不等于“消费者业务已经完成”，只代表生产端已经获得 RabbitMQ 对该消息的确认；消费者侧仍以手动 ACK、有限重试、DLQ 和幂等设计处理下游失败与重复投递。

可执行的生产端失败、消费者重试、DLQ 与恢复演示，以及长时间未投递事件的排查 SQL，见 [`outbox-failure-demo.md`](outbox-failure-demo.md)。
