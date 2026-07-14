import http from 'k6/http';
import { check } from 'k6';

// Warm-up / JIT + cache priming before the measured runs.
// 20 iterations of matrix+forecast (single VU), plus 3 iterations of each upload endpoint.
// All scenarios run once and exit; nothing is timed here.

const BASE = __ENV.BASE || 'http://localhost:8082';
const MATRIX_URL = `${BASE}/api/v1/cash-flow/matrix?from=2025-01-01&to=2026-06-30`;
const FORECAST_URL = `${BASE}/api/v1/cash-flow/budget?asOf=2026-06-30`;
const SALES_URL = `${BASE}/api/v1/sales-analysis/analyze`;
const RECON_URL = `${BASE}/api/v1/reconciliation/analyze`;

const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const salesBin = open('C:/Users/Boris/Dell/Projects/APPS/Camora/camora_erp/export_products_260312.xlsx', 'b');
const rsgeBin = open('C:/Users/Boris/Dell/Projects/APPS/Camora/camora_erp/report (67).csv', 'b');
const posterBin = open('C:/Users/Boris/Dell/Projects/APPS/Camora/camora_erp/export_supplies_260224 (2).xlsx', 'b');

export const options = {
  scenarios: {
    reads: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 20, // -> 20 matrix + 20 forecast hits
      exec: 'reads',
    },
    salesWarm: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 3,
      exec: 'salesUpload',
    },
    reconWarm: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 3,
      exec: 'reconUpload',
    },
  },
  // No thresholds on warm-up; failures here just signal the server is not ready.
};

export function reads() {
  const m = http.get(MATRIX_URL);
  const f = http.get(FORECAST_URL);
  check(m, { 'matrix 200': (r) => r.status === 200 });
  check(f, { 'forecast 200': (r) => r.status === 200 });
}

export function salesUpload() {
  const res = http.post(SALES_URL, {
    salesFile: http.file(salesBin, 'export_products_260312.xlsx', XLSX_MIME),
    dateFrom: '2026-01-01',
    dateTo: '2026-12-31',
  });
  check(res, { 'sales 200': (r) => r.status === 200 });
}

export function reconUpload() {
  const res = http.post(RECON_URL, {
    rsgeFile: http.file(rsgeBin, 'report (67).csv', 'text/csv'),
    posterFile: http.file(posterBin, 'export_supplies_260224 (2).xlsx', XLSX_MIME),
    dateFrom: '2025-01-01',
    dateTo: '2026-12-31',
  });
  check(res, { 'recon 200': (r) => r.status === 200 });
}
