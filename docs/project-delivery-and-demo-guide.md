# 悦选本地到家服务平台：交付与演示说明

## 1. 项目定位

悦选本地到家服务平台服务社区即时零售和预约上门服务。用户端提供商品、组合商品、限时购、配送、自提和预约；管理端提供商品/SKU、组合、限时购、订单和接单状态管理。

工程内部仍保留 `dish`、`setmeal`、`dish_flavor` 等课程项目物理表/字段名以保证现有数据和接口兼容。它们的业务含义分别是“商品”“组合商品”“规格元数据”，不是用户端的餐饮概念；用户界面不应展示这些技术名称。

## 2. 数据与定价约定

| 对象 | 唯一价格来源 | 唯一库存来源 | 用户端展示 |
| --- | --- | --- | --- |
| 鲜花绿植单品 | `dish.price` | `dish.stock` | 单品价，直接加购 |
| 有规格商品 | `product_sku.price` | `product_sku.stock` | 最低价“起”，必须选择规格 |
| 组合商品 | `setmeal.price` | 组成商品/SKU 的库存 | 组合价、组合名称和组成明细 |
| 限时购 | `flash_sale_activity.sale_price` | 活动库存 + 对应 SKU 库存 | 限时购标识、活动价、余量与每人限购 |

`dish.stock` 对有 SKU 的商品只是全部在售 SKU 库存之和，用于展示；下单、取消和超时回补均按实际 SKU 操作。不能把商品级库存与 SKU 库存当成两份库存。

组合商品配置在 `setmeal_dish`。组合下单会展开所有组成项：有 `sku_id` 时扣相应 SKU，否则扣单品库存；任意一项扣减失败，整个本地事务回滚。取消、商家取消、拒单和支付超时沿同一明细回补路径处理。

## 3. 交易可靠性实现

### 下单和支付

- 下单请求用 `Idempotency-Key` 做 Redis 短期防重，并由订单表 `(user_id, submit_request_id)` 唯一约束兜底。
- 下单接口按用户用 Redis Lua 固定窗口限流；限流保护流量，幂等保护重复提交，两者职责不同。
- 库存通过 MySQL 条件更新扣减，例如 `stock >= quantity`；扣减、订单和明细在同一事务。
- 支付与订单状态迁移使用 `WHERE id=? AND status=?` 条件更新，重复支付不会重复更新或重复发布事件。

### RabbitMQ、超时与 Outbox

```text
创建订单/支付成功（MySQL 本地事务）
  └─ 同事务写 outbox_event(PENDING)
            ↓ OutboxDispatchTask 扫描、抢占、投递
        RabbitMQ Topic Exchange
          ├─ ORDER_PAID → 管理端 WebSocket 新订单通知
          └─ ORDER_CLOSE 延迟队列 → 15 分钟后超时关单
                                      ↓
                         条件更新取消订单 + 事务回补库存
```

- Outbox 将订单状态变更和待发送事件放进同一个 MySQL 事务，避免“订单已成功但 JVM 在发送 MQ 前宕机”造成事件无记录。
- 投递器按 `PENDING / SENDING / RETRY / SENT` 管理事件；失败指数退避，最长 5 分钟，超时的 `SENDING` 可重新领取。
- RabbitMQ 使用发布确认、手动 ACK、失败重试和 DLQ。
- 消费端借助 `processed_message(event_id, consumer_name)` 唯一约束幂等；超时关单还由订单状态条件更新防止重复回补。
- `OrderTask` 是 MQ 故障时的兜底扫描，不替代正常的延迟消息处理。

## 4. 限时购规则

- 限时购关联具体 SKU，不是笼统商品。
- 购物车行以 `flash_sale_activity_id` 作为区分键，普通购买和限时购同一 SKU 不会合并。
- 加购只做活动有效性、余量和个人限额预检查；真正扣活动库存、SKU 库存与写个人配额发生在提交订单事务中。
- 限购失败的用户提示为“已超过购买上限，本次活动每人最多购买 N 件”。

