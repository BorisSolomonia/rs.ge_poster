# Supplier Debt Data Integrity Audit

Date: 2026-06-09

## Scope

Investigated the supplier-debts data flow for RS.ge purchases and BOG/TBC supplier payments for the statement range supplied by the local Excel files:

- `St_ST_20250101_20260609.xlsx`
- `account_statement_16838660_01012025_09062026.xlsx`

## Excel Ledger Control Totals

These are the statement totals extracted directly from the provided files and should be used as the control totals when comparing live raw payloads in the supplier-debts debug panel.

| Source | Excel sheet | Date range present | Debit/outgoing count | Debit/outgoing total | Credit/incoming count | Credit/incoming total |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| BOG | `Statement of Account` | 2025-05-02 to 2026-06-09 | 421 | 1,094,736.46 GEL | 52,607 | 1,115,090.84 GEL |
| TBC | `GE11TB7779036020100008-GEL` | 2025-04-28 to 2026-06-09 | 379 | 666,521.53 GEL | 30,817 | 671,606.98 GEL |

Supplier debt currently uses outgoing bank payments only. Incoming card/payment rows are expected to appear in the raw bank payloads but are not subtracted from supplier debt.

## Confirmed Root Causes

1. Partial source failures could overwrite a valid saved snapshot.

   `SupplierDebtService.fetchSource(...)` intentionally converted BOG/TBC/RS.ge exceptions into an empty record list plus a `FAILED` source status. Before this fix, `refreshSnapshot(...)` and background refresh still saved that calculated overview. Result: a temporary bank or RS.ge failure could persist a supplier-debt snapshot with missing source totals and make the UI look like payments disappeared.

   Fix: snapshot refresh is now fail-closed. If RS.ge, BOG, or TBC has status `FAILED`, the refresh throws and the previous saved snapshot is not overwritten.

2. BOG supplier-debt sync was configured to call the statement API one day at a time.

   `CAMORA_SUPPLIER_DEBT_BOG_STATEMENT_WINDOW_DAYS` defaulted to `1`, producing hundreds of serial BOG calls for this range. The BOG client already has range chunking and recursive split logic based on `Count`/`TotalCount`, so daily windows were an unnecessary performance bottleneck.

   Fix: the default is now `0`, which means supplier debt uses the BOG API statement chunk setting (`CAMORA_BOG_API_STATEMENT_CHUNK_DAYS`, currently 31) instead of forcing daily calls.

3. Bank payments without supplier TIN or saved mapping are not lost, but they do not reduce supplier debt.

   `addBankPayments(...)` only subtracts outgoing `DEBIT` transactions that can be matched to an RS.ge supplier by TIN or saved payment mapping. Unmatched bank debits are shown in unmatched payment groups and are excluded from `paidTotal` until mapped. This can create an apparent debt discrepancy if testers compare the full Excel outgoing total to matched supplier payments.

4. Current code does not show evidence of primary pagination truncation.

   BOG checks response `Count` against `TotalCount` and recursively splits the date range. TBC fetches pages until a page smaller than the configured page size is returned. The stronger confirmed issue was not pagination truncation; it was snapshot persistence after source failure plus excessive BOG daily calls.

5. Timezone/date handling is not the leading cause found in code.

   Bank statement fetching is date-based. RS.ge buyer waybills are requested from start-of-day through the next start-of-day after `dateTo`, making the end date inclusive. No direct timezone conversion bug was found in the supplier-debt aggregation path.

## Debugging Workflow Added

The supplier-debts page now includes a `Raw Source Payloads` panel. It loads raw payloads for:

- RS.ge buyer waybills
- BOG bank transactions
- TBC bank transactions

Use this panel to compare live source payloads against the Excel control totals above:

- BOG raw payloads should contain 421 outgoing `DEBIT` rows totaling 1,094,736.46 GEL for the statement-covered range.
- TBC raw payloads should contain 379 outgoing `DEBIT` rows totaling 666,521.53 GEL for the statement-covered range.
- Any outgoing raw bank row that appears in the debug panel but is not counted in supplier debt should appear in unmatched payment groups unless it is matched by supplier TIN or saved mapping.

## Remaining Verification Needed

The local workspace does not include captured live BOG/TBC/RS.ge API payload files, and the API credentials are environment-driven. Therefore, individual "missed payment" row IDs can only be named after running the debug panel against the live APIs and comparing the returned raw payload table with the Excel control totals above.

The implemented debug view is the required instrumentation for that comparison.
