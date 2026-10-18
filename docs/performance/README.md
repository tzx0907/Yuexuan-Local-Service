# 下单压测与一致性校验

这套材料用于证明“下单幂等、MySQL 条件扣库存、Outbox 本地事务、RabbitMQ 至少一次投递与消费者幂等”在一个可复现实验中协同工作。它不是生产容量承诺，也不应把本机结果包装成线上 QPS。

## 实验范围

默认实验是：一个初始库存为 **100** 的普通 SKU，100 个独立测试用户同时各购买 1 件，到店自提下单，单次突发最长 60 秒。

- 脚本：[`order-submit-k6.js`](order-submit-k6.js)
- 用户数据模板：[`vu-data.example.json`](vu-data.example.json)
- 数据库只读校验：[`order-consistency-check.sql`](order-consistency-check.sql)
- 结果记录模板：[`performance-report.md`](performance-report.md)
- MySQL 运行时诊断 SQL：[`mysql-diagnostics.sql`](mysql-diagnostics.sql)

下单接口按用户限流为 60 秒 5 次。因此 100 并发实验必须使用至少 100 个**独立测试用户**；不能复用一个 JWT，否则测到的是限流而不是库存并发控制。每个用户只调用一次“加购 → 下单”，避免同一购物车或同一幂等键互相污染。

## 前置条件

1. 使用独立的 Docker 演示库 `yuexuan_local_service`，不要对本机旧 `sky_take_out` 库执行本实验。
2. 启动 MySQL、Redis、RabbitMQ 与后端；确认门店为营业状态。
3. 选择一个普通 SKU，记录其 `sku_id` 和运行前库存，并将库存设置为 100。SKU 必须上架，且不要同时配置限时购活动。
4. 准备不少于并发数的压测用户。每项须有该用户的 JWT、共同压测的 `dishId`、`skuId`。本项目提供一个**默认关闭且仅 dev profile 可用**的本地辅助接口，可为一次压测生成带 `perf-k6-` 前缀的专用用户和 JWT；详见下方“准备本地压测用户”。为避免泄露 Token，复制模板为被 Git 忽略的本地文件：

   ```powershell
   Copy-Item docs/performance/vu-data.example.json docs/performance/vu-data.local.json
   ```

   不要提交 `vu-data.local.json`、终端历史或 k6 原始响应体。
5. 安装 k6：仅需在执行机安装，不加入项目 Maven 依赖。PowerShell 可用 `k6 version` 确认安装。

## 准备本地压测用户

该辅助接口不属于业务 API，只有同时满足以下条件才会注册：

```text
Spring profile = dev
sky.performance.enabled = true
```

先仅在本机忽略的 `sky-server/src/main/resources/application-dev.yml` 中增加：

```yaml
sky:
  performance:
    enabled: true
```

重启后端后，调用下面接口。`runId` 用于隔离本轮压测用户；它只能含字母、数字、连字符，最多 32 位。接口每次最多生成或复用 200 个 `perf-k6-<runId>-001` 形式的用户，响应 `data` 可直接保存为 `vu-data.local.json`。接口不会在日志中打印 Token。

```powershell
$runId = "stock100-$(Get-Date -Format yyyyMMddHHmmss)"
$prepared = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/_dev/performance/k6-users?runId=$runId&count=100&dishId=50&skuId=37"

$prepared.data | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 docs/performance/vu-data.local.json
```

将示例中的 `dishId=50` 和 `skuId=37` 替换为选定的普通上架商品与 SKU。先做单用户冒烟验证时，将 `count=100` 改成 `count=1`。完成压测后把 `sky.performance.enabled` 改回 `false` 或删除该段并重启后端；该接口随之不再注册。

## 执行 100 库存 / 100 并发下单实验

先在 MySQL 中记录开始时间与库存；将对应值填入校验 SQL 的变量区。随后在项目根目录执行：

```powershell
$usersJson = Get-Content -Raw docs/performance/vu-data.local.json
$runId = "order-stock100-$(Get-Date -Format yyyyMMddHHmmss)"
$env:K6_TEST_USERS_JSON = $usersJson
$env:RUN_ID = $runId
$env:VUS = '100'
k6 run docs/performance/order-submit-k6.js
```

脚本的 `per-vu-iterations` 模式会在一个测试窗口中让 100 个 VU 各执行 1 次业务链路；`maxDuration` 为 60 秒。它是“固定库存下的并发突发”验证，而不是持续 60 秒地无限创建订单——后者会自然耗尽 100 件库存，无法再用“成功数等于库存”评价结果。

