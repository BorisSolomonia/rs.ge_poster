# Audit Control Architecture Review

## Context Summary

Camora ERP is currently a modular monolith with a Spring Boot 3 backend and React 18/Vite frontend. The backend exposes REST endpoints under `camora.api-prefix`, integrates RS.ge through SOAP clients, integrates bank statements through BOG and TBC clients, and persists most operational configuration and snapshots as JSON files through `ConfigStore` and dedicated stores. The frontend is route based, with API clients in `frontend/src/api`, shared types in `frontend/src/types`, and pages in `frontend/src/pages`.

The requested `/audit-control` module depends on inventory, customer, supplier, product hierarchy, payments, RS.ge waybills, sales, write-offs, and audit exception data. Some of those concepts exist only partially today. Supplier debt currently has JSON-backed supplier/payment mappings, RS.ge purchase snapshots, BOG/TBC payment matching, manual cash payments, and audit helpers. There is no active PostgreSQL/JPA persistence layer in the repository and no migration system. PostgreSQL is referenced as a production hardening direction, not as the current storage implementation.

## Current Data Flow

- RS.ge purchase waybills are fetched by `RsgePurchaseWaybillService` and converted into `RsgeRecord` rows.
- Bank data is fetched by `BogBusinessOnlineClient` and `TbcDbiClient`, converted into `BankTransaction` rows, then matched to supplier purchases in `SupplierDebtService`.
- Supplier debt overview data is saved as a JSON snapshot by `SupplierDebtSnapshotStore`.
- Manual supplier cash payments and supplier payment mappings are stored by `ConfigStore`.
- The supplier-debts page reads overview data through `frontend/src/api/supplier-debts.api.ts` and renders controls in `SupplierDebtsPage`.

## Root Cause: Stale Supplier-Debt Data

The stale data path was not a 15-minute cron. The supplier-debt page had a manual refresh endpoint, `/supplier-debts/refresh`, but that endpoint intentionally started a background refresh and immediately returned a saved snapshot or empty state. That behavior is useful for nonblocking date changes, but it is wrong for a management button that promises fresh RS.ge and bank data.

The 15-minute setting was the source cache TTL: `camora.supplier-debt.source-cache-ttl-minutes`. It allowed source fetches to be reused inside the supplier debt service. For freshness control, manual sync must force cache invalidation and block until RS.ge, BOG, and TBC fetches finish.

## Implemented Freshness Change

- Added `/supplier-debts/sync-now`, a synchronous endpoint that calls `SupplierDebtService.syncNow(...)`.
- `syncNow` forces a source refresh and replaces the supplier-debt snapshot before returning data to the client.
- Added an hourly supplier-debt scheduler using `camora.supplier-debt.sync-fixed-delay`, defaulting to `3600000` ms.
- Changed supplier-debt source cache TTL default from 15 minutes to 60 minutes.
- Kept the old asynchronous `/supplier-debts/refresh` endpoint for nonblocking workflows.
- Rewired the supplier-debts page button to use `/sync-now` and show a loading state while the fresh fetch runs.

## Data Modeling Plan

The requested audit module should use a durable relational model before adding legal inventory controls. Recommended tables once PostgreSQL is introduced:

- `products`: `id`, `parent_product_id`, `name`, `unit`, `is_active`, timestamps.
- `business_partners`: `id`, `kind` (`CUSTOMER` or `SUPPLIER`), `tin`, `name`, `is_real_entity`, timestamps.
- `official_documents`: `id`, `source`, `external_id`, `partner_id`, `document_type`, `document_date`, totals, raw payload reference.
- `inventory_movements`: `id`, `product_id`, `movement_date`, `movement_type`, `quantity_kg`, `document_id`, notes.
- `processing_batches`: `id`, `processing_date`, `input_product_id`, `input_kg`, `output_kg`, `actual_loss_kg`, `allowed_loss_kg`, `overage_kg`.
- `audit_exceptions`: `id`, `exception_type`, `severity`, `document_id`, `partner_id`, `status`, `details_json`, timestamps.
- `payment_reconciliations`: `id`, `payment_source`, `payment_external_id`, `document_id`, `manual_paid_override`, `override_user`, `override_reason`, timestamps.

Do not keep origin tracking fields such as Georgian vs Imported in the new model. Product aggregation should use `parent_product_id`, not origin categories.

## Algorithm Plan

Inventory on hand for a selected range should be calculated as:

`opening_stock + purchases - sales - write_offs`

Daily write-off calculation should run day by day after official purchases, sales, and processing movements are normalized. The requested 29 to 30 percent legal write-off target must be configurable until an accountant-approved source is supplied. The service should calculate:

- `target_writeoff_kg = input_kg * target_percent`
- `max_allowed_writeoff_kg = input_kg * max_percent`
- `actual_loss_kg = input_kg - output_kg`
- flag an overage when `actual_loss_kg > max_allowed_writeoff_kg`
- use the lower of actual loss and max allowed loss for compliant ledger reporting

## Hardcode And Secrets Plan

- Keep RS.ge, BOG, and TBC credentials in environment variables or secret manager only.
- Keep write-off target and limit as configuration until legal source material is confirmed.
- Keep target expense ID `01008026584` in configuration so it is auditable and changeable without a rebuild.
- Avoid UI hardcoding of Georgian deployment path; respect the existing `/camora` base-path compatibility.

## Deployment Plan

The immediate sync changes are compatible with the current deployment model. Operators can tune:

- `CAMORA_SUPPLIER_DEBT_SOURCE_CACHE_TTL_MINUTES=60`
- `CAMORA_SUPPLIER_DEBT_SCHEDULED_SYNC_ENABLED=true`
- `CAMORA_SUPPLIER_DEBT_SYNC_FIXED_DELAY=3600000`
- `CAMORA_SUPPLIER_DEBT_SYNC_INITIAL_DELAY=60000`

Full `/audit-control` implementation should be staged behind a persistence migration because inventory ledgers, audit exceptions, payment overrides, and historical calculations require durable relational integrity.

## Open Architecture Decisions

- Confirm whether to introduce PostgreSQL now or extend JSON stores temporarily.
- Confirm the legally approved write-off regulation and whether 29 to 30 percent applies to all meat categories or only specific product families.
- Define the authoritative sales source for delivered goods and customer debt.
- Define authorization rules for manual payment override actions.