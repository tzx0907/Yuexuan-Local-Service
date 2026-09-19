# 悦选用户端接口契约（第一版）

本文档以当前后端实现为准，是新用户端页面的接口依据。旧小程序中遗留的“餐厅、菜品、套餐、餐具”仅代表课程项目历史，不应再新增同类文案。

## 统一请求规则

- 后端地址（本地开发）：`http://localhost:8080`
- 用户登录后，JWT 放在请求头 `authentication`。
- 每个请求可携带 `X-Trace-Id`；后端也会在响应头返回该值。
- 成功响应：`{ code: 1, msg: null, data: ... }`；失败响应：`{ code: 0, msg: "错误原因" }`。

## 商品浏览与购物车

| 用户动作 | 方法与地址 | 核心请求参数 | 关键响应字段 |
| --- | --- | --- | --- |
| 查询分类 | `GET /user/category/list` | `type`（1 商品，2 服务组合） | `id`、`name`、`type` |
| 查询商品 | `GET /user/dish/list` | `categoryId` | `id`、`name`、`price`、`image`、`description`、`flavors`、`skus` |
| 查询服务组合 | `GET /user/setmeal/list` | `categoryId` | `id`、`name`、`price`、`image` |
| 加入购物车 | `POST /user/shoppingCart/add` | `dishId`、`skuId`、`setmealId`、`dishFlavor` | 无 |
| 购物车列表 | `GET /user/shoppingCart/list` | 无 | `dishId`、`skuId`、`name`、`amount`、`number` |
| 减少商品 | `POST /user/shoppingCart/sub` | 与加入购物车相同 | 无 |
| 清空购物车 | `DELETE /user/shoppingCart/clean` | 无 | 无 |

## 提交订单

`POST /user/order/submit`

除 JWT 外，必须包含下列请求头：

```http
Idempotency-Key: <本次下单生成的 UUID>
X-Trace-Id: <可选；前端生成或沿用>
```

请求体：

```json
{
  "addressBookId": 1,
  "payMethod": 1,
  "remark": "请电话联系",
  "deliveryStatus": 1,
  "tablewareNumber": 0,
  "tablewareStatus": 1,
  "packAmount": 0,
  "amount": 39.90
}
```

前端规则：第一次点击“提交订单”生成 Key；网络重试必须复用同一个 Key；拿到成功结果或用户主动退出确认页后才清除该 Key。接口同时受“同用户 60 秒最多 5 次”的 Redis 限流保护。
