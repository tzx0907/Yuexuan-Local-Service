import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import exec from 'k6/execution';

/*
 * 悦选下单一致性压测（不使用同一 JWT 伪造高并发）。
 *
 * 每个 VU 只做一次“加购 -> 下单”，并从 K6_TEST_USERS_JSON 读取独立用户。
 * 这样既遵守每用户 60 秒 5 次的下单限流，也能对同一 SKU 进行真实的
 * 并发条件扣库存验证。脚本不会支付订单；每一笔成功下单应产生一条
 * ORDER_CLOSE Outbox 事件，供后续 SQL 校验。
 */

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const vus = Number(__ENV.VUS || 100);
const runId = __ENV.RUN_ID;
const rawUsers = __ENV.K6_TEST_USERS_JSON;

if (!rawUsers) {
  fail('缺少 K6_TEST_USERS_JSON。请按 docs/performance/vu-data.example.json 准备本地测试用户数据。');
}
if (!runId) {
  fail('缺少 RUN_ID。请在启动 k6 前显式设置本轮实验标识，供 SQL 与日志关联。');
}

const users = JSON.parse(rawUsers);
if (!Array.isArray(users) || users.length < vus) {
  fail(`压测需要至少 ${vus} 个独立测试用户，当前仅提供 ${Array.isArray(users) ? users.length : 0} 个。`);
}

const addCartFailures = new Counter('cart_add_failures');
const submitFailures = new Counter('order_submit_failures');
const createdOrders = new Counter('created_orders');
const submitLatency = new Trend('order_submit_latency', true);
const businessSuccess = new Rate('order_submit_business_success');

export const options = {
  scenarios: {
    order_submit_burst: {
      executor: 'per-vu-iterations',
      vus,
      iterations: 1,
      maxDuration: '60s',
      gracefulStop: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    order_submit_business_success: ['rate>0.99'],
    order_submit_latency: ['p(95)<1500'],
  },
};

function headers(token, idempotencyKey) {
  const result = {
    'Content-Type': 'application/json',
    authentication: token,
    'X-Performance-Run-Id': runId,
  };
  if (idempotencyKey) {
    result['Idempotency-Key'] = idempotencyKey;
  }
  return result;
}

function isBusinessSuccess(response) {
  if (response.status !== 200) {
    return false;
  }
  try {
    return JSON.parse(response.body).code === 1;
  } catch (_) {
    return false;
  }
}

export default function () {
  // VU 编号从 1 开始。每一个 VU 独占一个用户，避免购物车与限流相互干扰。
  const user = users[exec.vu.idInTest - 1];
  const cartPayload = JSON.stringify({
    dishId: user.dishId,
    skuId: user.skuId,
    normalPurchase: true,
  });

  const addResponse = http.post(
    `${baseUrl}/user/shoppingCart/add`,
    cartPayload,
    { headers: headers(user.token) },
  );
  const cartOk = isBusinessSuccess(addResponse);
  check(addResponse, {
    '加购 HTTP 200': (response) => response.status === 200,
    '加购业务成功': () => cartOk,
  });
  if (!cartOk) {
    addCartFailures.add(1);
    businessSuccess.add(false);
    return;
  }

  // deliveryStatus=2 为到店自提，不依赖压测用户各自的地址簿数据。
  const key = `${runId}-vu${exec.vu.idInTest}-iter${exec.scenario.iterationInTest}`;
  const submitResponse = http.post(
    `${baseUrl}/user/order/submit`,
    JSON.stringify({
      deliveryStatus: 2,
      payMethod: 1,
      remark: `k6:${runId}`,
    }),
    { headers: headers(user.token, key) },
  );
  submitLatency.add(submitResponse.timings.duration);
  const submitOk = isBusinessSuccess(submitResponse);
  check(submitResponse, {
    '下单 HTTP 200': (response) => response.status === 200,
    '下单业务成功': () => submitOk,
  });
  businessSuccess.add(submitOk);
  if (submitOk) {
    createdOrders.add(1);
  } else {
    submitFailures.add(1);
  }
}
