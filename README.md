# 悦选本地到家服务平台

> 面向社区即时零售与到家服务场景的全栈项目。用户可浏览商品与服务、选择 SKU、购买组合商品或限时购，并完成配送、自提或预约上门；运营端可维护商品、服务组合、订单履约和经营数据。

本仓库从餐饮点单练习项目持续演进而来，当前已完成订单状态机、支付与下单幂等、SKU 库存、Redis Cache Aside 与 Lua 限流、RabbitMQ 超时关闭、Outbox 可靠投递、限时购和组合商品闭环。所有业务迭代均以“悦选本地到家服务平台”为产品主题。

用户端前端统一位于 [`frontend/`](frontend/README.md)。当前已迁入可运行的小程序基线 `frontend/legacy-mp-weixin`；课程资料未包含其原始 uni-app 源码，因此后续会在 `frontend/yuexuan-miniprogram` 中重建可维护的源码，并逐页完成接口与产品主题迁移。

已有数据库升级请执行 [`sql/migrations/V2__yuexuan_product_domain.sql`](sql/migrations/V2__yuexuan_product_domain.sql)。该迁移不会删除旧表或历史订单；它补齐 SKU 购物车字段、查询索引，并提供以“商品/服务组合”命名的兼容视图。

课程库中的餐饮演示分类、商品文案和图片可使用 [`sql/migrations/V3__yuexuan_demo_catalog.sql`](sql/migrations/V3__yuexuan_demo_catalog.sql) 替换为悦选社区商品与到家服务演示数据。执行后需清理 Redis 商品缓存或重启 Redis，才能立即看到更新。

## 产品定位

悦选服务周边社区，提供日用百货、生鲜果蔬、乳品烘焙、家庭清洁、宠物用品、鲜花绿植、健康护理等即时零售商品，以及可预约的本地到家服务。平台围绕“商品/服务选择 → 购物车 → 下单支付 → 门店接单 → 配送、自提或上门履约”构建交易闭环。

当前版本的餐饮领域对象暂作为通用交易模型使用：

| 当前实现名称 | 悦选平台中的业务含义 | 后续演进方向 |
| --- | --- | --- |
| 菜品（Dish） | 标准化商品或服务项目 | 统一为商品（Product） |
| 口味（DishFlavor） | 商品规格/服务选项 | 统一为 SKU 规格 |
| 套餐（Setmeal） | 商品组合或服务套餐 | 统一为组合商品（Bundle） |
| 店铺（Shop） | 门店或服务站点 | 支持多门店、服务范围与营业时间 |
| 派送订单 | 本地配送/上门履约订单 | 支持配送与预约上门两种履约方式 |

这是有意保留的渐进式改造：先复用稳定交易主链路，再逐步替换领域模型，而不是为了改名一次性破坏已有接口和数据。

## 当前功能

### 运营端

- 运营人员登录、账号、密码与状态管理
- 工作台数据概览：交易额、有效订单、用户与商品统计
- 商品、规格、组合商品、分类的维护与上下架
- 门店营业状态管理
- 订单查询、接单、拒单、取消、配送与完成履约
- 交易额、用户、订单、销量 Top10 等经营报表
- 商品图片上传至阿里云 OSS
- 通过 WebSocket 接收新订单提醒

### 用户端

- 微信小程序登录与 JWT 鉴权
- 商品、服务组合、分类浏览
- 商品列表采用 Cache Aside：Redis 命中直接返回；未命中回源 MySQL 并以随机 TTL 回填缓存
- 普通商品库存管理：下单时通过 MySQL 条件更新原子扣减库存，库存不足或商品下架时拒绝创建订单
- 商品 SKU：支持规格名称、规格值、独立售价、独立库存与上下架；购物车和订单明细保留 SKU 及规格快照
- 购物车添加、减少和清空
- 常用服务地址管理与默认地址设置
- 提交订单、模拟支付、订单详情与历史订单
- 催单、再次购买
- 门店营业状态查询

## 交易可靠性

订单状态由 Service 层统一控制，不能通过通用更新接口任意修改：

```text
待支付 -> 待接单 -> 已接单 -> 配送中 -> 已完成
   |         |          |
   +-------> 已取消 <----+
```