结束后等待至少一个 Outbox 扫描周期（当前为 10 秒），将 `$runId`、实际开始/结束时间、`sku_id`、运行前库存、k6 的 `created_orders` 值填入 [`order-consistency-check.sql`](order-consistency-check.sql)，再通过 Docker MySQL 执行该只读脚本：

```powershell
Get-Content -Raw docs/performance/order-consistency-check.sql |
  docker exec -i yuexuan-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" yuexuan_local_service'
```

该命令只把 SQL 内容经标准输入交给容器内 MySQL 客户端；密码由容器已有的 `MYSQL_ROOT_PASSWORD` 环境变量提供，不会写进 PowerShell 命令历史。若 Docker 容器名不同，以 `docker compose ps` 为准。

## 如何判定通过

1. k6 的 `order_submit_business_success` 与成功订单数一致；当库存为 100 且 100 个合法用户各提交一次时，预期为 100 成功、0 业务失败。
2. 校验 SQL 的压测 SKU `stock_after = 0`、`actual_stock_decrement = 100`，且“全库负库存”查询返回 0 行。
3. 本次压测订单数不超过 100；`(user_id, submit_request_id)` 重复查询返回 0 行。
4. 每笔本次订单恰有一条 `ORDER_CLOSE` Outbox 事件。事件在投递器扫描后通常为 `SENT`；短暂 `PENDING`/`RETRY` 需结合 `last_error` 排查，而不是直接判定消息丢失。
5. `processed_message` 重复查询返回 0 行。ORDER_CLOSE 为 15 分钟延迟消息，压测后立即没有对应消费记录是预期行为。

## 重复提交实验

不要把重复提交混入库存实验。它应该单独执行：一个用户先加购 1 件，用同一个 `Idempotency-Key` 连续调用两次 `/user/order/submit`，检查两次返回同一订单 ID、订单表仅 1 行、库存只扣 1 次。详细请求示例见 [`../project-delivery-and-demo-guide.md`](../project-delivery-and-demo-guide.md)。

## 结果记录要求

在提交性能结论前，完整填写 [`performance-report.md`](performance-report.md)：CPU/内存、操作系统、JDK、Docker 资源限制、MySQL/Redis/RabbitMQ 镜像版本、VUs、运行时长、SKU 初始库存、接口、平均/P95/P99、错误率和所有 SQL 校验结果。没有完整环境与校验数据时，只能说“脚本已准备，尚未实测”。

## 性能瓶颈定位原则

首次 100 VUs / 100 件同 SKU 实验的正确性已经通过，但下单 P95 为 2.48s，未达到 1.5s 的本地目标。不要直接调大阈值，也不要先猜测连接池或索引问题。

该实验的 100 个事务会同时执行同一条 SKU 的条件扣减：`UPDATE product_sku ... WHERE stock >= ?`。为了不超卖，InnoDB 必须在该库存行上协调并发写入，因此部分锁等待是预期成本。下一轮同场景复测时，应在 k6 运行中执行 [`mysql-diagnostics.sql`](mysql-diagnostics.sql)。该脚本只使用 Docker MySQL 基础可用的 `SHOW`、`information_schema.INNODB_TRX` 与 `SHOW ENGINE INNODB STATUS`，不依赖不同镜像可能关闭或限制访问的 `performance_schema.data_lock_waits` 表。可据此区分以下情况：

| 观察 | 可能含义 | 后续动作 |
| --- | --- | --- |
| 锁等待集中在 `product_sku` 被测 SKU 行 | 同热点库存竞争，属于正确性与延迟的业务取舍 | 记录为热点 SKU 场景；后续评估库存分片、排队或限购策略 |
| `Threads_running` / `Max_used_connections` 接近上限 | 连接池或 MySQL 连接排队 | 依据实际快照再调整连接池 / MySQL 配额 |
| `SHOW ENGINE INNODB STATUS` 显示长时间锁等待或死锁 | 锁竞争超出预期或存在事务设计问题 | 结合事务 SQL 与 `EXPLAIN` 排查，不能直接取消条件扣库存 |
| 没有锁等待但延迟仍高 | 应用日志、CPU、Docker 资源或其他应用内步骤 | 采集应用分段耗时和容器资源再定位 |

`com.sky.mapper` 的常态日志级别已设为 `info`，避免把每条 MyBatis SQL 的 debug 输出写入控制台而干扰压测延迟。若要排查单条 SQL，可仅在本机 `application-dev.yml` 临时覆盖为 `debug`；压测复测时应保持 `info`，保证前后结果可比较。
