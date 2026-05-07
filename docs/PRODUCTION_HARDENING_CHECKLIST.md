# Camora ERP Production Hardening Checklist

This checklist tracks the production work that cannot be completed safely as a small patch.

## Blockers Before Public Production

- Add authentication and role-based authorization for all finance, bank, RS.ge, mapping, and admin endpoints.
- Move all secrets to GCP Secret Manager and keep certificates, service-account JSON, `.env` files, and Postman secrets out of the repository and deployment artifacts.
- Replace JSON-file configuration storage with PostgreSQL tables for mappings, sync runs, audit events, and manual overrides.
- Add audit records for bank syncs, RS.ge syncs, supplier mappings, bank mappings, password-change attempts, and manual corrections.
- Convert long-running external API calls into background sync runs with status polling, retry policy, and failure details.

## Production Reliability

- Add readiness checks for database connectivity, writable config/storage, required secrets, and integration configuration.
- Add structured JSON logs with request IDs, provider names, sync run IDs, and stable error codes.
- Add alerts for failed syncs, blocked TBC users, expired certificates, stale cashflow data, and unmapped large payments.
- Add server-side pagination for bank transactions and supplier debt drilldowns.
- Add daily backups for the production database and a tested restore procedure.

## Business Analytics Roadmap

- Executive dashboard: cash position, supplier debt, unpaid debt, paid amount, gross sales, bank inflow, margin, and reconciliation confidence.
- Drilldown path: KPI to supplier to RS.ge waybill to bank payment to raw source row.
- Supplier debt aging: current, 1-7 days, 8-30 days, 31-60 days, and 60+ days.
- Variance analysis: actual vs previous period, actual vs budget, and actual vs forecast.
- Cashflow forecast: expected supplier payments, bank inflow trend, and projected shortfall.
- Anomaly detection: duplicate transactions, unmapped large payments, supplier debt spikes, and sales-bank mismatches.
- Scheduled management reports by email or exported PDF/Excel.

## CI/CD Gates

- Backend verification must pass.
- Frontend lint and production build must pass.
- Secret scanning must pass.
- Dependency and filesystem vulnerability scans must pass.
- Deployment must run smoke checks against backend and frontend health endpoints.
