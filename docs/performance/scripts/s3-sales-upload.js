import http from 'k6/http';
import { check } from 'k6';

// POST sales-analysis multipart upload under load.
// 2 VUs, 2m. Fields: salesFile (xlsx) + dateFrom + dateTo (ISO yyyy-MM-dd, both REQUIRED).
// File path is a Windows path resolved by k6.exe. open(..., 'b') reads raw bytes in init context.

const BASE = __ENV.BASE || 'http://localhost:8082';
const URL = `${BASE}/api/v1/sales-analysis/analyze`;

// export_products_260312.xlsx: sheet 0, date col 1, product col 2, amount(Revenue) col 7. Rows dated 2026-03-12.
const SALES_XLSX = 'C:/Users/Boris/Dell/Projects/APPS/Camora/camora_erp/export_products_260312.xlsx';
const salesBin = open(SALES_XLSX, 'b');

export const options = {
  vus: 2,
  duration: '2m',
  thresholds: {
    http_req_duration: ['p(95)<10000', 'p(99)<20000'],
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const body = {
    salesFile: http.file(
      salesBin,
      'export_products_260312.xlsx',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    ),
    dateFrom: '2026-01-01',
    dateTo: '2026-12-31',
  };
  const res = http.post(URL, body); // k6 sets multipart/form-data with a boundary automatically
  check(res, {
    'status is 200': (r) => r.status === 200,
    'body success:true': (r) => r.json('success') === true,
  });
}
