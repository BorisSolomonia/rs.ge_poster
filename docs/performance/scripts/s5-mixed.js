import http from 'k6/http';
import { check } from 'k6';

// Mixed read load: matrix (5 VUs) + forecast (2 VUs) running concurrently for 1m.
// Two constant-VU scenarios, each pinned to its own exec function.

const BASE = __ENV.BASE || 'http://localhost:8082';
const MATRIX_URL = `${BASE}/api/v1/cash-flow/matrix?from=2025-01-01&to=2026-06-30`;
const FORECAST_URL = `${BASE}/api/v1/cash-flow/budget?asOf=2026-06-30`;

export const options = {
  scenarios: {
    matrix: {
      executor: 'constant-vus',
      vus: 5,
      duration: '1m',
      exec: 'matrix',
    },
    forecast: {
      executor: 'constant-vus',
      vus: 2,
      duration: '1m',
      exec: 'forecast',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<3000', 'p(99)<6000'],
    http_req_failed: ['rate<0.05'],
  },
};

export function matrix() {
  const res = http.get(MATRIX_URL);
  check(res, { 'matrix 200': (r) => r.status === 200 });
}

export function forecast() {
  const res = http.get(FORECAST_URL);
  check(res, { 'forecast 200': (r) => r.status === 200 });
}