## 5. 建议演示流程

启动 MySQL、Redis、RabbitMQ、后端、Nginx 管理端后，按以下顺序演示最清晰。

1. 管理端商品管理：打开一个有 SKU 商品，展示规格、独立价格和库存；再展示鲜花绿植为单品。
2. 管理端组合管理：打开“家庭清洁组合”，说明组成 SKU、单买总价和组合 8 折价。
3. 用户端普通购买：选择两个不同规格，确认弹窗价格、购物车行和总价随 SKU 变化。
4. 用户端组合购买：加入组合后确认购物车显示“组合价”；订单详情、历史订单和管理端订单详情均显示组合名和组成内容。
5. 限时购：创建/启用一个活动，用户端左侧出现“限时购”；同 SKU 分别普通加购和限时购，确认两条购物车行互不合并；超过个人限额时检查提示。
6. 三种履约：实物配送显示地址和配送费；到店自提显示取货码（`orders.id` 纯数字）且不显示配送地址/配送时间；上门服务显示预约日期时间和备注，且不能与实物同单。
7. 订单状态：支付后管理端收到新订单通知；用户催单后管理端收到催单通知；依次接单、配送/完成或拒单/取消，检查双方详情状态一致。
8. 可靠性说明：展示 `outbox_event`、RabbitMQ 队列和 `processed_message` 的作用；创建未支付订单后说明 15 分钟关闭及库存回补。
9. 暂停接单：管理端关闭服务状态，用户端首页显示暂停接单，提交订单被后端明确拒绝。

## 6. 可执行的接口验收步骤

以下命令假定后端运行在 `http://localhost:8080`，并已通过小程序或登录接口取得用户 JWT。将占位符替换为当前环境的真实值；不要把 Token、密码或支付证书提交到仓库。

```powershell
$base = 'http://localhost:8080'
$token = '用户 JWT'
$headers = @{ authentication = $token }

# 1. 浏览分类商品，确认 SKU 商品返回 skus，组合商品返回 type=2。
Invoke-RestMethod "$base/user/dish/list?categoryId=17" -Headers $headers

# 2. 清空购物车后，以明确 skuId 加购；同一商品换另一 skuId 应形成另一购物车行。
Invoke-RestMethod "$base/user/shoppingCart/add" -Method Post -Headers $headers -ContentType 'application/json' `
  -Body '{"dishId":62,"skuId":123}'
Invoke-RestMethod "$base/user/shoppingCart/list" -Headers $headers
```

`skuId` 不应在文档中写死；从第一步返回的 `skus[].id` 选择。若不传 `skuId` 请求有 SKU 的商品，后端应返回“请选择商品规格”。库存不足时可在管理端把某个 SKU 库存调为 0，再按该 SKU 加入购物车，预期立即返回库存不足；最终提交订单仍以 MySQL 条件扣减作为并发安全兜底。

下单必须显式携带 `Idempotency-Key`。同一个 Key 和同一业务请求连续发两次，第二次应返回第一次创建的订单，而不是生成新订单：

```powershell
$idempotencyKey = [guid]::NewGuid().ToString()
$submitHeaders = @{ authentication = $token; 'Idempotency-Key' = $idempotencyKey }
$body = '{"addressBookId":1,"deliveryStatus":1,"payMethod":1,"remark":"接口验收"}'
Invoke-RestMethod "$base/user/order/submit" -Method Post -Headers $submitHeaders -ContentType 'application/json' -Body $body
Invoke-RestMethod "$base/user/order/submit" -Method Post -Headers $submitHeaders -ContentType 'application/json' -Body $body
```

支付后检查订单从“待支付”变为“待接单”，再在 MySQL 中按订单 ID 查看 `outbox_event`。投递器处理后事件会进入 `SENT`，RabbitMQ 管理台 `http://localhost:15672` 可查看支付通知和延迟关单队列。响应头中的 `X-Trace-Id` 可用于在后端日志中搜索同一次请求的过滤器、鉴权、下单、库存或异常记录。