- 支付仅允许将待支付订单变为待接单；运营人员只能按规定顺序接单、配送和完成履约。
- 用户仅能取消待支付或待接单订单，且只能取消自己的订单。
- 每次状态更新均使用 `WHERE id = ? AND status = ?` 条件更新，避免并发请求覆盖新状态。
- 支付成功处理具有幂等性：重复支付请求或回调只会首次更新订单并发送来单提醒。
- 下单接口要求请求头 `Idempotency-Key`：Redis 用 `SETNX` 拦截短时重复请求；订单表用 `(user_id, submit_request_id)` 联合唯一索引提供最终兜底。重复请求会返回第一次创建的订单。
- 下单接口使用 Redis Lua 脚本做按用户固定窗口限流：同一用户 60 秒内最多提交 5 次。Lua 将计数与设置过期时间原子执行；触发限流时返回“操作过于频繁，请稍后再试”。限流是流量保护，幂等是重复请求保护，二者互补。
- 商品浏览缓存使用 `yuexuan:v2:product:list:{categoryId}` 规范 Key；运营端商品新增、编辑、上下架和删除时精确删除受影响分类缓存，不使用 Redis `KEYS` 通配符扫描。`v2` 用于隔离旧餐饮演示数据缓存。
- 普通商品库存使用 `UPDATE ... SET stock = stock - ? WHERE stock >= ? AND status = 1` 条件更新，避免并发下单超卖；库存扣减与创建订单位于同一事务中。
- 选择 SKU 的商品下单时优先扣减 SKU 库存；订单明细保存 `skuId` 与规格快照，避免后续规格变更影响历史订单展示。
- 每个 HTTP 请求会携带或生成 `X-Trace-Id`，该值写入日志 MDC 并原样返回响应头。可按 `traceId` 串联“请求开始、鉴权、限流、订单、支付、异常”等同一次请求的日志；非法请求头不会直接写日志，避免日志污染。
- 创建订单和支付成功会在同一个本地事务内写入 `outbox_event`；后台投递器可靠发布 RabbitMQ 消息，消费者以 `eventId` 幂等处理。详见 [`docs/project-delivery-and-demo-guide.md`](docs/project-delivery-and-demo-guide.md)。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 基础框架 | Spring Boot 2.7.3、Spring MVC |
| 持久层 | MyBatis、PageHelper、MySQL |
| 缓存与状态 | Redis、Spring Cache |
| 安全 | JWT（运营端与用户端独立密钥） |
| 文件存储 | 阿里云 OSS |
| 接口文档 | Knife4j / Swagger |
| 实时通信 | WebSocket |
| 测试 | JUnit 5、Mockito |
| 构建工具 | Maven、JDK 17 |

## 项目结构

```text
yuexuan-local-service/              # Maven 父工程产物名
├── sky-common/                      # 公共配置、工具类、常量、异常、结果封装
├── sky-pojo/                        # Entity、DTO、VO 等业务对象
├── sky-server/                      # Spring Boot 启动模块、Controller、Service、Mapper
├── sql/                             # 建表脚本与增量迁移脚本
├── docker-compose.yml               # 本地 MySQL、Redis
└── pom.xml
```

> `sky-*` 目录和 `com.sky` 包名是历史技术标识，当前暂不影响对外产品名称或运行。待商品模型、门店模型重构时会以小步提交逐步替换，避免大规模重命名造成回归。

## 环境要求

- JDK 17
- Maven 3.6+
- MySQL 8.0
- Redis 7+
- 可选：阿里云 OSS、微信小程序与微信支付相关配置

## 快速开始

### 1. 启动本地依赖

项目提供 Docker Compose，用于启动 MySQL 8.0 和 Redis 7。复制环境变量模板并填写一个仅用于本地开发的 MySQL root 密码：

```bash
cp .env.example .env
docker compose up -d
docker compose ps
```

首次初始化时，执行 `sql/sky_take_out_schema.sql` 创建 `Yuexuan-Local-Service` 数据库和表结构。MySQL 与 Redis 分别映射到本机 `3306` 和 `6379` 端口；若本机端口已被占用，请先停止冲突服务或调整 `docker-compose.yml` 中的端口映射。

### 2. 初始化数据库

项目提供 `sql/sky_take_out_schema.sql`，用于创建空的本地开发数据库及其表结构：

```bash
mysql -u root -p < sql/sky_take_out_schema.sql
```

使用 Docker Compose 首次启动时无需手动执行该命令。脚本仅包含数据库和表结构，不包含用户、订单或其他业务数据。脚本会删除同名表，因此只应在新建的本地开发数据库中执行。

### 3. 配置开发环境

复制 `sky-server/src/main/resources/application-dev.yml.example` 为 `application-dev.yml`，再填写本地服务和第三方服务的真实配置。不要将密码、AccessKey、微信私钥或证书提交到 Git。

```yaml
sky:
  datasource:
    host: localhost
    port: 3306
    database: Yuexuan-Local-Service
    username: root
    password: your-password
  redis:
    host: localhost
    port: 6379
    password:
    database: 0
```

### 4. 测试、编译与启动

在项目根目录执行：

```bash
mvn test
mvn clean package
mvn -pl sky-server -am spring-boot:run
```

服务默认监听 `http://localhost:8080`；接口文档地址为 `http://localhost:8080/doc.html`。管理员端接口通常以 `/admin` 开头，用户端接口通常以 `/user` 开头。

## 后续演进路线

1. **组合商品库存**：为商品组合建立物料清单，并按组合明细扣减多个 SKU 库存。
2. **多门店与服务范围**：门店营业时间、配送半径、服务区域、商品可售范围。
3. **履约能力**：配送方式、预约时间窗、配送员接单与履约轨迹。
4. **营销与售后**：优惠券、满减、退款、评价与投诉。
5. **工程化能力**：Redis 缓存一致性、接口限流、消息队列与可观测性。

## 开发约定

- 提交前执行 `mvn test`；外部 OSS、HTTP、Redis 演示测试已标记为手动检查，不在默认构建中运行。
- 新增交易状态必须通过 Service 层和状态机控制，避免 Controller 直接更新数据库。
- 新增下单类接口必须考虑幂等、并发更新和事务边界。
- 数据库、Redis、OSS 和微信配置必须按环境隔离，敏感信息不得提交到 Git。

## 许可证

本项目目前未声明开源许可证。若用于商业分发或二次发布，请先与仓库维护者确认授权范围。
