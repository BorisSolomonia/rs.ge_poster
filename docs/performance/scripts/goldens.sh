#!/usr/bin/env bash
# Golden-snapshot capture for Camora ERP endpoints.
# Runs WSL-side (curl + jq). Writes one canonical JSON file per endpoint into $OUT.
#
# Usage:  BASE=http://localhost:8082 OUT=/path/to/goldens  ./goldens.sh
#     or  ./goldens.sh <OUT_DIR> [BASE_URL]
#
# Each response is normalized with `jq -S` (sorted keys) and the volatile
# `.timestamp` field (ApiResponse sets Instant.now() on every call) is stripped
# so snapshots are byte-stable across runs. Reconciliation additionally carries a
# random `.data.runId` UUID and per-result timestamps -> also stripped.
#
# curl -sf makes any non-2xx status fail loudly; set -euo pipefail aborts the run.

set -euo pipefail

OUT="${1:-${OUT:-./goldens}}"
BASE="${2:-${BASE:-http://localhost:8082}}"
API="${BASE}/api/v1"

# Real input files (WSL paths — this script runs in WSL, not Windows).
REPO="/mnt/c/Users/Boris/Dell/Projects/APPS/Camora/camora_erp"
SALES_XLSX="${REPO}/export_products_260312.xlsx"
RSGE_CSV="${REPO}/report (67).csv"
POSTER_XLSX="${REPO}/export_supplies_260224 (2).xlsx"
XLSX_MIME="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

mkdir -p "$OUT"
echo "BASE=$BASE  OUT=$OUT"

# get <outfile> <path-with-query>
get() {
  local out="$1"; shift
  local path="$1"; shift
  echo "GET  $path -> $out"
  curl -sf "${API}${path}" | jq -S 'del(.timestamp)' > "${OUT}/${out}"
}

# Reconciliation/sales results carry non-deterministic ids/timestamps; strip broadly.
strip_volatile() {
  jq -S '
    del(.timestamp)
    | if .data then .data
        |= (if type=="object" then (del(.runId) | del(.generatedAt) | del(.createdAt)) else . end)
      else . end
  '
}

echo "== read endpoints =="
get status.json          "/cash-flow/status"
get categories.json      "/cash-flow/categories"
get matrix.json          "/cash-flow/matrix"
get matrix-range.json    "/cash-flow/matrix?from=2025-01-01&to=2026-06-30"
get forecast.json        "/cash-flow/budget?asOf=2026-06-30"

echo "== sales-analysis upload =="
curl -sf "${API}/sales-analysis/analyze" \
  -F "salesFile=@${SALES_XLSX};type=${XLSX_MIME}" \
  -F "dateFrom=2026-01-01" \
  -F "dateTo=2026-12-31" \
  | strip_volatile > "${OUT}/sales-analyze.json"
echo "POST /sales-analysis/analyze -> sales-analyze.json"

echo "== reconciliation upload =="
curl -sf "${API}/reconciliation/analyze" \
  -F "rsgeFile=@${RSGE_CSV};type=text/csv" \
  -F "posterFile=@${POSTER_XLSX};type=${XLSX_MIME}" \
  -F "dateFrom=2025-01-01" \
  -F "dateTo=2026-12-31" \
  | strip_volatile > "${OUT}/reconciliation-analyze.json"
echo "POST /reconciliation/analyze -> reconciliation-analyze.json"

echo "Done. $(ls -1 "$OUT" | wc -l) golden files in $OUT"
