import http from 'k6/http';
import { check } from 'k6';

// GET rolling budget forecast under load.
// 3 VUs, 1m. `asOf` query param is optional and omitted here (server defaults to today).

const BASE = __ENV.BASE || 'http://localhost:8082';
const URL = `${BASE}/api/v1/cash-flow/budget?asOf=2026-06-30`;

export const options = {
  vus: 3,
  duration: '1m',
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const res = http.get(URL);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'body success:true': (r) => r.json('success') === true,
  });
}