## 7. 验收清单

| 场景 | 预期结果 |
| --- | --- |
| SKU 商品未选择规格 | 不能加入购物车，提示选择商品规格 |
| 两个 SKU 同时加购 | 分为两行，金额为各自 SKU 单价 × 数量 |
| 组合内库存不足 | 整个组合下单失败，不产生部分扣库存 |
| 普通商品与组合同单 | 金额为各购物车行金额之和；取消/超时分别回补普通项和组合组成项 |
| 自提订单 | 不展示配送地址/配送时间，取货码为订单自增 ID |
| 上门服务与实物混合 | 提交前明确拒绝，要求分开下单 |
| 限时购与普通购买同 SKU | 两条购物车行不合并；提交订单才原子占用活动库存和个人配额 |
| 重复支付/重复 MQ 消息 | 状态和通知不重复处理 |
| 管理端暂停接单 | 首页可见暂停状态，后端提交接口拒绝新订单 |

## 8. Docker 全链路验收记录

本项目使用 Docker Compose 启动基础设施，宿主机端口如下：

| 服务 | 宿主机入口 | 容器内部端口 | 验收内容 |
| --- | --- | --- | --- |
| MySQL 8 | `localhost:3307` | `3306` | 自动创建 `yuexuan_local_service`，挂载基础建表脚本并执行增量迁移 |
| Redis 7 | `localhost:6380` | `6379` | Cache Aside、限流和店铺接单状态读写 |
| RabbitMQ 3.13 | `localhost:5672` | `5672` | Outbox 投递、支付通知和延迟关单 |
| RabbitMQ 管理台 | `http://localhost:15672` | `15672` | exchange、queue、Ready / Unacked 与 DLQ 观察 |

Docker 全链路验收时，应确保宿主机原生 RabbitMQ 服务未占用 `5672` / `15672`。应用 RabbitMQ 账号只需具备 `/` vhost 的 configure、write、read 权限；调用管理台 HTTP API 还需要额外的管理 tag，这与后端 AMQP 连接无关。

已验收链路：用户模拟登录、JWT 鉴权、地址簿、SKU 加购、加购库存预检查、配送下单、订单落库、店铺暂停接单的用户端展示与后端拒单。支付、Outbox 与 RabbitMQ 可继续按第 6 节使用同一订单完成验收。

### Flyway 迁移接管

Flyway 在 Spring Boot 启动阶段、业务 Bean 初始化之前运行，迁移记录存储于 `flyway_schema_history`。Docker 首次创建时仍由 Compose 导入基础 Schema；Flyway 以版本 `0` 建立基线，再执行 `classpath:db/migration/` 的 V2.1～V23 唯一版本升级。已有的完整 Docker 演示库应将本机 `application-dev.yml` 的 `spring.flyway.baseline-version` 设为 `23`，仅建立基线记录，不重跑历史迁移。

`V6__clear_legacy_transaction_history.sql` 被刻意排除在 Flyway 自动目录外，因为它会删除交易和购物车演示数据。后续任何结构或数据升级只新增 `V24__...sql` 及更高版本，不修改已执行文件，也不使用 Flyway `clean`。

## 9. 最终提交建议（不执行提交）

建议将当前工作分两次提交，便于代码审阅和回滚：

```text
feat(catalog): standardize product sku pricing and bundle inventory
docs(delivery): add Yuexuan acceptance checklist and demo guide
```

第一条包括 V17～V23 迁移、库存/组合/SKU 代码和小程序展示；第二条包括本说明、README 和既有 RabbitMQ/Outbox 学习文档的修订。提交前执行：

```powershell
mvn -q -pl sky-server -am test
mvn -q -pl sky-server -am package -DskipTests
```

小程序是编译产物，修改后必须在微信开发者工具重新编译；后端 Java 修改后必须由运行者重启后端，不能只刷新小程序。
