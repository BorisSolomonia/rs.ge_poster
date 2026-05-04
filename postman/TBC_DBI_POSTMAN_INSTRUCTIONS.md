# TBC DBI Postman Test

This folder contains an importable Postman collection and environment for testing TBC DBI `GetAccountMovements`.

Files:

- `tbc-dbi-account-movement.postman_collection.json`
- `tbc-dbi-local.postman_environment.json`

## 1. Import Files

Open Postman and import both JSON files:

1. Click `Import`.
2. Select `postman/tbc-dbi-account-movement.postman_collection.json`.
3. Select `postman/tbc-dbi-local.postman_environment.json`.
4. Choose environment `TBC DBI - Local Template`.

## 2. Configure Certificate

The certificate is not stored in the collection. Add it in Postman:

1. Open `Postman Settings`.
2. Open `Certificates`.
3. Click `Add Certificate`.
4. For production set:

```text
Host: secdbi.tbconline.ge
Port: 443
PFX file: C:\Users\Boris\Dell\Projects\APPS\Camora\camora_erp\Shps verapani.pfx
Passphrase: your certificate password
```

For TBC test environment use this host instead:

```text
Host: secdbitst.tbconline.ge
Port: 443
```

## 3. Fill Environment Variables

In Postman, open environment `TBC DBI - Local Template` and set:

```text
tbc_dbi_username       DBI username from TBC
tbc_dbi_password       DBI password from TBC
tbc_dbi_soap_action    "http://www.mygemini.com/schemas/mygemini/GetAccountMovements"
tbc_dbi_account_number TBC account IBAN, for example GE...TB...
tbc_dbi_currency       GEL
tbc_dbi_period_from    2026-02-01T00:00:00.000
tbc_dbi_period_to      2026-02-28T23:59:59.000
tbc_dbi_page_index     0
tbc_dbi_page_size      700
```

Production endpoint:

```text
tbc_dbi_base_url=https://secdbi.tbconline.ge/dbi/dbiService
```

Test endpoint:

```text
tbc_dbi_base_url=https://secdbitst.tbconline.ge/dbi/dbiService
```

## 4. Send TBC Request

Run:

```text
TBC DBI - GetAccountMovements by date range
```

Success means:

- HTTP status is `200`.
- Response contains SOAP `Envelope`.
- Response contains `GetAccountMovementsResponseIo`.
- Response does not contain `Fault`.

If the response says unauthorized, forbidden, or SOAP fault, check:

- Header `SOAPAction` exists and its value is `"http://www.mygemini.com/schemas/mygemini/GetAccountMovements"`.
- Certificate host matches the request host.
- PFX password is correct.
- DBI username/password are from TBC Integration Service, not developer app key/secret.
- Account IBAN belongs to the company activated for DBI.
- You are using the correct test or production endpoint.

## 5. Test Camora Backend

After TBC works directly in Postman, configure the app env:

```env
CAMORA_TBC_DBI_ENABLED=true
CAMORA_TBC_DBI_USERNAME=
CAMORA_TBC_DBI_PASSWORD=
CAMORA_TBC_DBI_CERTIFICATE_PASSWORD=
CAMORA_TBC_DBI_ACCOUNT_NUMBER=
CAMORA_TBC_DBI_CURRENCY=GEL
CAMORA_TBC_DBI_CERTIFICATE_BASE64=
```

Then run:

```text
Camora - Bank Analysis via backend
```

For local backend:

```text
camora_base_url=http://localhost:8082
camora_api_prefix=/api/v1
```

For deployed backend behind Caddy, use:

```text
camora_base_url=http://34.10.54.191/camora
camora_api_prefix=/api/v1
```

## 6. Paging

TBC returns maximum `700` movements per page. If page `0` returns 700 records, increase:

```text
tbc_dbi_page_index=1
```

Then send the request again. Continue until the returned count is less than `700`.
