// Load test for the whole saga: place an order, then wait until it is CONFIRMED.
//
//   k6 run load-test/order-flow.js
//   (or with Docker, see README) and tune with:  -e PEAK_RATE=15  -e POLL_SECONDS=1  -e PRODUCTS=50
//
// order_confirm_ms = time from "POST /api/orders" until the order is CONFIRMED
// (the full chain: Order -> Kafka -> Inventory -> Kafka -> Order -> Kafka -> Payment -> Kafka -> Order).
import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const ORDER_URL = __ENV.ORDER_URL || 'http://localhost:8082';
const INVENTORY_URL = __ENV.INVENTORY_URL || 'http://localhost:8081';
const PRODUCTS = parseInt(__ENV.PRODUCTS || '50');   // orders are spread over this many products
const PEAK_RATE = parseInt(__ENV.PEAK_RATE || '15'); // orders per second at the peak
const POLL_SECONDS = parseFloat(__ENV.POLL_SECONDS || '1'); // how often to check if the order is confirmed
const JSON_HEADERS = { 'Content-Type': 'application/json' };

const confirmTime = new Trend('order_confirm_ms', true);
const confirmed = new Rate('orders_confirmed');

export const options = {
  scenarios: {
    place_orders: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 400,
      stages: [
        { target: Math.ceil(PEAK_RATE / 3), duration: '20s' },
        { target: Math.ceil(PEAK_RATE / 3), duration: '30s' },
        { target: PEAK_RATE, duration: '20s' },
        { target: PEAK_RATE, duration: '60s' },
        { target: 0, duration: '10s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:POST /api/orders}': ['p(95)<1000'],
    orders_confirmed: ['rate>0.99'],
    order_confirm_ms: ['p(95)<5000'],
  },
};

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

// Runs once before the test: creates products with plenty of stock.
export function setup() {
  const run = Date.now();
  const productIds = [];
  for (let i = 0; i < PRODUCTS; i++) {
    const res = http.post(
      `${INVENTORY_URL}/api/products`,
      JSON.stringify({ sku: `LOAD-${run}-${i}`, name: `Load test product ${i}`, price: 10.0, initialStock: 1000000 }),
      { headers: JSON_HEADERS },
    );
    if (res.status !== 201) {
      fail(`could not create product: ${res.status} ${res.body}`);
    }
    productIds.push(res.json('id'));
  }
  return { productIds };
}

export default function (data) {
  const productId = data.productIds[Math.floor(Math.random() * data.productIds.length)];
  const body = JSON.stringify({
    customerId: uuid(),
    items: [{ productId: productId, quantity: 1, unitPrice: 10.0 }],
  });

  const start = Date.now();
  const res = http.post(`${ORDER_URL}/api/orders`, body, {
    headers: JSON_HEADERS,
    tags: { name: 'POST /api/orders' },
  });
  const created = check(res, { 'order created (201)': (r) => r.status === 201 });
  if (!created) {
    confirmed.add(false);
    return;
  }

  const id = res.json('id');
  let status = 'PENDING';
  while (Date.now() - start < 30000) {
    sleep(POLL_SECONDS);
    const poll = http.get(`${ORDER_URL}/api/orders/${id}`, { tags: { name: 'GET /api/orders/{id}' } });
    status = poll.json('status');
    if (status !== 'PENDING') {
      break;
    }
  }

  confirmed.add(status === 'CONFIRMED');
  if (status === 'CONFIRMED') {
    confirmTime.add(Date.now() - start);
  }
}
