import http from 'k6/http';
import { check } from 'k6';

// GET cash-flow matrix under sustained load.
// 5 VUs, 2m total wall clock (30s ramp-up to 5, then hold at 5).
// Thresholds are declared for reporting only; the run is not aborted on breach.
// Caller captures results via `k6 run --summary-export=<file> s1-matrix.js`.

const BASE = __ENV.BASE || 'http://localhost:8082';
const URL = `${BASE}/api/v1/cash-flow/matrix?from=2025-01-01&to=2026-06-30`;

export const options = {
  stages: [
    { duration: '30s', target: 5 },   // ramp
    { duration: '1m30s', target: 5 }, // hold
  ],
  thresholds: {
    // Recorded, not enforced (abortOnFail defaults to false; caller ignores exit code).
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
