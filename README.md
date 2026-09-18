# 悦选本地到家服务平台

> 面向社区即时零售与到家服务场景的 Spring Boot 后端项目。用户可浏览商品与服务、加入购物车并提交配送订单；运营端可维护商品、服务组合、订单履约和经营数据。

本仓库从餐饮点单练习项目持续演进而来。目前优先完成了通用交易链路的工程化改造：可复现的本地开发环境、订单状态机、支付回调幂等与下单幂等。后续所有业务迭代均以“悦选本地到家服务平台”为产品主题。

## 产品定位

悦选服务周边社区，提供日用百货、轻食、生鲜等即时零售商品，以及可预约的本地到家服务。平台围绕“商品/服务选择 → 购物车 → 下单支付 → 门店接单 → 配送履约 → 售后评价”构建交易闭环。

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
- 商品浏览缓存使用 `yuexuan:product:list:{categoryId}` 规范 Key；运营端商品新增、编辑、上下架和删除时精确删除受影响分类缓存，不使用 Redis `KEYS` 通配符扫描。
- 普通商品库存使用 `UPDATE ... SET stock = stock - ? WHERE stock >= ? AND status = 1` 条件更新，避免并发下单超卖；库存扣减与创建订单位于同一事务中。

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

首次启动时，MySQL 会自动执行 `sql/sky_take_out_schema.sql` 创建数据库和表结构。MySQL 与 Redis 分别映射到本机 `3306` 和 `6379` 端口；若本机端口已被占用，请先停止冲突服务或调整 `docker-compose.yml` 中的端口映射。

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
    database: sky_take_out
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

1. **商品与 SKU 重构**：将菜品、口味升级为商品、SKU 与规格价格，并扩展组合商品库存扣减。
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
