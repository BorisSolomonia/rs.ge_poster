# Secret and Hardcode Audit Report

Date: 2026-05-08

Values are intentionally redacted. This report lists only file paths, variable names, and risk categories.

## Critical Findings

- `backend/.env` is tracked by Git and contains non-empty credentials:
  - `CAMORA_RSGE_USERNAME`
  - `CAMORA_RSGE_PASSWORD`
  - `CAMORA_TBC_DBI_USERNAME`
  - `CAMORA_TBC_DBI_PASSWORD`
  - `CAMORA_TBC_DBI_CERTIFICATE_PATH`
  - `CAMORA_TBC_DBI_CERTIFICATE_PASSWORD`
  - `CAMORA_TBC_DBI_ACCOUNT_NUMBER`
- `frontend/.env` is tracked by Git. Frontend env files are bundled into browser-visible code when variables are referenced by Vite, so only public values may be placed there.
- `docker/.env.example` contains real-looking example values for RS.ge and TBC fields. Example files must use placeholders only.
- `postman/bog/BOG_integration`, `postman/tbc-dbi-local.postman_environment.json`, and `postman/tbc-dbi-account-movement.postman_collection.json` are tracked request artifacts and can expose endpoints, account numbers, usernames, or credentials if values are filled.
- Business data exports are tracked:
  - `camora_daily.xlsx`
  - `export_products_260312.xlsx`
  - `export_supplies_260224 (2).xlsx`
  - `report (67).csv`
  - `supplier_mapping.csv`
- `frontend/node_modules` is tracked in Git. This is not a direct secret, but it creates a very large attack surface and makes secret scanning noisy.

## Local-Only Sensitive Files

These files exist locally and are currently ignored, but they must never be uploaded or copied to GitHub:

- Local Google service-account JSON with private-key fields.
- Local TBC client certificate file.
- `docker/.env.secret.local`: local secret environment file with service account JSON and bank/API settings.

## Gitleaks Guardrails Added

- `.gitleaks.toml` adds custom Camora rules for:
  - `CAMORA_*` credential variables
  - bank account values in env files
  - inline Google service-account JSON
  - Postman secret values
- `.pre-commit-config.yaml` adds a staged Gitleaks hook.
- `.github/workflows/ci.yml` now checks out full history and runs Gitleaks with `.gitleaks.toml`.

## Required Cleanup Before GitHub Push

Run these only after confirming the files should be removed from Git tracking while staying on disk locally:

```powershell
git rm --cached backend/.env frontend/.env
git rm --cached camora_daily.xlsx export_products_260312.xlsx "export_supplies_260224 (2).xlsx" "report (67).csv" supplier_mapping.csv
git rm --cached -r frontend/node_modules
git rm --cached postman/bog/BOG_integration postman/tbc-dbi-local.postman_environment.json postman/tbc-dbi-account-movement.postman_collection.json
```

If any of these files were ever pushed to a remote repository, rotate the exposed credentials immediately. Removing files from the latest commit is not enough because secrets remain in Git history.

## History Cleanup If Already Pushed

1. Rotate RS.ge password, TBC DBI password, TBC certificate password, any exposed BOG credentials, and Google service-account key.
2. Create new credentials in the bank/provider/admin panels.
3. Store new values only in GCP Secret Manager.
4. Use `git filter-repo` or BFG to remove historical secrets from all commits.
5. Force-push only after coordinating with every collaborator.
6. Re-run Gitleaks against full history.

## Recommended Local Workflow

```powershell
pip install pre-commit
pre-commit install
pre-commit run gitleaks --all-files
```

If `gitleaks` is not available locally, install it from the official release page or run the hook in CI before merging.
