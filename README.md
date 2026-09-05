# 苍穹外卖（Sky Take-Out）

一个基于 Spring Boot 的前后端分离外卖业务后台服务，覆盖商家管理端与用户端核心业务流程。项目采用 Maven 多模块组织代码，适合作为外卖系统学习、二次开发和接口联调的基础项目。

> 仓库地址：[https://github.com/tzx0907/fly-take-out](https://github.com/tzx0907/fly-take-out)

## 功能概览

### 管理端

- 员工登录、退出、账号与密码管理
- 工作台数据概览：营业额、有效订单、用户与菜品统计
- 菜品、口味、套餐、分类的增删改查与启停售
- 店铺营业状态管理
- 订单查询、详情查看、接单、拒单、取消、派送与完成
- 营业额、用户、订单、销量 Top10 等报表统计
- 图片上传至阿里云 OSS
- 通过 WebSocket 接收新订单提醒

### 用户端

- 微信小程序登录与 JWT 鉴权
- 菜品、套餐、分类浏览
- 购物车添加、减少和清空
- 常用收货地址管理与默认地址设置
- 提交订单、模拟支付、订单详情与历史订单
- 订单催单、再来一单
- 店铺营业状态查询

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 基础框架 | Spring Boot 2.7.3、Spring MVC |
| 持久层 | MyBatis、PageHelper、MySQL |
| 缓存与状态 | Redis、Spring Cache |
| 安全 | JWT（管理员端与用户端独立密钥） |
| 文件存储 | 阿里云 OSS |
| 接口文档 | Knife4j / Swagger |
| 实时通信 | WebSocket |
| 工具库 | Lombok、Fastjson、Apache POI、Druid |
| 构建工具 | Maven、JDK 8+ |

## 项目结构

```text
sky-take-out/
├── sky-common/      # 公共配置、工具类、常量、异常、结果封装
├── sky-pojo/        # Entity、DTO、VO 等业务对象
├── sky-server/      # Spring Boot 启动模块、Controller、Service、Mapper
└── pom.xml          # 父工程与模块依赖管理
```

## 环境要求

- JDK 8 或更高版本（建议 JDK 8/11）
- Maven 3.6+
- MySQL 5.7/8.0
- Redis 5+
- 可选：阿里云 OSS、微信小程序与微信支付相关配置

## 快速开始

### 1. 初始化数据库

创建业务数据库并执行与当前版本匹配的建表及初始化脚本。仓库当前未固定提交数据库脚本，请根据实体与 Mapper 定义准备 `sky_take_out` 数据库及相关表结构。

确保 MySQL 字符集使用 `utf8mb4`，并记录数据库名称、账号和密码。

### 2. 配置开发环境

项目默认激活 `dev` 配置。请在 `sky-server/src/main/resources` 下准备对应的开发配置文件，并填写以下配置项：

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
  alioss:
    endpoint: your-oss-endpoint
    access-key-id: your-access-key-id
    access-key-secret: your-access-key-secret
    bucket-name: your-bucket
  wechat:
    appid: your-appid
    secret: your-secret
```

不要将真实密码、AccessKey、微信私钥或证书提交到 Git。生产环境建议使用环境变量或外部配置中心注入敏感信息。

### 3. 编译并启动

在项目根目录执行：

```bash
mvn clean package
mvn -pl sky-server -am spring-boot:run
```

服务默认监听 `http://localhost:8080`。也可以运行 `sky-server` 模块中的 `com.sky.SkyApplication` 启动类。

### 4. 查看接口文档

启动成功后访问：

```text
http://localhost:8080/doc.html
```

Knife4j 页面可用于查看接口、参数和在线调试。管理员端接口通常以 `/admin` 开头，用户端接口通常以 `/user` 开头。

## 配置说明

| 配置项 | 用途 |
| --- | --- |
| `server.port` | HTTP 服务端口，默认 `8080` |
| `sky.datasource.*` | MySQL 连接信息 |
| `sky.redis.*` | Redis 连接信息 |
| `sky.jwt.*` | 管理员端和用户端 JWT 配置 |
| `sky.alioss.*` | 菜品图片等文件上传配置 |
| `sky.wechat.*` | 微信登录、支付与回调配置 |

当前代码中的订单支付服务支持开发环境模拟支付，接入真实微信支付前请完善商户号、证书、API v3 密钥和回调地址配置，并通过 HTTPS 暴露回调接口。

## 开发建议

- 统一使用 UTF-8 编码，提交前执行 `mvn test` 或 `mvn package` 检查编译结果。
- 数据库、Redis、OSS 和微信配置按环境隔离，避免开发配置覆盖生产配置。
- 新增接口时同步补充 Swagger/Knife4j 注解及必要的参数校验。
- 订单状态变更应通过 Service 层完成，避免 Controller 直接操作数据库。

## 许可证

本项目目前未声明开源许可证。若用于商业分发或二次发布，请先与仓库维护者确认授权范围。




