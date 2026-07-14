import http from 'k6/http';
import { check } from 'k6';

// POST reconciliation /analyze multipart upload under load.
// 2 VUs, 1m. Fields: rsgeFile (csv) + posterFile (xlsx) + dateFrom + dateTo (ISO, both REQUIRED).
// NOTE: this is the sales/waybill reconciliation (uploads BOTH files). It is NOT /purchase-analyze,
// which fetches rs.ge waybills over SOAP and needs live RSGE credentials.

const BASE = __ENV.BASE || 'http://localhost:8082';
const URL = `${BASE}/api/v1/reconciliation/analyze`;

// report (67).csv: rs.ge waybill export, 26 cols (>=18 required), UTF-8 BOM, Georgian headers.
const RSGE_CSV = 'C:/Users/Boris/Dell/Projects/APPS/Camora/camora_erp/report (67).csv';
// export_supplies_260224 (2).xlsx: Poster "supplies" sheet, doc# col 1, date col 2, supplier col 3, products col 6, sum col 9.
const POSTER_XLSX = 'C:/Users/Boris/Dell/Projects/APPS/Camora/camora_erp/export_supplies_260224 (2).xlsx';

const rsgeBin = open(RSGE_CSV, 'b');
const posterBin = open(POSTER_XLSX, 'b');

export const options = {
  vus: 2,
  duration: '1m',
  thresholds: {
    http_req_duration: ['p(95)<10000', 'p(99)<20000'],
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const body = {
    rsgeFile: http.file(rsgeBin, 'report (67).csv', 'text/csv'),
    posterFile: http.file(
      posterBin,
      'export_supplies_260224 (2).xlsx',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    ),
    dateFrom: '2025-01-01',
    dateTo: '2026-12-31',
  };
  const res = http.post(URL, body);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'body success:true': (r) => r.json('success') === true,
  });
}
